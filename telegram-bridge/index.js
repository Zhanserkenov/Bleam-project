require('dotenv').config();

const express = require('express');
const axios = require('axios');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { createClient } = require('redis');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3001;
const SERVICE_NAME = process.env.SERVICE_NAME;
const SERVICE_SECRET = process.env.JWT_SERVICE_SECRET;
const PUBLIC_DOMAIN = process.env.PUBLIC_DOMAIN;
const PLATFORM_SERVICE_URL = process.env.PLATFORM_SERVICE_URL;
const TG_WEBHOOK_SECRET_SALT = process.env.TG_WEBHOOK_SECRET_SALT;

const REDIS_URL = process.env.REDIS_URL;

const redis = createClient({ url: REDIS_URL });

const platformTokens = new Map();

function verifyServiceToken(req, res, next) {
    const auth = req.headers['authorization'];
    if (!auth || !auth.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'No token' });
    }
    const token = auth.split(' ')[1];
    jwt.verify(token, SERVICE_SECRET, { algorithms: ['HS256'] }, (err, decoded) => {
        if (err) return res.status(403).json({ error: 'Invalid or expired token' });

        if (!decoded || decoded.token_type !== 'service') {
            return res.status(403).json({ error: 'Not a service token' });
        }
        req.callerService = decoded.service || decoded.sub;
        next();
    });
}

function createServiceToken() {
    const payload = {
        sub: SERVICE_NAME,
        token_type: 'service',
        service: SERVICE_NAME
    };
    return jwt.sign(payload, SERVICE_SECRET, { algorithm: 'HS256', expiresIn: '60s' });
}

function getWebhookSecret(userId) {
    const h = crypto.createHmac('sha256', TG_WEBHOOK_SECRET_SALT || '', String(userId)).update(String(userId)).digest('hex');
    return h.slice(0, 32);
}

/**
 * Webhook endpoint (Telegram -> this service)
 * Accepts Telegram update and publishes it to Redis stream telegram.incoming
 */
app.post('/webhook/:userId/:secret', async (req, res) => {
    try {
        const { userId, secret } = req.params;
        const expected = getWebhookSecret(userId);
        if (secret !== expected) {
            console.warn(`Webhook secret mismatch for user ${userId} from ${req.ip}`);
            return res.sendStatus(403);
        }

        const update = req.body;
        const chatUserId = update.message?.chat?.id;
        const text = update.message?.text || "";

        // publish to redis stream telegram.incoming
        try {
            await redis.sendCommand(['XADD', 'telegram.incoming', '*',
                'userId', String(userId),
                'chatUserId', String(chatUserId || ''),
                'message', String(text)
            ]);
        } catch (e) {
            console.error('Failed to XADD telegram.incoming', e);
            // continue — we still return 200 to Telegram to avoid retries in case we want that
        }

        res.sendStatus(200);
    } catch (err) {
        console.error('Error in /webhook:', err);
        res.sendStatus(500);
    }
});

/**
 * Outgoing consumer loop: reads redis stream telegram.outgoing and calls Telegram API
 * Expected stream message fields: userId, chatUserId, method, payload
 */
async function startOutgoingConsumerLoop() {
    const STREAM = 'telegram.outgoing';
    const GROUP = 'telegram-bridge-group';
    const consumerName = `consumer-${process.pid}-${Math.floor(Math.random() * 1000)}`;

    // create consumer group if not exists
    try {
        await redis.sendCommand(['XGROUP', 'CREATE', STREAM, GROUP, '$', 'MKSTREAM']);
        console.log('Created consumer group', GROUP, 'for stream', STREAM);
    } catch (err) {
        if (String(err).includes('BUSYGROUP')) {
            console.log('Consumer group already exists:', GROUP);
        } else {
            console.error('Failed to create group', err);
            // continue anyway — might still work if group exists
        }
    }

    while (true) {
        try {
            // block 2s, read up to 10 messages
            const reply = await redis.sendCommand([
                'XREADGROUP', 'GROUP', GROUP, consumerName,
                'BLOCK', '2000',
                'COUNT', '10',
                'STREAMS', STREAM, '>'
            ]);

            if (!reply) {
                continue;
            }

            // reply format: [ [ streamName, [ [id, [field, val, ...] ], ... ] ] ]
            for (const streamData of reply) {
                const streamName = streamData[0]; // should be 'telegram.outgoing'
                const entries = streamData[1];
                for (const entry of entries) {
                    const id = entry[0];
                    const arr = entry[1];
                    const obj = {};
                    for (let i = 0; i < arr.length; i += 2) {
                        obj[String(arr[i])] = String(arr[i + 1]);
                    }

                    try {
                        const userId = obj.userId;
                        const chatUserId = obj.chatUserId;
                        const method = obj.method; // e.g. "sendMessage"
                        // payload might be a JSON string or we can accept flat fields
                        let payload = {};
                        if (obj.payload) {
                            try { payload = JSON.parse(obj.payload); } catch (e) { payload = {}; }
                        } else {
                            // collect other fields except userId/chatUserId/method into payload
                            payload = {};
                            for (const [k, v] of Object.entries(obj)) {
                                if (!['userId','chatUserId','method','payload'].includes(k)) {
                                    payload[k] = v;
                                }
                            }
                        }

                        const token = platformTokens.get(String(userId));
                        if (!token) {
                            console.error(`No token for userId=${userId} (outgoing id=${id})`);
                        } else {
                            const apiUrl = `https://api.telegram.org/bot${token}/${method}`;
                            // Telegram expects { chat_id, ...payload }
                            await axios.post(apiUrl, { chat_id: chatUserId, ...payload });
                        }

                        // acknowledge
                        await redis.sendCommand(['XACK', STREAM, GROUP, id]);
                    } catch (procErr) {
                        console.error('Error processing outgoing entry', procErr, 'entry id=', id);
                        // do NOT XACK — leave in PENDING for later reprocessing
                    }
                }
            }
        } catch (err) {
            console.error('Error in outgoing consumer loop', err);
            await new Promise(r => setTimeout(r, 2000));
        }
    }
}

