const { execFile, spawn } = require('child_process');
const readline = require('readline');
const path = require('path');
const searchYouTube = require('./ytSearch');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
const TTL = 5 * 60 * 60 * 1000; // googlevideo URLs die around 6h
const SEARCH_TTL = 24 * 60 * 60 * 1000; // search results don't expire, keep them a day
const JOB_TIMEOUT = 90000;

const tracks = new Map();   // id -> { audioUrl, headers, expires }
const queries = new Map();  // query -> { results, expires }
const inflight = new Map(); // job key -> Promise

// ---- One Python worker that stays running, so yt-dlp isn't loaded again for every song ----

let worker = null;
let workerReady = false;
let restartDelay = 2000;
let nextJob = 1;
const jobs = new Map(); // job id -> { resolve, timer }

function startWorker() {
  let child;
  try {
    child = spawn(PYTHON, ['-u', SCRIPT_PATH, '--serve'], { stdio: ['pipe', 'pipe', 'pipe'] });
  } catch (err) {
    console.warn('[youtube] Could not start the worker:', err.message);
    return;
  }
  worker = child;
  workerReady = false;

  readline.createInterface({ input: child.stdout }).on('line', (line) => {
    let msg;
    try {
      msg = JSON.parse(line);
    } catch {
      return; // stray output from a library, not a reply
    }
    if (msg.ready) {
      workerReady = true;
      restartDelay = 2000;
      console.log('[youtube] Worker ready');
      return;
    }
    const job = jobs.get(msg.id);
    if (!job) return;
    jobs.delete(msg.id);
    clearTimeout(job.timer);
    job.resolve(msg.result || { error: 'Extraction failed' });
  });

  child.stderr.on('data', (chunk) => {
    const text = chunk.toString().trim();
    if (text) console.warn('[youtube-py]', text.slice(0, 600));
  });

  const stopped = (why) => {
    if (worker !== child) return;
    console.warn('[youtube] Worker stopped:', why);
    worker = null;
    workerReady = false;
    for (const job of jobs.values()) {
      clearTimeout(job.timer);
      job.resolve({ error: 'Extraction failed' });
    }
    jobs.clear();
    setTimeout(startWorker, restartDelay);
    restartDelay = Math.min(restartDelay * 2, 60000);
  };
  child.on('exit', (code) => stopped(`exit ${code}`));
  child.on('error', (err) => stopped(err.message));
}

function askWorker(arg) {
  return new Promise((resolve) => {
    const id = nextJob++;
    const timer = setTimeout(() => {
      jobs.delete(id);
      resolve({ error: 'Timed out' });
    }, JOB_TIMEOUT);
    jobs.set(id, { resolve, timer });
    worker.stdin.write(JSON.stringify({ id, arg }) + '\n');
  });
}

// Used only while the worker is starting up or restarting.
function runOnce(arg) {
  return new Promise((resolve) => {
    execFile(PYTHON, ['-u', SCRIPT_PATH, arg], { timeout: JOB_TIMEOUT }, (error, stdout, stderr) => {
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

function runPython(arg) {
  return worker && workerReady ? askWorker(arg) : runOnce(arg);
}

startWorker();

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
  if (cached && cached.expires > Date.now()) {
    prepare(cached.results);
    return cached.results;
  }

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

  prepare(results);
  return results;
}

// Get stream links ready before anyone taps, so songs start straight away.
// The top result goes first. The next two wait a moment, because the app searches as you
// type and only the search you stop on is worth preparing.
let latestSearch = 0;
function prepare(results) {
  const stamp = ++latestSearch;
  const warm = (t) => {
    if (t && !getTrack(t.id)) refetch(t.id).catch(() => {});
  };
  warm(results[0]);
  setTimeout(() => {
    if (stamp === latestSearch) results.slice(1, 3).forEach(warm);
  }, 1500);
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
