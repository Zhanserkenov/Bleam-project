require('dotenv').config();

const { makeWASocket, useMultiFileAuthState, fetchLatestBaileysVersion } = require('@whiskeysockets/baileys');
const express = require('express');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const axios = require('axios');
const { createClient } = require('redis');

const app = express();
app.use(express.json());

const APP_PORT = process.env.PORT || 3000;
const SERVICE_NAME = process.env.SERVICE_NAME;
const SERVICE_SECRET = process.env.JWT_SERVICE_SECRET;
const PLATFORM_SERVICE_URL = process.env.PLATFORM_SERVICE_URL;

// Redis URL
const REDIS_URL = process.env.REDIS_URL;

const redis = createClient({ url: REDIS_URL });

const sockets = new Map();
const manualStops = new Set();

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

async function startSocket(userId) {
    manualStops.delete(userId);
    if (!sockets.has(userId)) sockets.set(userId, { attempts: 0 });
    const sessionFolder = path.join(__dirname, 'sessions', `auth-${userId}`);
    const { state, saveCreds } = await useMultiFileAuthState(sessionFolder);
    const { version } = await fetchLatestBaileysVersion();

    const sock = makeWASocket({ version, auth: state });
    sockets.get(userId).sock = sock;
    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr && !manualStops.has(userId)) {
            // publish to Redis stream whatsapp_qr
            try {
                await redis.sendCommand(['XADD', 'whatsapp_qr', '*', 'userId', String(userId), 'qrCode', qr]);
            } catch (e) {
                console.error('Failed to publish qr to redis', e);
            }
        }

        if (connection === 'open') {
            sockets.get(userId).attempts = 0;
            try {
                await redis.sendCommand(['XADD', 'whatsapp.status', '*', 'userId', String(userId), 'status', 'CONNECTED']);
            } catch (e) {
                console.error('Failed to publish status CONNECTED', e);
            }
            console.log(`User ${userId} connected to WhatsApp`);
        }

        if (connection === 'close') {
            if (manualStops.has(userId)) {
                await cleanup(userId);
                return;
            }

            const info = lastDisconnect?.error?.message || 'Unknown';
            let { attempts } = sockets.get(userId);
            attempts++;
            sockets.get(userId).attempts = attempts;
            console.log(`User ${userId} socket closed (${info}), attempt ${attempts}`);

            if (attempts < 3) {
                console.log(`Reconnecting user ${userId}…`);
                // wait a bit before reconnecting to avoid tight loop
                setTimeout(() => startSocket(userId), 1000 * 2);
            } else {
                console.log(`Max reconnects reached for user ${userId}, deactivating.`);
                await cleanup(userId);
                try {
                    await redis.sendCommand(['XADD', 'whatsapp.status', '*', 'userId', String(userId), 'status', 'DISCONNECTED']);
                } catch (e) {
                    console.error('Failed to publish status DISCONNECTED', e);
                }
            }
        }
    });

    sock.ev.on('messages.upsert', async (m) => {
        try {
            const msg = m.messages[0];
            if (!msg.message || msg.key.fromMe) return;
            const text = msg.message.conversation || msg.message.extendedTextMessage?.text;
            const chatUserId = msg.key.remoteJid.split('@')[0];
            await sendToSpring(chatUserId, text, userId);
        } catch (e) {
            console.error('Error handling messages.upsert', e);
        }
    });
}

async function cleanup(userId) {
    const sessionFolder = path.join(__dirname, 'sessions', `auth-${userId}`);
    if (fs.existsSync(sessionFolder)) {
        fs.rmSync(sessionFolder, { recursive: true, force: true });
        console.log(`Deleted session folder for user ${userId}`);
    }
    const record = sockets.get(userId);
    if (record?.sock) {
        try { await record.sock.logout(); } catch(e){/*ignore*/ }
    }
    sockets.delete(userId);
    manualStops.delete(userId);
}

async function sendToSpring(chatUserId, messageText, userId) {
    // publish into whatsapp.incoming stream
    try {
        await redis.sendCommand(['XADD', 'whatsapp.incoming', '*',
            'chatUserId', chatUserId,
            'message', messageText,
            'userId', String(userId)
        ]);
    } catch (e) {
        console.error('Failed to publish incoming message to redis', e);
    }
}

