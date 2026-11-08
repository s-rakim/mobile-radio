'use strict';

const CONFIG = window.SPITIFY_CONFIG || {};
const JAMENDO_CLIENT_ID = (CONFIG.jamendoClientId || '').trim();
const JAMENDO_API = 'https://api.jamendo.com/v3.0';
const AUDIUS_API = 'https://api.audius.co/v1';
const AUDIUS_APP_NAME = 'spitify';
const RESULTS_PER_SOURCE = 10;
const SEARCH_DELAY_MS = 400;

const FALLBACK_ART = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">' +
  '<rect width="64" height="64" fill="#2a2a2a"/>' +
  '<path d="M27 20v18.3a6 6 0 1 0 4 5.7V26h10v-6z" fill="#8c8c8c"/></svg>'
);

const ICON_PLAY = '<svg class="i-play" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
const ICON_PAUSE = '<svg class="i-pause" viewBox="0 0 24 24"><path d="M7 5h4v14H7zM13 5h4v14h-4z"/></svg>';

const $ = (id) => document.getElementById(id);
const searchBox = $('search-box');
const statusEl = $('status');
const resultsEl = $('results-container');
const audio = $('audio-player');
const player = $('player');
const seekBar = $('seek-bar');

const state = {
  query: '',
  source: 'all',
  tracks: [],         // everything the last search returned
  visible: [],        // tracks after the source filter
  current: null,      // the track loaded in the player
  controller: null,   // aborts a search that's been overtaken by newer typing
  seeking: false,
};

// ---------- Sources ----------

async function getJson(url, signal) {
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error(`${res.status} from ${new URL(url).host}`);
  return res.json();
}

async function searchJamendo(query, signal) {
  const params = new URLSearchParams({
    client_id: JAMENDO_CLIENT_ID,
    format: 'json',
    limit: RESULTS_PER_SOURCE,
    search: query,
    audioformat: 'mp32',
    imagesize: 200,
  });
  const data = await getJson(`${JAMENDO_API}/tracks/?${params}`, signal);
  if (data.headers && data.headers.status !== 'success') {
    throw new Error(data.headers.error_message || 'Jamendo search failed');
  }
  return (data.results || [])
    .filter((t) => t.audio)
    .map((t) => ({
      key: `jamendo:${t.id}`,
      title: t.name,
      artist: t.artist_name,
      image: t.album_image || t.image,
      audioUrl: t.audio,
      duration: Number(t.duration) || 0,
      source: 'Jamendo',
    }));
}

async function searchAudius(query, signal) {
  const params = new URLSearchParams({
    query,
    limit: RESULTS_PER_SOURCE,
    app_name: AUDIUS_APP_NAME,
  });
  const data = await getJson(`${AUDIUS_API}/tracks/search?${params}`, signal);
  return (data.data || [])
    .filter((t) => t.is_streamable !== false)
    .map((t) => ({
      key: `audius:${t.id}`,
      title: t.title,
      artist: t.user ? t.user.name : '',
      image: t.artwork && (t.artwork['150x150'] || t.artwork['480x480']),
      audioUrl: `${AUDIUS_API}/tracks/${encodeURIComponent(t.id)}/stream?app_name=${AUDIUS_APP_NAME}`,
      duration: Number(t.duration) || 0,
      source: 'Audius',
    }));
}

// ---------- Search ----------

let debounceTimer;

searchBox.addEventListener('input', () => {
  clearTimeout(debounceTimer);
  const query = searchBox.value.trim();
  if (!query) {
    cancelSearch();
    state.query = '';
    state.tracks = [];
    render();
    setStatus('Search for a song, an artist or a mood.');
    return;
  }
  debounceTimer = setTimeout(() => runSearch(query), SEARCH_DELAY_MS);
});

searchBox.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    clearTimeout(debounceTimer);
    const query = searchBox.value.trim();
    if (query) runSearch(query);
  }
});

function cancelSearch() {
  if (state.controller) state.controller.abort();
  state.controller = null;
}

