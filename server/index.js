require('dotenv').config();
const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID;
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const path = require('path');

const app = express();
const PORT = 3000;

app.use(cors());

// 1. Serve static frontend files from the current directory
app.use(express.static(__dirname));

// 2. Proxy endpoint for Invidious (YouTube) to bypass browser CORS restrictions
app.get('/api/youtube', async (req, res) => {
    const query = req.query.q;
    const INVIDIOUS_INSTANCE = 'https://puffyan.us'; // You may need to change this if it goes down
    
    try {
        const response = await fetch(`${INVIDIOUS_INSTANCE}/api/v1/search?q=${encodeURIComponent(query)}&type=video`);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("YouTube Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Invidious" });
    }
});

// 3. Proxy endpoint for Jamendo to keep things safe and fast
app.get('/api/jamendo', async (req, res) => {
    const query = req.query.q;
    const JAMENDO_CLIENT_ID = '56d30c55'; 
    
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

app.listen(PORT, () => {
    console.log(`Spitify Server running at http://localhost:${PORT}`);
});
