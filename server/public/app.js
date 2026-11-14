const searchBox = document.getElementById('search-box');
const resultsContainer = document.getElementById('results-container');
const audioPlayer = document.getElementById('audio-player');
let debounceTimer;

// When the page is served by the backend itself, talk to that same server.
// Opened as a local file, it falls back to a server running on this machine.
const SERVER = location.protocol.startsWith('http') ? location.origin : 'http://localhost:3000';
let latestSearch = 0;

searchBox.addEventListener('input', (e) => {
    const query = e.target.value.trim();
    clearTimeout(debounceTimer);

    if (!query) {
        latestSearch++;
        resultsContainer.innerHTML = '';
        return;
    }

    debounceTimer = setTimeout(() => searchAllSources(query), 600);
});

function api(route, query, fallback) {
    return fetch(`${SERVER}/api/${route}?q=${encodeURIComponent(query)}`)
        .then(res => res.json())
        .catch(() => fallback);
}

async function searchAllSources(searchQuery) {
    // Typing fast fires several searches; only the newest one is allowed to draw.
    const searchId = ++latestSearch;
    resultsContainer.textContent = 'Searching...';

    let catalogTracks = [];
    let youtubeTracks = [];
    let youtubePending = true;
    let soundcloudTracks = [];
    let soundcloudPending = true;
    let pending = 5;

    const draw = () => {
        if (searchId !== latestSearch) return;
        displayResults([...youtubeTracks, ...soundcloudTracks, ...catalogTracks], youtubePending || soundcloudPending || pending > 0);
    };

    // YouTube is the slow one, so it fills in on its own when it's ready.
    api('youtube', searchQuery, []).then(res => {
        youtubeTracks = (Array.isArray(res) ? res : [])
            .filter(t => !t.error && t.audioUrl)
            .map(t => ({ ...t, source: 'YouTube' }));
        youtubePending = false;
        draw();
    });

    // SoundCloud songs that only come as HLS can't play in a plain <audio> tag on most browsers.
    api('soundcloud', searchQuery, []).then(res => {
        soundcloudTracks = (Array.isArray(res) ? res : [])
            .filter(t => !t.error && t.audioUrl && !t.hls)
            .map(t => ({ ...t, source: 'SoundCloud' }));
        soundcloudPending = false;
        draw();
    });

    // Each source shows up as soon as it answers instead of waiting for the slowest one.
    const bySource = {};
    const order = ['jamendo', 'audius', 'deezer', 'archive', 'ccmixter'];
    const add = (name, tracks) => {
        bySource[name] = tracks;
        pending--;
        catalogTracks = order.flatMap(n => bySource[n] || []);
        draw();
    };

    api('jamendo', searchQuery, { results: [] }).then(res => add('jamendo', (res.results || []).map(track => ({
        title: track.name, artist: track.artist_name,
        image: track.album_image || 'https://placehold.co/60x45',
        audioUrl: track.audio, source: 'Jamendo', id: track.id
    }))));

    api('audius', searchQuery, { data: [] }).then(res => add('audius', (res.data || []).slice(0, 5).map(track => ({
        title: track.title, artist: track.user?.name,
        image: track.artwork?.['150x150'] || 'https://placehold.co/60x45',
        audioUrl: `https://api.audius.co/v1/tracks/${track.id}/stream?app_name=spitify`,
        source: 'Audius', id: track.id
    }))));

    api('deezer', searchQuery, { data: [] }).then(res => add('deezer', (res.data || []).slice(0, 5).map(track => ({
        title: track.title, artist: track.artist?.name,
        image: track.album?.cover_medium || 'https://placehold.co/60x45',
        audioUrl: track.preview, source: 'Deezer (Preview)', id: track.id
    }))));

    api('archive', searchQuery, []).then(res => add('archive', Array.isArray(res) ? res : []));
    api('ccmixter', searchQuery, []).then(res => add('ccmixter', Array.isArray(res) ? res : []));
}

// Titles and artist names come from other people's servers, so they go in as text, never as HTML.
function displayResults(tracks, stillSearching) {
    resultsContainer.replaceChildren();

    if (stillSearching) {
        const note = document.createElement('p');
        note.className = 'pending';
        note.textContent = 'Still searching...';
        resultsContainer.appendChild(note);
    }

    if (tracks.length === 0) {
        if (!stillSearching) resultsContainer.textContent = 'No matching tracks found.';
        return;
    }

    tracks.forEach(track => {
        const row = document.createElement('div');
        row.className = `track-item ${String(track.source || '').toLowerCase().replace(/[^a-z0-9]/g, '')}`;

        const img = document.createElement('img');
        img.src = track.image || 'https://placehold.co/60x45';
        img.alt = '';

        const info = document.createElement('div');
        info.className = 'track-info';
        const title = document.createElement('strong');
        title.textContent = track.title || 'Untitled';
        const tag = document.createElement('span');
        tag.className = 'source-tag';
        tag.textContent = track.source || '';
        const artist = document.createElement('small');
        artist.textContent = track.artist || '';
        info.append(title, ' ', tag, document.createElement('br'), artist);

        const button = document.createElement('button');
        button.className = 'play-btn';
        button.textContent = 'Play';
        button.addEventListener('click', () => playTrack(track.audioUrl));

        row.append(img, info, button);
        resultsContainer.appendChild(row);
    });
}

function playTrack(url) {
    if (!url) return;
    audioPlayer.src = url;
    audioPlayer.play().catch(err => console.log('Stream blocked:', err));
}
