const searchBox = document.getElementById('search-box');
const resultsContainer = document.getElementById('results-container');
const audioPlayer = document.getElementById('audio-player');
let debounceTimer;

// Your live Render backend URL
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
        // Fetch from ALL sources concurrently
        const [
            jamendoRes, 
            youtubeRes, 
            audiusRes, 
            soundcloudRes, 
            deezerRes,
            tubidyRes,
            jiosaavnRes,
            bandcampRes
        ] = await Promise.all([
            fetch(`${SERVER_IP}/api/jamendo?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ results: [] })),
            fetch(`${SERVER_IP}/api/youtube?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/audius?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: [] })),
            fetch(`${SERVER_IP}/api/soundcloud?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/deezer?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: [] })),
            fetch(`${SERVER_IP}/api/tubidy?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => []),
            fetch(`${SERVER_IP}/api/jiosaavn?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => ({ data: { results: [] } })),
            fetch(`${SERVER_IP}/api/bandcamp?q=${encodeURIComponent(searchQuery)}`).then(res => res.json()).catch(() => [])
        ]);

        // --- Format results from each source ---

        // 1. Jamendo
        const jamendoTracks = (jamendoRes.results || []).map(track => ({
            title: track.name,
            artist: track.artist_name,
            image: track.album_image || 'https://placehold.co/60x45',
            audioUrl: track.audio,
            source: 'Jamendo',
            id: track.id
        }));

        // 2. YouTube (Invidious)
        const youtubeTracks = (Array.isArray(youtubeRes) ? youtubeRes.slice(0, 5) : []).map(video => ({
            title: video.title,
            artist: video.author,
            image: video.videoThumbnails?.find(t => t.quality === 'medium')?.url || 'https://placehold.co/60x45',
            audioUrl: `https://inv.nadeko.net/latest_version?id=${video.videoId}&itag=140`, 
            source: 'YouTube',
            id: video.videoId
        }));

        // 3. Audius
        const audiusTracks = (audiusRes.data || []).slice(0, 5).map(track => ({
            title: track.title,
            artist: track.user.name,
            image: track.artwork?.['150x150'] || 'https://placehold.co/60x45',
            audioUrl: `https://discoveryprovider.audius.co/v1/tracks/${track.id}/stream?app_name=SpitifyClone`,
            source: 'Audius',
            id: track.id
        }));

        // 4. SoundCloud
        const soundcloudTracks = (Array.isArray(soundcloudRes) ? soundcloudRes.slice(0, 5) : []).map(track => ({
            title: track.title,
            artist: track.user.username,
            image: track.artwork_url || 'https://placehold.co/60x45',
            audioUrl: track.stream_url ? `${track.stream_url}?client_id=${process.env.SOUNDCLOUD_CLIENT_ID}` : null,
            source: 'SoundCloud',
            id: track.id
        })).filter(track => track.audioUrl);

        // 5. Deezer
        const deezerTracks = (deezerRes.data || []).slice(0, 5).map(track => ({
            title: track.title,
            artist: track.artist.name,
            image: track.album.cover_medium || 'https://placehold.co/60x45',
            audioUrl: track.preview,
            source: 'Deezer (Preview)',
            id: track.id
        }));

        // 6. Tubidy
        const tubidyTracks = (Array.isArray(tubidyRes) ? tubidyRes : []).map(track => ({
            title: track.title,
            artist: track.artist,
            image: track.image,
            audioUrl: track.audioUrl,
            source: 'Tubidy',
            id: track.id
        }));

        // 7. JioSaavn
        const jiosaavnTracks = (jiosaavnRes.data?.results || []).slice(0, 5).map(track => ({
            title: track.name,
            artist: track.artists.primary[0]?.name || 'Unknown Artist',
            image: track.image?.[2]?.url || 'https://placehold.co/60x45',
            // JioSaavn provides download URLs at various qualities; we use the highest
            audioUrl: track.downloadUrl?.[4]?.url || track.downloadUrl?.[3]?.url,
            source: 'JioSaavn',
            id: track.id
        })).filter(track => track.audioUrl);

        // 8. Bandcamp
        const bandcampTracks = (Array.isArray(bandcampRes) ? bandcampRes : []).map(track => ({
            title: track.title,
            artist: track.artist,
            image: track.image,
            audioUrl: track.audioUrl,
            source: 'Bandcamp',
            id: track.id
        }));

        // Combine all results
        const combinedResults = [
            ...jamendoTracks, 
            ...youtubeTracks, 
            ...audiusTracks, 
            ...soundcloudTracks, 
            ...deezerTracks,
            ...tubidyTracks,
            ...jiosaavnTracks,
            ...bandcampTracks
        ];
        
        displayResults(combinedResults);

    } catch (error) {
        console.error('Unified fetch error:', error);
        resultsContainer.innerHTML = '<p style="color: red;">Error querying media catalogs.</p>';
    }
}

// ... displayResults and playTrack functions remain the same ...
function displayResults(tracks) {
    resultsContainer.innerHTML = '';
    if (tracks.length === 0) {
        resultsContainer.innerHTML = '<p>No matching tracks found.</p>';
        return;
    }
    tracks.forEach(track => {
        const trackElement = document.createElement('div');
        trackElement.className = `track-item ${track.source.toLowerCase().replace(/[^a-z0-9]/g, '')}`;
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
    if (!url) {
        console.error("No audio URL provided for this track.");
        return;
    }
    audioPlayer.src = url;
    audioPlayer.play().catch(err => console.log("Stream play request blocked or context shifted:", err));
}
