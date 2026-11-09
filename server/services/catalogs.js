// Search routes for the open catalogues. Each one gives up after 10 seconds
// and answers with an empty result instead of an error, so one slow source
// never breaks the page.

const TIMEOUT = 10000;

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
    `https://archive.org/advancedsearch.php?q=${enc(query)}&fl[]=identifier&fl[]=title&fl[]=creator&rows=6&output=json`
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

// name -> [search function, what to send back if it fails]
module.exports = {
  audius: [audius, { data: [] }],
  deezer: [deezer, { data: [] }],
  jamendo: [jamendo, { results: [] }],
  archive: [archive, []],
  ccmixter: [ccmixter, []],
};