async function startOutgoingConsumerLoop() {
    // This consumer reads from stream `whatsapp.outgoing` using a consumer group,
    // acknowledges processed messages, and sends them to WhatsApp sockets.
    const STREAM = 'whatsapp.outgoing';
    const GROUP = 'whatsapp-bridge-group';
    const consumerName = `consumer-${process.pid}-${Math.floor(Math.random()*1000)}`;

    // create group if not exists
    try {
        await redis.sendCommand(['XGROUP', 'CREATE', STREAM, GROUP, '$', 'MKSTREAM']);
        console.log('Created consumer group', GROUP, 'for stream', STREAM);
    } catch (err) {
        // group may already exist -> ignore "BUSYGROUP" message
        if (!(err && String(err).includes('BUSYGROUP'))) {
            console.error('Failed to create group', err);
            // but continue
        }
    }

    while (true) {
        try {
            // XREADGROUP GROUP <group> <consumer> BLOCK 2000 COUNT 10 STREAMS <stream> >
            const reply = await redis.sendCommand([
                'XREADGROUP', 'GROUP', GROUP, consumerName,
                'BLOCK', '2000',
                'COUNT', '10',
                'STREAMS', STREAM, '>'
            ]);

            if (!reply) {
                // nothing read within block
                continue;
            }

            // reply format: [ [ stream, [ [ id, [ field, val, field, val ... ] ], ... ] ] ]
            for (const streamData of reply) {
                const streamName = streamData[0]; // should be 'whatsapp.outgoing'
                const entries = streamData[1];
                for (const entry of entries) {
                    const id = entry[0];
                    const arr = entry[1]; // flat array [field, val, field, val...]
                    const obj = {};
                    for (let i = 0; i < arr.length; i += 2) {
                        obj[String(arr[i])] = String(arr[i + 1]);
                    }

                    try {
                        const chatUserId = obj.chatUserId;
                        const text = obj.message;
                        const userId = obj.userId;
                        const record = sockets.get(Number(userId));
                        if (!record?.sock) {
                            console.error(`No socket for user ${userId} (outgoing id=${id})`);
                        } else {
                            // send via baileys
                            await record.sock.sendMessage(`${chatUserId}@s.whatsapp.net`, { text });
                        }

                        // acknowledge
                        await redis.sendCommand(['XACK', STREAM, GROUP, id]);

                    } catch (procErr) {
                        console.error('Error processing outgoing stream entry', procErr);
                        // do not ack -> stays pending for reprocessing
                    }
                }
            }
        } catch (err) {
            console.error('Error in outgoing consumer loop', err);
            // on error, sleep a bit to avoid tight loop
            await new Promise(r => setTimeout(r, 2000));
        }
    }
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
    const userIds = await fetchActiveUsers();
    for (const userId of userIds) {
        try {
            await startSocket(userId);
        } catch (e) {
            console.error('Failed to start socket for user', userId, e);
        }
    }
}

app.post('/start-platform', verifyServiceToken, async (req, res) => {
    const { userId } = req.body;
    if (sockets.has(userId)) return res.status(200).send({ message: 'Already active' });
    try {
        await startSocket(userId);
        res.send({ message: 'Platform started' });
    } catch (e) {
        res.status(500).send({ error: 'Failed to start' });
    }
});

app.post('/stop-platform', verifyServiceToken, async (req, res) => {
    const { userId } = req.body;
    manualStops.add(userId);
    const { sock } = sockets.get(userId) || {};
    if (sock) {
        try {
            await sock.logout();
        } catch (e) { /* ignore */ }
        res.send({ message: 'Platform stopped' });
    } else {
        res.status(404).send({ error: 'Not found' });
    }
});

(async () => {
    try {
        await redis.connect();
        console.log('Connected to Redis', REDIS_URL);

        // start outgoing consumer loop
        startOutgoingConsumerLoop().catch(err => console.error('Outgoing loop crashed', err));

        // start existing sessions
        await startAllUsers();

        app.listen(APP_PORT, '0.0.0.0', () =>
            console.log(`WhatsApp microservice listening on port ${APP_PORT}`)
        );
    } catch (err) {
        console.error('Failed to bootstrap WhatsApp microservice:', err);
        process.exit(1);
    }
})();
