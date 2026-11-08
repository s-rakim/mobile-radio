const searchBox = document.getElementById('search-box');
const resultsContainer = document.getElementById('results-container');
const audioPlayer = document.getElementById('audio-player');
let debounceTimer;

// 1. YOUR LIVE RENDER BACKEND URL
const SERVER_IP = 'https://spitify-backend.onrender.com';

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
    resultsContainer.innerHTML = '<p>Searching across catalogs...</p>';

    try {
        // Fetch from your live Render backend
        const [jamendoRes, youtubeRes] = await Promise.all([
            fetch(`${SERVER_IP}/api/jamendo?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ results: [] })),
            fetch(`${SERVER_IP}/api/youtube?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => [])
        ]);

        // Format Jamendo Data
        const jamendoTracks = (jamendoRes.results || []).map(track => ({
            title: track.name,
            artist: track.artist_name,
            image: track.album_image || 'https://placehold.co/60x45',
            audioUrl: track.audio,
            source: 'Jamendo',
            id: track.id
        }));

        // Format YouTube/Invidious Data
        const youtubeTracks = (Array.isArray(youtubeRes) ? youtubeRes.slice(0, 5) : []).map(video => ({
            title: video.title,
            artist: video.author,
            image: video.videoThumbnails?.find(t => t.quality === 'medium')?.url || 'https://placehold.co/60x45',
            // 2. UPDATED WORKING INVIDIOUS AUDIO LINK
            audioUrl: `https://inv.nadeko.net/latest_version?id=${video.videoId}&itag=140`, 
            source: 'YouTube',
            id: video.videoId
        }));

        const combinedResults = [...jamendoTracks, ...youtubeTracks];
        displayResults(combinedResults);

    } catch (error) {
        console.error('Unified fetch error:', error);
        resultsContainer.innerHTML = '<p style="color: red;">Error querying media catalogs.</p>';
    }
}

function displayResults(tracks) {
    resultsContainer.innerHTML = '';

    if (tracks.length === 0) {
        resultsContainer.innerHTML = '<p>No matching tracks found.</p>';
        return;
    }

    tracks.forEach(track => {
        const trackElement = document.createElement('div');
        trackElement.className = `track-item ${track.source.toLowerCase()}`;

        trackElement.innerHTML = `
            <img src="${track.image}" alt="Cover">
            <div class="track-info">
                <strong>${track.title}</strong> <span class="source-tag">${track.source}</span><br>
                <small>${track.artist}</small>
            </div>
            <button class="play-btn" data-audio="${track.audioUrl}">Play</button>
        `;

        resultsContainer.appendChild(trackElement);
    });

    document.querySelectorAll('.play-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            const streamUrl = e.target.getAttribute('data-audio');
            playTrack(streamUrl);
        });
    });
}

function playTrack(url) {
    audioPlayer.src = url;
    audioPlayer.play().catch(err => console.log("Stream play request blocked or context shifted:", err));
}
