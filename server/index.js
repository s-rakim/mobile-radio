require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const { Tubidy } = require("tubidy-scrape");
const bcfetch = require('bandcamp-fetch');
const scdl = require('soundcloud-downloader').default;

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.static(__dirname));

// --- Configuration ---
const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID || '56d30c55';
const AUDIUS_APP_NAME = 'SpitifyClone';
const INVIDIOUS_INSTANCE = 'https://inv.nadeko.net';
const tubidy = Tubidy();

// --- 1. JAMENDO (Full Tracks) ---
app.get('/api/jamendo', async (req, res) => {
    try {
        const url = `https://api.jamendo.com/v3.0/tracks/?client_id=${JAMENDO_CLIENT_ID}&format=json&limit=10&search=${encodeURIComponent(req.query.q)}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "Jamendo error" }); }
});

// --- 2. YOUTUBE (via Invidious) ---
app.get('/api/youtube', async (req, res) => {
    try {
        const response = await fetch(`${INVIDIOUS_INSTANCE}/api/v1/search?q=${encodeURIComponent(req.query.q)}&type=video`);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "YouTube error" }); }
});

// --- 3. AUDIUS (Full Tracks) ---
app.get('/api/audius', async (req, res) => {
    try {
        const url = `https://discoveryprovider.audius.co/v1/tracks/search?query=${encodeURIComponent(req.query.q)}&app_name=${AUDIUS_APP_NAME}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "Audius error" }); }
});

// --- 4. SOUNDCLOUD (Bypassed) ---
app.get('/api/soundcloud', async (req, res) => {
    try {
        // scdl automatically handles the client ID
        const searchResult = await scdl.search({ query: req.query.q, limit: 10 });
        res.json(searchResult.collection || []);
    } catch (error) { 
        console.error("SoundCloud Error:", error);
        res.status(500).json({ error: "SoundCloud error" }); 
    }
});

// --- 5. DEEZER (30s Previews) ---
app.get('/api/deezer', async (req, res) => {
    try {
        const response = await fetch(`https://api.deezer.com/search?q=${encodeURIComponent(req.query.q)}&limit=10`);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "Deezer error" }); }
});

// --- 6. TUBIDY (MP3 Scraper) ---
app.get('/api/tubidy', async (req, res) => {
    try {
        const searchResults = await tubidy.search(req.query.q, 1);
        if (!searchResults || searchResults.length === 0) return res.json([]);
        
        const firstResult = searchResults[0];
        const downloadData = await tubidy.download(firstResult.id, "mp3audio");
        
        res.json([{
            title: firstResult.title,
            artist: "Tubidy Source",
            audioUrl: downloadData.play,
            image: "https://placehold.co/60x45",
            source: "Tubidy",
            id: firstResult.id
        }]);
    } catch (error) { res.status(500).json({ error: "Tubidy error" }); }
});

// --- 7. JIOSAAVN (Unofficial API) ---
app.get('/api/jiosaavn', async (req, res) => {
    try {
        const response = await fetch(`https://jiosaavn-api-private.vercel.app/search/songs?query=${encodeURIComponent(req.query.q)}&limit=10`);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "JioSaavn error" }); }
});

// --- 8. BANDCAMP (Scraper) ---
app.get('/api/bandcamp', async (req, res) => {
    try {
        const searchResults = await bcfetch.search.albums({ query: req.query.q, page: 1 });
        const tracks = [];
        for (const album of searchResults.items.slice(0, 2)) {
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
            if (tracks.length >= 5) break; 
        }
        res.json(tracks);
    } catch (error) { res.status(500).json({ error: "Bandcamp error" }); }
});

app.listen(PORT, () => {
    console.log(`Spitify Server running on port ${PORT}`);
});