async function runSearch(query) {
  cancelSearch();
  const controller = new AbortController();
  state.controller = controller;
  state.query = query;

  showLoading();

  const outcomes = await Promise.allSettled([
    JAMENDO_CLIENT_ID ? searchJamendo(query, controller.signal) : Promise.resolve([]),
    searchAudius(query, controller.signal),
  ]);

  // A newer search started while this one was in flight; drop these results.
  if (controller.signal.aborted) return;
  state.controller = null;

  const failures = outcomes.filter((o) => o.status === 'rejected');
  failures.forEach((f) => console.warn('Search source failed:', f.reason));

  if (failures.length === outcomes.length) {
    state.tracks = [];
    render();
    setStatus("Couldn't reach the music catalogs. Check your connection.", { error: true, retry: true });
    return;
  }

  const [jamendo, audius] = outcomes.map((o) => (o.status === 'fulfilled' ? o.value : []));
  state.tracks = interleave(jamendo, audius);
  render();

  if (failures.length) {
    const down = outcomes[0].status === 'rejected' ? 'Jamendo' : 'Audius';
    setStatus(`${down} isn't responding, so these results are from one source only.`);
  }
}

// Alternate the two catalogs so neither one buries the other.
function interleave(a, b) {
  const out = [];
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if (a[i]) out.push(a[i]);
    if (b[i]) out.push(b[i]);
  }
  return out;
}

// ---------- Rendering ----------

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function formatTime(sec) {
  if (!Number.isFinite(sec) || sec <= 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

function setStatus(text, { error = false, retry = false } = {}) {
  statusEl.hidden = !text;
  statusEl.classList.toggle('is-error', error);
  statusEl.textContent = text || '';
  if (retry) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = 'Try again';
    btn.addEventListener('click', () => state.query && runSearch(state.query));
    statusEl.append(btn);
  }
}

function showLoading() {
  setStatus('');
  resultsEl.innerHTML = '<li class="skeleton"></li>'.repeat(6);
  resultsEl.setAttribute('aria-busy', 'true');
}

function render() {
  resultsEl.removeAttribute('aria-busy');
  state.visible = state.source === 'all'
    ? state.tracks
    : state.tracks.filter((t) => t.source.toLowerCase() === state.source);

  if (state.query && state.visible.length === 0 && state.tracks.length === 0) {
    setStatus(`No tracks found for "${state.query}".`);
  } else if (state.query && state.visible.length === 0) {
    setStatus(`Nothing on ${state.source === 'jamendo' ? 'Jamendo' : 'Audius'} for "${state.query}".`);
  } else {
    setStatus('');
  }

  resultsEl.innerHTML = state.visible.map((t, i) => `
    <li class="track" data-index="${i}">
      <img src="${escapeHtml(t.image || FALLBACK_ART)}" alt="" loading="lazy" width="52" height="52">
      <div class="track-info">
        <span class="track-title">${escapeHtml(t.title)}</span>
        <span class="track-artist"><span class="tag">${t.source}</span>${escapeHtml(t.artist)}</span>
      </div>
      <span class="duration">${formatTime(t.duration)}</span>
      <button class="play-btn" type="button" aria-label="Play ${escapeHtml(t.title)}">${ICON_PLAY}${ICON_PAUSE}</button>
    </li>`).join('');

  markPlaying();
}

// Broken artwork URLs fall back to the placeholder. Image errors don't
// bubble, so listen in the capture phase.
resultsEl.addEventListener('error', (e) => {
  if (e.target.tagName === 'IMG' && e.target.src !== FALLBACK_ART) e.target.src = FALLBACK_ART;
}, true);

$('player-art').addEventListener('error', (e) => {
  if (e.target.src !== FALLBACK_ART) e.target.src = FALLBACK_ART;
});

function markPlaying() {
  resultsEl.querySelectorAll('.track').forEach((row) => {
    const track = state.visible[row.dataset.index];
    const isCurrent = !!state.current && track.key === state.current.key;
    row.classList.toggle('is-playing', isCurrent);
    row.classList.toggle('is-active', isCurrent && !audio.paused);
    const btn = row.querySelector('.play-btn');
    btn.setAttribute('aria-label', `${isCurrent && !audio.paused ? 'Pause' : 'Play'} ${track.title}`);
  });
}

// One listener for every row, so re-rendering never leaks handlers.
resultsEl.addEventListener('click', (e) => {
  const row = e.target.closest('.track');
  if (!row) return;
  const track = state.visible[row.dataset.index];
  if (state.current && track.key === state.current.key) {
    togglePlayback();
  } else {
    playTrack(track);
  }
});

document.querySelector('.filters').addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (!chip) return;
  state.source = chip.dataset.source;
  document.querySelectorAll('.chip').forEach((c) => {
    const on = c === chip;
    c.classList.toggle('is-active', on);
    c.setAttribute('aria-selected', String(on));
  });
  render();
});

