require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const path = require('path');
const { Tubidy } = require("tubidy-scrape");
const bcfetch = require('bandcamp-fetch');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.static(__dirname));

// --- Configuration ---
const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID || '56d30c55';
const SOUNDCLOUD_CLIENT_ID = process.env.SOUNDCLOUD_CLIENT_ID; // Must be set in Render
const AUDIUS_APP_NAME = 'SpitifyClone';
const INVIDIOUS_INSTANCE = 'https://inv.nadeko.net';
const tubidy = Tubidy();

// --- 1. JAMENDO (Full Tracks) ---
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

// --- 2. YOUTUBE (via Invidious - Full Audio) ---
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

// --- 3. AUDIUS (Full Tracks) ---
app.get('/api/audius', async (req, res) => {
    const query = req.query.q;
    try {
        const url = `https://discoveryprovider.audius.co/v1/tracks/search?query=${encodeURIComponent(query)}&app_name=${AUDIUS_APP_NAME}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Audius Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Audius" });
    }
});

// --- 4. SOUNDCLOUD (Full Tracks) ---
app.get('/api/soundcloud', async (req, res) => {
    const query = req.query.q;
    if (!SOUNDCLOUD_CLIENT_ID) {
        return res.status(400).json({ error: "SoundCloud client ID not configured." });
    }
    try {
        const url = `https://api.soundcloud.com/tracks?q=${encodeURIComponent(query)}&client_id=${SOUNDCLOUD_CLIENT_ID}&limit=10`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("SoundCloud Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from SoundCloud" });
    }
});

// --- 5. DEEZER (30s Previews) ---
app.get('/api/deezer', async (req, res) => {
    const query = req.query.q;
    try {
        const url = `https://api.deezer.com/search?q=${encodeURIComponent(query)}&limit=10`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Deezer Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Deezer" });
    }
});

// --- 6. TUBIDY (Scraper - Full MP3s) ---
app.get('/api/tubidy', async (req, res) => {
    const query = req.query.q;
    try {
        const searchResults = await tubidy.search(query, 1);
        if (!searchResults || searchResults.length === 0) {
            return res.json([]);
        }
        const firstResult = searchResults[0];
        const downloadData = await tubidy.download(firstResult.id, "mp3audio");
        const formattedResult = {
            title: firstResult.title,
            artist: "Tubidy Source",
            audioUrl: downloadData.play,
            image: "https://placehold.co/60x45",
            source: "Tubidy",
            id: firstResult.id
        };
        res.json([formattedResult]);
    } catch (error) {
        console.error("Tubidy Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Tubidy" });
    }
});

// --- 7. JIOSAAVN (Full Tracks via Unofficial API) ---
app.get('/api/jiosaavn', async (req, res) => {
    const query = req.query.q;
    try {
        // Using the public saavn.dev instance as the API endpoint
        const url = `https://jiosaavn-api-private.vercel.app/search/songs?query=${encodeURIComponent(query)}&limit=10`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("JioSaavn Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from JioSaavn" });
    }
});

// --- 8. BANDCAMP (Full Tracks via Scraper) ---
app.get('/api/bandcamp', async (req, res) => {
    const query = req.query.q;
    try {
        // Note: bandcamp-fetch uses Puppeteer, which may be resource-intensive.
        const searchResults = await bcfetch.search.albums({ query: query, page: 1 });
        const tracks = [];
        // Process top 3 albums to find streamable tracks
        for (const album of searchResults.items.slice(0, 3)) {
            const albumInfo = await bcfetch.album.getInfo({ albumUrl: album.url });
            for (const track of albumInfo.tracks) {
                if (track.streamUrl) {
                    tracks.push({
                        title: track.name,
                        artist: albumInfo.artist.name,
                        audioUrl: track.streamUrl,
                        image: albumInfo.imageUrl || 'https://placehold.co/60x45',
                        source: 'Bandcamp',
                        id: track.id
                    });
                }
            }
            if (tracks.length >= 5) break; // Limit results
        }
        res.json(tracks);
    } catch (error) {
        console.error("Bandcamp Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Bandcamp" });
    }
});

app.listen(PORT, () => {
    console.log(`Spitify Server running on port ${PORT}`);
});
