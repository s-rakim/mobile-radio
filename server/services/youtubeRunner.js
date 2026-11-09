const { execFile } = require('child_process');
const path = require('path');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');
const TTL = 5 * 60 * 60 * 1000; // googlevideo URLs die around 6h

const tracks = new Map();   // id -> { audioUrl, headers, expires }
const queries = new Map();  // query -> { result, expires }
const inflight = new Map(); // query -> Promise

function runPython(query) {
  return new Promise((resolve) => {
    execFile('python3', ['-u', SCRIPT_PATH, query], { timeout: 60000 }, (error, stdout, stderr) => {
      if (error) {
        console.warn('[youtube-docker] Python failed:', error.message, '\n', stderr);
        return resolve({ error: 'Extraction failed' });
      }
      const last = stdout.trim().split('\n').pop();
      try {
        resolve(JSON.parse(last));
      } catch {
        console.warn('[youtube-docker] Bad JSON:', stdout, '\n', stderr);
        resolve({ error: 'Extraction failed' });
      }
    });
  });
}

async function fetchTrack(query) {
  const key = query.trim().toLowerCase();

  const cached = queries.get(key);
  if (cached && cached.expires > Date.now()) return [cached.result];

  if (!inflight.has(key)) {
    inflight.set(key, runPython(query).finally(() => inflight.delete(key)));
  }
  const data = await inflight.get(key);

  if (data.error) {
    console.warn('[youtube-docker] Scraper error:', data.error);
    return [{ error: data.error }];
  }

  const expires = Date.now() + TTL;
  tracks.set(data.id, { audioUrl: data.audioUrl, headers: data.headers || {}, expires });

  const result = {
    title: data.title,
    artist: data.artist,
    image: data.image,
    audioUrl: `${process.env.PUBLIC_URL || ''}/stream/${data.id}`,
    source: data.source,
    id: data.id,
  };
  queries.set(key, { result, expires });
  return [result];
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

function invalidate(id) {
  tracks.delete(id);
  for (const [k, v] of queries) if (v.result.id === id) queries.delete(k);
}

module.exports = fetchTrack;
module.exports.getTrack = getTrack;
module.exports.invalidate = invalidate;