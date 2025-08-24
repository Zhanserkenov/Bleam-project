require('dotenv').config();

const express = require('express');
const axios = require('axios');
const { Kafka } = require('kafkajs');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());

const PORT = process.env.PORT;

const SERVICE_NAME = process.env.SERVICE_NAME;
const SERVICE_SECRET = process.env.JWT_SERVICE_SECRET;

const PUBLIC_DOMAIN = process.env.PUBLIC_DOMAIN;
const PLATFORM_SERVICE_URL = process.env.PLATFORM_SERVICE_URL;

const TG_WEBHOOK_SECRET_SALT = process.env.TG_WEBHOOK_SECRET_SALT;

const KAFKA_BROKERS = (process.env.KAFKA_BROKERS).split(',');
const KAFKA_CLIENT_ID = process.env.KAFKA_CLIENT_ID;
const KAFKA_CONSUMER_GROUP = process.env.KAFKA_CONSUMER_GROUP;

const kafka = new Kafka({ clientId: KAFKA_CLIENT_ID, brokers: KAFKA_BROKERS });
const producer = kafka.producer();
const consumer = kafka.consumer({ groupId: KAFKA_CONSUMER_GROUP });

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
    const h = crypto.createHmac('sha256', TG_WEBHOOK_SECRET_SALT).update(String(userId)).digest('hex');
    return h.slice(0, 32);
}

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

        await producer.send({
            topic: "telegram.incoming",
            messages: [{
                key: String(chatUserId || 'unknown'),
                value: JSON.stringify({ userId, chatUserId, message: text })
            }]
        });

        res.sendStatus(200);
    } catch (err) {
        console.error('Error in /webhook:', err);
        res.sendStatus(500);
    }
});

async function startConsumer() {
    await consumer.connect();
    await consumer.subscribe({ topic: 'telegram.outgoing', fromBeginning: false });

    await consumer.run({
        eachMessage: async ({ message }) => {
            try {
                const payloadObj = JSON.parse(message.value.toString());
                const { userId, chatUserId, method, payload } = payloadObj;
                const token = platformTokens.get(String(userId));

                if (!token) {
                    console.error(`No token for userId=${userId}`);
                    return;
                }

                const apiUrl = `https://api.telegram.org/bot${token}/${method}`;
                await axios.post(apiUrl, { chat_id: chatUserId, ...payload });
            } catch (err) {
                console.error('Telegram send error:', err.response?.data || err.message);
            }
        }
    });
}

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

async function registerWebhook(userId, apiToken) {
    if (!PUBLIC_DOMAIN) throw new Error('PUBLIC_DOMAIN is not set');

    const isValid = await validateApiToken(apiToken);
    if (!isValid) {
        throw new Error(`Invalid Telegram API token for user ${userId}`);
    }

    console.log(isValid);

    const secret = getWebhookSecret(userId);
    const webhookUrl = `${PUBLIC_DOMAIN.replace(/\/$/, '')}/webhook/${encodeURIComponent(userId)}/${encodeURIComponent(secret)}`;

    const url = `https://api.telegram.org/bot${apiToken}/setWebhook?url=${encodeURIComponent(webhookUrl)}`;
    await axios.get(url);

    platformTokens.set(String(userId), apiToken);
    console.log(`Webhook set for user ${userId} -> ${webhookUrl}`);
}

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
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });
    return res.data;
}

async function startAllUsers() {
    const users = await fetchActiveUsers();

    for (const { userId, apiToken } of users) {
        try {
            await registerWebhook(userId, apiToken);
        } catch (err) {
            console.error(`Failed to register webhook for user ${userId}:`, err.message);
        }
    }
}

app.post('/start-platform', verifyServiceToken, async (req, res) => {
    const { userId, apiToken } = req.body;

    if (!userId || !apiToken) {
        return res.status(400).json({ error: 'userId and apiToken are required' });
    }

    try {
        await registerWebhook(userId, apiToken);
        res.json({ message: `Telegram Platform has been started for ${userId}` });
    } catch (err) {
        console.error('Error in /start-platform:', err.message);
        res.status(500).json({ error: 'Failed to start platform' });
    }
});

app.post('/stop-platform', verifyServiceToken, async (req, res) => {
    const { userId } = req.body;

    if (!userId) {
        return res.status(400).json({ error: 'userId is required' });
    }

    try {
        await unregisterWebhook(userId);
        res.json({ message: `Telegram Platform has been stopped for ${userId}` });
    } catch (err) {
        console.error('Error in /stop-platform:', err.message);
        res.status(500).json({ error: 'Failed to stop platform' });
    }
});

(async () => {
    try {
        await producer.connect();
        await startConsumer();
        await startAllUsers();

        app.listen(PORT, () =>
            console.log(`Telegram microservice listening on port ${PORT}`)
        );
    } catch (err) {
        console.error('Failed to bootstrap Telegram microservice:', err);
        process.exit(1);
    }
})();
