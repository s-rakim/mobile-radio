const searchBox = document.getElementById('search-box');
const resultsContainer = document.getElementById('results-container');
const audioPlayer = document.getElementById('audio-player');
let debounceTimer;

// When the page is served by the backend itself, talk to that same server.
// Opened as a local file, it falls back to the Render deployment.
const SERVER_IP = location.protocol.startsWith('http') ? location.origin : 'https://spitify-backend.onrender.com';
let latestSearch = 0;

searchBox.addEventListener('input', (e) => {
    const query = e.target.value.trim();
    clearTimeout(debounceTimer);

    if (!query) {
        resultsContainer.innerHTML = '';
        return;
    }

    debounceTimer = setTimeout(() => {
        searchAllSources(query);
    }, 600); 
});

async function searchAllSources(searchQuery) {
    // Typing fast fires several searches; only the newest one is allowed to draw.
    const searchId = ++latestSearch;
    resultsContainer.textContent = 'Searching...';

    try {
        const [
            jamendoRes, youtubeRes, audiusRes, soundcloudRes, 
            deezerRes, tubidyRes, jiosaavnRes, bandcampRes,
            archiveRes, ccmixterRes
        ] = await Promise.all([
            fetch(`${SERVER_IP}/api/jamendo?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ results: [] })),
            fetch(`${SERVER_IP}/api/youtube?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/audius?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: [] })),
            fetch(`${SERVER_IP}/api/soundcloud?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/deezer?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: [] })),
            fetch(`${SERVER_IP}/api/tubidy?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/jiosaavn?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: { results: [] } })),
            fetch(`${SERVER_IP}/api/bandcamp?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/archive?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/ccmixter?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => [])
        ]);
        if (searchId !== latestSearch) return;

        // 1. Jamendo
        const jamendoTracks = (jamendoRes.results || []).map(track => ({
            title: track.name, artist: track.artist_name,
            image: track.album_image || 'https://placehold.co/60x45',
            audioUrl: track.audio, source: 'Jamendo', id: track.id
        }));

        // 2. YouTube
        const youtubeTracks = (Array.isArray(youtubeRes) ? youtubeRes.slice(0, 5) : []).map(video => ({
            title: video.title, artist: video.author,
            image: video.videoThumbnails?.find(t => t.quality === 'medium')?.url || 'https://placehold.co/60x45',
            audioUrl: `https://inv.nadeko.net/latest_version?id=${video.videoId}&itag=140`, 
            source: 'YouTube', id: video.videoId
        }));

        // 3. Audius
        const audiusTracks = (audiusRes.data || []).slice(0, 5).map(track => ({
            title: track.title, artist: track.user.name,
            image: track.artwork?.['150x150'] || 'https://placehold.co/60x45',
            audioUrl: `https://api.audius.co/v1/tracks/${track.id}/stream?app_name=spitify`,
            source: 'Audius', id: track.id
        }));

        // 4. SoundCloud (Using the bypass response structure)
        const soundcloudTracks = (Array.isArray(soundcloudRes) ? soundcloudRes.slice(0, 5) : []).map(track => ({
            title: track.title, artist: track.user.username,
            image: track.artwork_url || 'https://placehold.co/60x45',
            audioUrl: track.stream_url, // scdl provides playable stream
            source: 'SoundCloud', id: track.id
        })).filter(track => track.audioUrl);

        // 5. Deezer
        const deezerTracks = (deezerRes.data || []).slice(0, 5).map(track => ({
            title: track.title, artist: track.artist.name,
            image: track.album.cover_medium || 'https://placehold.co/60x45',
            audioUrl: track.preview, source: 'Deezer (Preview)', id: track.id
        }));

        // 6. Tubidy
        const tubidyTracks = (Array.isArray(tubidyRes) ? tubidyRes : []).map(track => ({
            title: track.title, artist: track.artist,
            image: track.image, audioUrl: track.audioUrl,
            source: 'Tubidy', id: track.id
        }));

        // 7. JioSaavn
        const jiosaavnTracks = (jiosaavnRes.data?.results || []).slice(0, 5).map(track => ({
            title: track.name, artist: track.artists.primary[0]?.name || 'Unknown Artist',
            image: track.image?.[2]?.url || 'https://placehold.co/60x45',
            audioUrl: track.downloadUrl?.[4]?.url || track.downloadUrl?.[3]?.url,
            source: 'JioSaavn', id: track.id
        })).filter(track => track.audioUrl);

        // 8. Bandcamp
        const bandcampTracks = (Array.isArray(bandcampRes) ? bandcampRes : []).map(track => ({
            title: track.title, artist: track.artist,
            image: track.image, audioUrl: track.audioUrl,
            source: 'Bandcamp', id: track.id
        }));

        const archiveTracks = Array.isArray(archiveRes) ? archiveRes : [];
        const ccmixterTracks = Array.isArray(ccmixterRes) ? ccmixterRes : [];

        // Combine everything!
        const combinedResults = [
            ...jamendoTracks, ...youtubeTracks, ...audiusTracks, ...soundcloudTracks,
            ...deezerTracks, ...tubidyTracks, ...jiosaavnTracks, ...bandcampTracks,
            ...archiveTracks, ...ccmixterTracks
        ];
        
        displayResults(combinedResults);

    } catch (error) {
        console.error('Unified fetch error:', error);
        if (searchId === latestSearch) resultsContainer.textContent = 'Error querying media catalogs.';
    }
}

// Titles and artist names come from other people's servers, so they go in as text, never as HTML.
function displayResults(tracks) {
    resultsContainer.replaceChildren();
    if (tracks.length === 0) {
        resultsContainer.textContent = 'No matching tracks found.';
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
    audioPlayer.play().catch(err => console.log("Stream blocked:", err));
}
