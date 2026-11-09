const { execFile } = require('child_process');
const path = require('path');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
const TTL = 5 * 60 * 60 * 1000; // googlevideo URLs die around 6h
const SEARCH_TTL = 24 * 60 * 60 * 1000; // search results don't expire, keep them a day

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
    expires: Date.now() + TTL,
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

  const data = await once(`q:${key}`, clean);
  if (data.error) {
    console.warn('[youtube] Search error:', data.error);
    return [{ error: data.error }];
  }

  const results = (data.results || []).map((t) => ({ ...t, streamPath: `/stream/${t.id}` }));
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

function invalidate(id) {
  tracks.delete(id);
}

module.exports = fetchTrack;
module.exports.getTrack = getTrack;
module.exports.refetch = refetch;
module.exports.invalidate = invalidate;
