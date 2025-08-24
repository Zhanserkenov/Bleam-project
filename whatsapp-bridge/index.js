require('dotenv').config();

const { makeWASocket, useMultiFileAuthState, fetchLatestBaileysVersion } = require('@whiskeysockets/baileys');
const { Kafka } = require('kafkajs');
const express = require('express');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const axios = require('axios');

const app = express();
app.use(express.json());

const APP_PORT = process.env.PORT;

const SERVICE_NAME = process.env.SERVICE_NAME;
const SERVICE_SECRET = process.env.JWT_SERVICE_SECRET;

const PLATFORM_SERVICE_URL = process.env.PLATFORM_SERVICE_URL;

const KAFKA_BROKERS = (process.env.KAFKA_BROKERS).split(',');
const KAFKA_CLIENT_ID = process.env.KAFKA_CLIENT_ID;
const KAFKA_CONSUMER_GROUP = process.env.KAFKA_CONSUMER_GROUP;

const kafka = new Kafka({ clientId: KAFKA_CLIENT_ID, brokers: KAFKA_BROKERS });
const producer = kafka.producer();
const consumer = kafka.consumer({ groupId: KAFKA_CONSUMER_GROUP });

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
            await producer.send({
                topic: 'whatsapp_qr',
                messages: [{ key: userId.toString(), value: JSON.stringify({ userId, qrCode: qr }) }]
            });
        }

        if (connection === 'open') {
            sockets.get(userId).attempts = 0;
            await producer.send({
                topic: 'whatsapp.status',
                messages: [{ key: userId.toString(), value: JSON.stringify({ userId, status: 'CONNECTED' }) }]
            });
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
                await startSocket(userId);
            } else {
                console.log(`Max reconnects reached for user ${userId}, deactivating.`);
                await cleanup(userId);
                await producer.send({
                    topic: 'whatsapp.status',
                    messages: [{ key: userId.toString(), value: JSON.stringify({ userId, status: 'DISCONNECTED' }) }]
                });
            }
        }
    });

    sock.ev.on('messages.upsert', async (m) => {
        const msg = m.messages[0];
        if (!msg.message || msg.key.fromMe) return;
        const text = msg.message.conversation || msg.message.extendedTextMessage?.text;
        const chatUserId = msg.key.remoteJid.split('@')[0];
        await sendToSpring(chatUserId, text, userId);
    });
}

async function cleanup(userId) {
    const sessionFolder = path.join(__dirname, 'sessions', `auth-${userId}`);
    if (fs.existsSync(sessionFolder)) {
        fs.rmSync(sessionFolder, { recursive: true, force: true });
        console.log(`Deleted session folder for user ${userId}`);
    }
    sockets.delete(userId);
    manualStops.delete(userId);
}

async function sendToSpring(chatUserId, messageText, userId) {
    await producer.send({ topic: 'whatsapp.incoming', messages: [{ key: chatUserId, value: JSON.stringify({ chatUserId, message: messageText, userId }) }] });
}

async function startConsumer() {
    await consumer.connect();
    await consumer.subscribe({ topic: 'whatsapp.outgoing' });
    await consumer.run({
        eachMessage: async ({ message }) => {
            const { chatUserId, message: text, userId } = JSON.parse(message.value.toString());
            const record = sockets.get(userId);
            if (!record?.sock) return console.error(`No socket for user ${userId}`);
            await record.sock.sendMessage(`${chatUserId}@s.whatsapp.net`, { text });
        }
    });
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
        await startSocket(userId);
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
        await sock.logout();
        res.send({ message: 'Platform stopped' });
    } else {
        res.status(404).send({ error: 'Not found' });
    }
});

(async () => {
    try {
        await producer.connect();
        await startConsumer();
        await startAllUsers();

        app.listen(APP_PORT, '0.0.0.0', () =>
            console.log(`WhatsApp microservice listening on port ${APP_PORT}`)
        );
    } catch (err) {
        console.error('Failed to bootstrap WhatsApp microservice:', err);
        process.exit(1);
    }
})();
