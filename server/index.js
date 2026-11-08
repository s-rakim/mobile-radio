require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
// Only the web page lives in public/. Serving the whole folder would hand out this file and package.json.
app.use(express.static(path.join(__dirname, 'public')));

// Every search route needs ?q=. Without this, an empty box searched for the word "undefined".
app.use('/api', (req, res, next) => {
    const q = (req.query.q || '').toString().trim();
    if (!q) return res.status(400).json({ error: 'Add a search with ?q=' });
    req.query.q = q.slice(0, 200);
    next();
});

// node-fetch v2 takes a timeout. Ten seconds keeps one slow source from stalling the whole page.
const TIMEOUT = 10000;
const getJson = (url) => fetch(url, { timeout: TIMEOUT }).then((r) => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
});

// --- Configuration ---
// The old shared Jamendo id is rejected now, so Jamendo only runs when you set your own.
const JAMENDO_CLIENT_ID = process.env.JAMENDO_CLIENT_ID || '';
const AUDIUS_APP_NAME = 'spitify';

// --- 1. JAMENDO (Full Tracks) ---
app.get('/api/jamendo', async (req, res) => {
    if (!JAMENDO_CLIENT_ID) return res.json({ results: [] });
    try {
        const url = `https://api.jamendo.com/v3.0/tracks/?client_id=${JAMENDO_CLIENT_ID}&format=json&limit=10&search=${encodeURIComponent(req.query.q)}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "Jamendo error" }); }
});

// --- 2. AUDIUS (Full Tracks) ---
app.get('/api/audius', async (req, res) => {
    try {
        // discoveryprovider.audius.co is the old host; api.audius.co picks a healthy node for you.
        const url = `https://api.audius.co/v1/tracks/search?query=${encodeURIComponent(req.query.q)}&app_name=${AUDIUS_APP_NAME}`;
        res.json(await getJson(url));
    } catch (error) { res.status(500).json({ error: "Audius error" }); }
});

// --- 3. DEEZER (30s Previews) ---
app.get('/api/deezer', async (req, res) => {
    try {
        const response = await fetch(`https://api.deezer.com/search?q=${encodeURIComponent(req.query.q)}&limit=10`);
        const data = await response.json();
        res.json(data);
    } catch (error) { res.status(500).json({ error: "Deezer error" }); }
});

// --- 4. INTERNET ARCHIVE (netlabels and live shows, free to stream) ---
app.get('/api/archive', async (req, res) => {
    try {
        const terms = req.query.q.replace(/[^\p{L}\p{N}\s]/gu, ' ').trim().split(/\s+/).join(' AND ');
        const q = `(${terms}) AND mediatype:audio AND collection:(netlabels OR etree)`;
        const search = await getJson(`https://archive.org/advancedsearch.php?q=${encodeURIComponent(q)}&fl[]=identifier&fl[]=title&fl[]=creator&rows=3&output=json`);
        const docs = (search.response && search.response.docs) || [];
        const albums = await Promise.all(docs.map((doc) => getJson(`https://archive.org/metadata/${doc.identifier}`).catch(() => null)));
        const tracks = [];
        albums.forEach((meta, i) => {
            if (!meta || !meta.files) return;
            const id = docs[i].identifier;
            const creator = [].concat(meta.metadata.creator || 'Internet Archive')[0];
            meta.files
                .filter((f) => f.format === 'VBR MP3')
                .slice(0, 4)
                .forEach((f) => tracks.push({
                    title: f.title || f.name.replace(/\.mp3$/i, ''),
                    artist: f.artist || f.creator || creator,
                    image: `https://archive.org/services/img/${id}`,
                    audioUrl: `https://archive.org/download/${id}/${f.name.split('/').map(encodeURIComponent).join('/')}`,
                    source: 'Internet Archive',
                    id: `${id}/${f.name}`,
                }));
        });
        res.json(tracks);
    } catch (error) { res.status(500).json({ error: 'Internet Archive error' }); }
});

// --- 5. CCMIXTER (Creative Commons, open API) ---
app.get('/api/ccmixter', async (req, res) => {
    try {
        const uploads = await getJson(`https://ccmixter.org/api/query?f=json&limit=10&sort=rank&search_type=all&search=${encodeURIComponent(req.query.q)}`);
        res.json((Array.isArray(uploads) ? uploads : []).map((u) => {
            const file = (u.files || []).find((f) => f.file_format_info && f.file_format_info.mime_type === 'audio/mpeg');
            return file && {
                title: u.upload_name,
                artist: u.user_real_name || u.user_name,
                image: '',
                audioUrl: file.download_url,
                source: 'ccMixter',
                id: u.upload_id,
            };
        }).filter(Boolean));
    } catch (error) { res.status(500).json({ error: 'ccMixter error' }); }
});

app.listen(PORT, () => {
    console.log(`Spitify Server running on port ${PORT}`);
});
