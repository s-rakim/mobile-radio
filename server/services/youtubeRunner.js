const { execFile } = require('child_process');
const path = require('path');
const searchYouTube = require('./ytSearch');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
const TTL = 5 * 60 * 60 * 1000; // googlevideo URLs die around 6h
const SEARCH_TTL = 24 * 60 * 60 * 1000; // search results don't expire, keep them a day
const SC_TTL = 20 * 60 * 1000; // SoundCloud's signed links don't last as long

const tracks = new Map();   // id -> { audioUrl, headers, expires }
const queries = new Map();  // query -> { results, expires }
const inflight = new Map(); // job key -> Promise

function runPython(arg) {
  return new Promise((resolve) => {
    execFile(PYTHON, ['-u', SCRIPT_PATH, arg], { timeout: 90000 }, (error, stdout, stderr) => {
      if (error) {
        console.warn('[youtube] Python failed:', error.message, '\n', stderr);
        return resolve({ error: 'Extraction failed' });
      }
      const last = stdout.trim().split('\n').pop();
      try {
        resolve(JSON.parse(last));
      } catch {
        console.warn('[youtube] Bad JSON:', stdout, '\n', stderr);
        resolve({ error: 'Extraction failed' });
      }
    });
  });
}

// Two requests for the same thing at once share one Python run.
function once(key, arg) {
  if (!inflight.has(key)) {
    inflight.set(key, runPython(arg).finally(() => inflight.delete(key)));
  }
  return inflight.get(key);
}

function remember(data) {
  tracks.set(data.id, {
    audioUrl: data.audioUrl,
    headers: data.headers || {},
    hls: Boolean(data.hls),
    expires: Date.now() + (data.id.startsWith('sc-') ? SC_TTL : TTL),
  });
}

// Search. Only looks up titles and ids, which is quick; the audio link is
// fetched when a song is played (see refetch). Returns stream paths like
// /stream/yt-abc123; index.js adds the host.
async function fetchTrack(query) {
  const clean = query.replace(/^id:/i, '').trim();
  const key = clean.toLowerCase();

  const cached = queries.get(key);
  if (cached && cached.expires > Date.now()) return cached.results;

  // YouTube's own search API first (fast); yt-dlp only if that comes back empty or fails.
  let found = [];
  try {
    found = await searchYouTube(clean);
  } catch (err) {
    console.warn('[youtube] Fast search failed, using yt-dlp:', err.message);
  }
  if (found.length === 0) {
    const data = await once(`q:${key}`, clean);
    if (data.error) {
      console.warn('[youtube] Search error:', data.error);
      return [{ error: data.error }];
    }
    found = data.results || [];
  }

  const results = found.map((t) => ({ ...t, streamPath: `/stream/${t.id}` }));
  queries.set(key, { results, expires: Date.now() + SEARCH_TTL });

  // Get the top result ready in the background, since it's the one people usually tap.
  if (results[0] && !getTrack(results[0].id)) refetch(results[0].id).catch(() => {});

  return results;
}

function getTrack(id) {
  const t = tracks.get(id);
  if (!t) return null;
  if (t.expires < Date.now()) {
    tracks.delete(id);
    return null;
  }
  return t;
}

// Fetches a fresh stream for a known video, so old links (liked songs,
// downloads, a server restart) keep working instead of expiring.
async function refetch(id) {
  if (id.startsWith('sc-')) {
    const token = id.slice(3);
    if (!/^[\w-]{4,400}$/.test(token)) return null;
    const data = await once(`scid:${token}`, `scid:${token}`);
    if (data.error || data.id !== id) {
      console.warn('[soundcloud] Refetch failed for', id, data.error || '');
      return null;
    }
    remember(data);
    return getTrack(id);
  }

  const videoId = id.replace(/^yt-/, '');
  if (!/^[\w-]{11}$/.test(videoId)) return null;

  const data = await once(`id:${videoId}`, `id:${videoId}`);
  if (data.error || data.id !== id) {
    console.warn('[youtube] Refetch failed for', id, data.error || '');
    return null;
  }
  remember(data);
  return getTrack(id);
}

// SoundCloud search. Full extraction (SoundCloud has no slow challenge like YouTube), so every
// result comes back ready to play straight away.
async function searchSoundCloud(query) {
  const clean = query.trim();
  const key = `sc:${clean.toLowerCase()}`;

  const cached = queries.get(key);
  if (cached && cached.expires > Date.now()) return cached.results;

  const data = await once(key, `sc:${clean}`);
  if (data.error) {
    console.warn('[soundcloud] Search error:', data.error);
    return [{ error: data.error }];
  }

  const found = data.results || [];
  found.forEach(remember);
  const results = found.map(({ audioUrl, headers, ...t }) => ({ ...t, streamPath: `/stream/${t.id}` }));
  queries.set(key, { results, expires: Date.now() + SEARCH_TTL });
  return results;
}

function invalidate(id) {
  tracks.delete(id);
}

module.exports = fetchTrack;
module.exports.getTrack = getTrack;
module.exports.refetch = refetch;
module.exports.invalidate = invalidate;
module.exports.searchSoundCloud = searchSoundCloud;
