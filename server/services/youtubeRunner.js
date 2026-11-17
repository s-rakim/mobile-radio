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

// ---- Only a few yt-dlp runs at once ----
// Each run is a whole Python process, and YouTube's challenge solving keeps a CPU core busy.
// Without a limit, a phone checking a 50-song playlist starts 50 at once, the machine hits 100%,
// and every run (including the song you just tapped) times out. So runs wait their turn:
//   play    - a song someone is waiting to hear: always first
//   search  - a search that YouTube's quick search couldn't answer
//   prepare - getting a song ready before anyone asks: dropped when the queue is busy
const MAX_RUNS = Math.max(1, parseInt(process.env.YT_CONCURRENCY || '2', 10));
const PRIORITY = { play: 0, search: 1, prepare: 2 };
const MAX_WAITING_PREPARES = 3;
const waiting = [];
let running = 0;

function schedule(arg, priority) {
  return new Promise((resolve) => {
    const level = PRIORITY[priority] ?? PRIORITY.search;
    if (level === PRIORITY.prepare) {
      const prepares = waiting.filter((j) => j.level === PRIORITY.prepare).length;
      if (prepares >= MAX_WAITING_PREPARES) return resolve({ error: 'Busy, skipped' });
    }
    waiting.push({ arg, level, resolve, at: Date.now() });
    // lowest level first, then oldest first
    waiting.sort((a, b) => a.level - b.level || a.at - b.at);
    pump();
  });
}

function pump() {
  while (running < MAX_RUNS && waiting.length) {
    const job = waiting.shift();
    running++;
    runPython(job.arg)
      .then(job.resolve)
      .finally(() => {
        running--;
        pump();
      });
  }
}

function runPython(arg) {
  return new Promise((resolve) => {
    execFile(PYTHON, ['-u', SCRIPT_PATH, arg], { timeout: 90000 }, (error, stdout, stderr) => {
      if (error) {
        const why = error.killed ? 'took longer than 90 seconds' : (stderr || '').trim().split('\n').pop() || error.message;
        console.warn(`[youtube] ${arg.split(':')[0] || 'search'} failed: ${why}`);
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

// Two requests for the same thing at once share one Python run. If a song someone is waiting
// for is already queued as a "prepare", it's moved up to the front.
function once(key, arg, priority = 'search') {
  const queued = waiting.find((j) => j.arg === arg);
  if (queued && PRIORITY[priority] < queued.level) {
    queued.level = PRIORITY[priority];
    waiting.sort((a, b) => a.level - b.level || a.at - b.at);
  }
  if (!inflight.has(key)) {
    inflight.set(key, schedule(arg, priority).finally(() => inflight.delete(key)));
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
// `prepare: false` skips getting the top song ready, for lookups that only want to know
// whether a song exists (the phone checking a playlist against the server).
async function fetchTrack(query, { prepare = true } = {}) {
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
  if (prepare && results[0] && !getTrack(results[0].id)) refetch(results[0].id, 'prepare').catch(() => {});

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
async function refetch(id, priority = 'play') {
  if (id.startsWith('sc-')) {
    const token = id.slice(3);
    if (!/^[\w-]{4,400}$/.test(token)) return null;
    const data = await once(`scid:${token}`, `scid:${token}`, priority);
    if (data.error || data.id !== id) {
      if (data.error !== 'Busy, skipped') console.warn('[soundcloud] Refetch failed for', id, data.error || '');
      return null;
    }
    remember(data);
    return getTrack(id);
  }

  const videoId = id.replace(/^yt-/, '');
  if (!/^[\w-]{11}$/.test(videoId)) return null;

  const data = await once(`id:${videoId}`, `id:${videoId}`, priority);
  if (data.error || data.id !== id) {
    if (data.error !== 'Busy, skipped') console.warn('[youtube] Refetch failed for', id, data.error || '');
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