// ---------- Playback ----------

function playTrack(track) {
  state.current = track;
  audio.src = track.audioUrl;
  player.hidden = false;
  player.classList.add('is-loading');
  $('player-art').src = track.image || FALLBACK_ART;
  $('player-title').textContent = track.title;
  $('player-artist').textContent = `${track.artist} · ${track.source}`;
  $('time-total').textContent = formatTime(track.duration);
  seekBar.value = 0;
  document.title = `${track.title} · ${track.artist}`;

  audio.play().catch((err) => {
    if (err.name !== 'AbortError') {
      console.warn('Playback failed:', err);
      player.classList.remove('is-loading');
    }
  });
  updateMediaSession(track);
  markPlaying();
}

function togglePlayback() {
  if (!state.current) return;
  if (audio.paused) audio.play().catch(() => {});
  else audio.pause();
}

function step(offset) {
  if (!state.current || state.visible.length === 0) return;
  const i = state.visible.findIndex((t) => t.key === state.current.key);
  const next = state.visible[i + offset];
  if (next) playTrack(next);
}

$('toggle-btn').addEventListener('click', togglePlayback);
$('prev-btn').addEventListener('click', () => {
  // Like most players: restart the song first, go back only near the start.
  if (audio.currentTime > 3) audio.currentTime = 0;
  else step(-1);
});
$('next-btn').addEventListener('click', () => step(1));

audio.addEventListener('playing', () => {
  player.classList.remove('is-loading');
  player.classList.add('is-playing');
  $('toggle-btn').setAttribute('aria-label', 'Pause');
  markPlaying();
});
audio.addEventListener('pause', () => {
  player.classList.remove('is-playing');
  $('toggle-btn').setAttribute('aria-label', 'Play');
  markPlaying();
});
audio.addEventListener('waiting', () => player.classList.add('is-loading'));
audio.addEventListener('ended', () => step(1));
audio.addEventListener('error', () => {
  if (!state.current) return;
  player.classList.remove('is-loading', 'is-playing');
  $('player-artist').textContent = "This track won't play right now. Try another one.";
  markPlaying();
});

audio.addEventListener('loadedmetadata', () => {
  if (Number.isFinite(audio.duration)) $('time-total').textContent = formatTime(audio.duration);
});
audio.addEventListener('timeupdate', () => {
  if (state.seeking || !audio.duration) return;
  seekBar.value = (audio.currentTime / audio.duration) * 100;
  $('time-current').textContent = formatTime(audio.currentTime);
});

seekBar.addEventListener('input', () => {
  state.seeking = true;
  if (audio.duration) $('time-current').textContent = formatTime((seekBar.value / 100) * audio.duration);
});
seekBar.addEventListener('change', () => {
  if (audio.duration) audio.currentTime = (seekBar.value / 100) * audio.duration;
  state.seeking = false;
});

// Space toggles playback unless you're typing in the search box.
document.addEventListener('keydown', (e) => {
  if (e.code === 'Space' && e.target === document.body) {
    e.preventDefault();
    togglePlayback();
  }
});

// Lock screen and media key controls where the browser supports them.
function updateMediaSession(track) {
  if (!('mediaSession' in navigator)) return;
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist,
    album: track.source,
    artwork: track.image ? [{ src: track.image, sizes: '200x200' }] : [],
  });
}

if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('play', togglePlayback);
  navigator.mediaSession.setActionHandler('pause', togglePlayback);
  navigator.mediaSession.setActionHandler('previoustrack', () => step(-1));
  navigator.mediaSession.setActionHandler('nexttrack', () => step(1));
}

// Without a Jamendo key there's only one catalog, so the source chips are noise.
if (!JAMENDO_CLIENT_ID) document.querySelector('.filters').hidden = true;

searchBox.focus();
