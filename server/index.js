require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const path = require('path');

const app = express();
// Render requires the app to listen on its provided PORT
const PORT = process.env.PORT || 3000; 

app.use(cors());
app.use(express.static(__dirname));

// Use the Render environment variable, or fall back to the public playground key
const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID || '56d30c55';
const INVIDIOUS_INSTANCE = 'https://inv.nadeko.net'; // YouTube Proxy

// 1. Jamendo API Proxy
app.get('/api/jamendo', async (req, res) => {
    const query = req.query.q;
    try {
        const url = `https://api.jamendo.com/v3.0/tracks/?client_id=${JAMENDO_CLIENT_ID}&format=json&limit=10&search=${encodeURIComponent(query)}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Jamendo Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Jamendo" });
    }
});

// 2. YouTube (Invidious) API Proxy
app.get('/api/youtube', async (req, res) => {
    const query = req.query.q;
    try {
        const response = await fetch(`${INVIDIOUS_INSTANCE}/api/v1/search?q=${encodeURIComponent(query)}&type=video`);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("YouTube Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Invidious" });
    }
});

// Start the server
app.listen(PORT, () => {
    console.log(`Spitify Server running on port ${PORT}`);
});
