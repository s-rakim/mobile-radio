// Search routes for the open catalogues. Each request gives up after a few
// seconds and the route answers with an empty result instead of an error, so
// one slow source never holds up the rest.
//
// Three things keep searches quick:
//  - answers are cached for 10 minutes, so a repeat search costs nothing
//  - two identical searches at the same moment share one upstream request
//  - a source that keeps failing (ccMixter is often down) is skipped for a few
//    minutes instead of making every search wait for it to time out

const TIMEOUT = 3500;
const CACHE_MS = 10 * 60 * 1000;
const CACHE_MAX = 500;
const FAILS_BEFORE_PAUSE = 2;
const PAUSE_MS = 5 * 60 * 1000;

async function getJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT),
    headers: { 'User-Agent': 'spitify/1.0' },
  });
  if (!res.ok) throw new Error(`${res.status} from ${new URL(url).host}`);
  return res.json();
}

const enc = encodeURIComponent;
const first = (v) => (Array.isArray(v) ? v[0] : v);

async function audius(q) {
  return getJson(`https://api.audius.co/v1/tracks/search?query=${enc(q)}&app_name=spitify`);
}

async function deezer(q) {
  return getJson(`https://api.deezer.com/search?q=${enc(q)}&limit=10`);
}

async function jamendo(q) {
  const id = process.env.JAMENDO_CLIENT_ID;
  if (!id) return { results: [] };
  return getJson(
    `https://api.jamendo.com/v3.0/tracks/?client_id=${enc(id)}&format=json&limit=10&audioformat=mp32&search=${enc(q)}`
  );
}

// Best audio file in an Archive item: lossless FLAC first, then the highest MP3 bitrate.
// "VBR MP3" (the usual default) is often around 128 kbps.
function bestArchiveFile(files) {
  const rank = (f) => {
    const format = (f.format || '').toLowerCase();
    if (format.includes('flac') && /\.flac$/i.test(f.name)) return 0;
    if (format.includes('320')) return 1;
    if (format.includes('256')) return 2;
    if (format.includes('vbr mp3')) return 3;
    if (format.includes('mp3') && /\.mp3$/i.test(f.name)) return 4;
    return 99;
  };
  return files
    .filter((f) => rank(f) < 99)
    .sort((a, b) => rank(a) - rank(b))[0];
}

// Internet Archive search only returns items, not files, so each hit needs a
// second call to find an mp3 inside it.
async function archive(q) {
  const clean = q.replace(/[^\p{L}\p{N}\s'-]/gu, ' ').trim();
  if (!clean) return [];

  const query = `(${clean}) AND mediatype:audio AND (collection:netlabels OR collection:etree)`;
  const search = await getJson(
    `https://archive.org/advancedsearch.php?q=${enc(query)}&fl[]=identifier&fl[]=title&fl[]=creator&rows=5&output=json`
  );
  const docs = search?.response?.docs || [];

  const tracks = await Promise.allSettled(
    docs.map(async (doc) => {
      const meta = await getJson(`https://archive.org/metadata/${enc(doc.identifier)}/files`);
      const file = bestArchiveFile(meta.result || []);
      if (!file) return null;

      const filePath = file.name.split('/').map(enc).join('/');
      return {
        title: first(doc.title) || file.title || file.name,
        artist: first(doc.creator) || 'Internet Archive',
        image: `https://archive.org/services/img/${enc(doc.identifier)}`,
        audioUrl: `https://archive.org/download/${enc(doc.identifier)}/${filePath}`,
        source: 'Archive',
        id: doc.identifier,
      };
    })
  );

  return tracks.filter((t) => t.status === 'fulfilled' && t.value).map((t) => t.value);
}

async function ccmixter(q) {
  const items = await getJson(
    `https://ccmixter.org/api/query?f=json&search=${enc(q)}&search_type=all&sort=rank&limit=8`
  );
  if (!Array.isArray(items)) return [];

  return items
    .map((item) => {
      const file = (item.files || []).find(
        (f) => /\.mp3$/i.test(f.download_url || '') || /audio/i.test(f.file_format_info?.['mime_type'] || '')
      );
      if (!file) return null;
      return {
        title: item.upload_name,
        artist: item.user_real_name || item.user_name,
        image: item.user_avatar_url || null,
        audioUrl: file.download_url,
        source: 'ccMixter',
        id: item.upload_id,
      };
    })
    .filter(Boolean);
}

// Wraps a search function with the cache, request sharing and pause-on-failure.
function fast(name, search) {
  const cache = new Map(); // query -> { value, at }
  const inflight = new Map(); // query -> Promise
  let fails = 0;
  let pausedUntil = 0;

  return async (q) => {
    const key = q.trim().toLowerCase();
    const hit = cache.get(key);
    if (hit && Date.now() - hit.at < CACHE_MS) return hit.value;

    if (Date.now() < pausedUntil) throw new Error(`${name} paused after repeated failures`);
    if (inflight.has(key)) return inflight.get(key);

    const job = search(q)
      .then((value) => {
        fails = 0;
        cache.set(key, { value, at: Date.now() });
        if (cache.size > CACHE_MAX) cache.delete(cache.keys().next().value); // drop the oldest
        return value;
      })
      .catch((err) => {
        if (++fails >= FAILS_BEFORE_PAUSE) {
          pausedUntil = Date.now() + PAUSE_MS;
          fails = 0;
          console.warn(`[${name}] failing, skipping it for ${PAUSE_MS / 60000} minutes`);
        }
        throw err;
      })
      .finally(() => inflight.delete(key));

    inflight.set(key, job);
    return job;
  };
}

// name -> [search function, what to send back if it fails]
module.exports = {
  audius: [fast('audius', audius), { data: [] }],
  deezer: [fast('deezer', deezer), { data: [] }],
  jamendo: [fast('jamendo', jamendo), { results: [] }],
  archive: [fast('archive', archive), []],
  ccmixter: [fast('ccmixter', ccmixter), []],
};
