const path = require('path');
const express = require('express');
const cors = require('cors');
const { request } = require('undici');
const catalogs = require('./services/catalogs');
const accounts = require('./services/accounts');
const youtubeRunner = require('./services/youtubeRunner');
const { getTrack, refetch, invalidate } = youtubeRunner;

const app = express();
app.set('trust proxy', true); // so req.protocol is https behind Cloudflare
app.use(cors());
app.use(express.json({ limit: '5mb' }));
app.use(express.static(path.join(__dirname, 'public')));

function readQuery(req, res) {
  const q = (req.query.q || '').toString().trim().slice(0, 200);
  if (!q) res.status(400).json({ error: 'Missing ?q=' });
  return q;
}

// Stream links point back at whatever URL the request came in on, so they
// keep working when a quick tunnel hands out a new address.
function baseUrl(req) {
  return process.env.PUBLIC_URL || `${req.protocol}://${req.get('host')}`;
}

app.get('/health', (req, res) => res.send('ok'));

// ---- Accounts: sign in from the app and keep your library on this server ----

function bearer(req) {
  const h = req.get('authorization') || '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}

function signedIn(req, res, next) {
  const user = accounts.userForToken(bearer(req));
  if (!user) return res.status(401).json({ error: 'Sign in again' });
  req.user = user;
  next();
}

function authRoute(action) {
  return (req, res) => {
    if (accounts.rateLimited(req.ip)) return res.status(429).json({ error: 'Too many tries, wait a minute' });
    try {
      res.json(action(req.body?.username, req.body?.password));
    } catch (err) {
      res.status(err.status || 500).json({ error: err.status ? err.message : 'Something went wrong' });
    }
  };
}

app.post('/api/auth/register', authRoute(accounts.register));
app.post('/api/auth/login', authRoute(accounts.login));
app.post('/api/auth/logout', (req, res) => {
  accounts.logout(bearer(req));
  res.json({ ok: true });
});

app.get('/api/library', signedIn, (req, res) => {
  res.json({ username: req.user, ...accounts.getLibrary(req.user) });
});

app.put('/api/library', signedIn, (req, res) => {
  if (!req.body || typeof req.body.library !== 'object') return res.status(400).json({ error: 'Missing library' });
  res.json({ updatedAt: accounts.putLibrary(req.user, req.body.library, req.body.updatedAt) });
});

for (const [name, [search, empty]] of Object.entries(catalogs)) {
  app.get(`/api/${name}`, async (req, res) => {
    const q = readQuery(req, res);
    if (!q) return;
    try {
      res.json(await search(q));
    } catch (err) {
      console.warn(`[${name}]`, err.message);
      res.json(empty);
    }
  });
}

app.get('/api/youtube', async (req, res) => {
  const q = readQuery(req, res);
  if (!q) return;

  const results = await youtubeRunner(q);
  const base = baseUrl(req);
  res.json(
    results.map(({ streamPath, ...r }) => (streamPath ? { ...r, audioUrl: `${base}${streamPath}` } : r))
  );
});

// YouTube stream URLs only work from the IP that fetched them (this machine),
// so the audio is piped through here instead of handed to the client.
// An unknown or expired id gets fetched again, so saved links don't go stale.
app.get('/stream/:id', async (req, res) => {
  const id = req.params.id;
  let track = getTrack(id) || (await refetch(id));
  if (!track) return res.status(404).json({ error: 'Track not found' });

  let upstream;
  for (let attempt = 0; attempt < 2; attempt++) {
    const headers = { ...track.headers };
    delete headers['Accept-Encoding']; // keep the body raw so ranges line up
    if (req.headers.range) headers.range = req.headers.range;

    try {
      upstream = await request(track.audioUrl, { headers });
    } catch (err) {
      console.warn('[stream]', err.message);
      return res.status(502).json({ error: 'Could not reach YouTube' });
    }

    if (upstream.statusCode !== 403 && upstream.statusCode !== 410) break;

    // link went stale: drop it and try once more with a fresh one
    upstream.body.destroy();
    invalidate(id);
    track = attempt === 0 ? await refetch(id) : null;
    if (!track) return res.status(410).json({ error: 'Stream URL expired, search again' });
  }

  res.status(upstream.statusCode);
  for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
    if (upstream.headers[h]) res.setHeader(h, upstream.headers[h]);
  }

  // stop downloading if the listener skips or leaves
  res.on('close', () => upstream.body.destroy());
  upstream.body.on('error', () => res.end());
  upstream.body.pipe(res);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Spitify running on http://localhost:${PORT}`);

  // one search at boot so yt-dlp caches the player JS before real requests
  youtubeRunner('sauti sol melanin')
    .then((r) => console.log('[warmup]', r[0]?.error || 'ok'))
    .catch(() => {});
});