/**
 * Validate Telegram bot token by calling getMe
 */
async function validateApiToken(apiToken) {
    try {
        const url = `https://api.telegram.org/bot${apiToken}/getMe`;
        const res = await axios.get(url);
        if (res.data && res.data.ok) {
            console.log(`✅ Valid token: connected bot @${res.data.result.username}`);
            return true;
        } else {
            console.error(`❌ Invalid token: Telegram API response ->`, res.data);
            return false;
        }
    } catch (err) {
        console.error(`❌ Error while validating token:`, err.response?.data || err.message);
        return false;
    }
}

/**
 * Register webhook for a given userId + apiToken
 */
async function registerWebhook(userId, apiToken) {
    if (!PUBLIC_DOMAIN) throw new Error('PUBLIC_DOMAIN is not set');

    const isValid = await validateApiToken(apiToken);
    if (!isValid) throw new Error(`Invalid Telegram API token for user ${userId}`);

    const secret = getWebhookSecret(userId);
    const webhookUrl = `${PUBLIC_DOMAIN.replace(/\/$/, '')}/webhook/${encodeURIComponent(userId)}/${encodeURIComponent(secret)}`;

    // set webhook
    const url = `https://api.telegram.org/bot${apiToken}/setWebhook?url=${encodeURIComponent(webhookUrl)}`;
    await axios.get(url);

    platformTokens.set(String(userId), apiToken);
    console.log(`Webhook set for user ${userId} -> ${webhookUrl}`);
}

/**
 * Unregister webhook
 */
async function unregisterWebhook(userId) {
    const token = platformTokens.get(String(userId));
    if (!token) throw new Error(`Token not found for user ${userId}`);

    const url = `https://api.telegram.org/bot${token}/deleteWebhook`;
    await axios.get(url);
    platformTokens.delete(String(userId));
    console.log(`Webhook deleted for user ${userId}`);
}

async function fetchActiveUsers() {
    const token = createServiceToken();
    const res = await axios.get(PLATFORM_SERVICE_URL, {
        headers: { Authorization: `Bearer ${token}` },
    });
    return res.data;
}

async function startAllUsers() {
    const users = await fetchActiveUsers();
    for (const { userId, apiToken } of users) {
        try {
            await registerWebhook(userId, apiToken);
        } catch (err) {
            console.error(`Failed to register webhook for user ${userId}:`, err.message || err);
        }
    }
}

app.post('/start-platform', verifyServiceToken, async (req, res) => {
    const { userId, apiToken } = req.body;
    if (!userId || !apiToken) return res.status(400).json({ error: 'userId and apiToken are required' });

    try {
        await registerWebhook(userId, apiToken);
        res.json({ message: `Telegram Platform has been started for ${userId}` });
    } catch (err) {
        console.error('Error in /start-platform:', err.message || err);
        res.status(500).json({ error: 'Failed to start platform' });
    }
});

app.post('/stop-platform', verifyServiceToken, async (req, res) => {
    const { userId } = req.body;
    if (!userId) return res.status(400).json({ error: 'userId is required' });

    try {
        await unregisterWebhook(userId);
        res.json({ message: `Telegram Platform has been stopped for ${userId}` });
    } catch (err) {
        console.error('Error in /stop-platform:', err.message || err);
        res.status(500).json({ error: 'Failed to stop platform' });
    }
});

(async () => {
    try {
        await redis.connect();
        console.log('Connected to Redis', REDIS_URL);

        // start outgoing consumer loop (background)
        startOutgoingConsumerLoop().catch(err => console.error('Outgoing loop crashed', err));

        // register webhooks for active users
        await startAllUsers();

        app.listen(PORT, () => console.log(`Telegram microservice listening on port ${PORT}`));
    } catch (err) {
        console.error('Failed to bootstrap Telegram microservice:', err);
        process.exit(1);
    }
})();
