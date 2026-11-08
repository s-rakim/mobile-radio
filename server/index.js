require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const { Tubidy } = require("tubidy-scrape"); // New dependency

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.static(__dirname));

const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID || '56d30c55';
const SOUNDCLOUD_CLIENT_ID = process.env.SOUNDCLOUD_CLIENT_ID;
const AUDIUS_APP_NAME = process.env.AUDIUS_APP_NAME || 'SpitifyClone';
const INVIDIOUS_INSTANCE = 'https://inv.nadeko.net';

// --- 1. EXISTING ROUTES ---
// Jamendo (Full Tracks)
app.get('/api/jamendo', async (req, res) => {
    // ... (your existing Jamendo route code)
});

// YouTube via Invidious (Full Audio)
app.get('/api/youtube', async (req, res) => {
    // ... (your existing YouTube route code)
});

// --- 2. NEW AUDIUS ROUTE (Full Tracks) ---
app.get('/api/audius', async (req, res) => {
    const query = req.query.q;
    try {
        // Audius has a public search endpoint
        const url = `https://discoveryprovider.audius.co/v1/tracks/search?query=${encodeURIComponent(query)}&app_name=${AUDIUS_APP_NAME}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Audius Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Audius" });
    }
});

// --- 3. NEW SOUNDCLOUD ROUTE (Full Tracks) ---
app.get('/api/soundcloud', async (req, res) => {
    const query = req.query.q;
    if (!SOUNDCLOUD_CLIENT_ID) {
        return res.status(400).json({ error: "SoundCloud client ID not configured." });
    }
    try {
        // SoundCloud search endpoint
        const url = `https://api.soundcloud.com/tracks?q=${encodeURIComponent(query)}&client_id=${SOUNDCLOUD_CLIENT_ID}&limit=10`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("SoundCloud Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from SoundCloud" });
    }
});

// --- 4. NEW DEEZER ROUTE (30s Previews) ---
app.get('/api/deezer', async (req, res) => {
    const query = req.query.q;
    try {
        // Deezer public search endpoint (no key needed)
        const url = `https://api.deezer.com/search?q=${encodeURIComponent(query)}&limit=10`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Deezer Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Deezer" });
    }
});

// --- 5. NEW TUBIDY ROUTE (Scraper - Full MP3s) ---
const tubidy = Tubidy();
app.get('/api/tubidy', async (req, res) => {
    const query = req.query.q;
    try {
        const searchResults = await tubidy.search(query, 1); // Search 1 page
        if (!searchResults || searchResults.length === 0) {
            return res.json([]);
        }
        // We need to get the download link for the first result
        const firstResult = searchResults[0];
        const downloadData = await tubidy.download(firstResult.id, "mp3audio");
        
        // Format the response to match what the frontend expects
        const formattedResult = {
            title: firstResult.title,
            artist: "Tubidy Source", // Tubidy doesn't provide artist info in search
            audioUrl: downloadData.play, // The playable MP3 URL
            image: "https://placehold.co/60x45", // Placeholder
            source: "Tubidy",
            id: firstResult.id
        };
        res.json([formattedResult]); // Return as an array
    } catch (error) {
        console.error("Tubidy Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Tubidy" });
    }
});

app.listen(PORT, () => {
    console.log(`Spitify Server running on port ${PORT}`);
});
