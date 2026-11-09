const express = require('express');
const cors = require('cors');
const { ProxyAgent, request } = require('undici');
const youtubeRunner = require('./services/youtubeRunner');
const { getTrack, invalidate } = youtubeRunner;

const app = express();
app.use(cors());

// gost HTTP port -> Tailscale -> phone
const agent = new ProxyAgent(process.env.STREAM_PROXY || 'http://127.0.0.1:8118');

app.get('/health', (req, res) => res.send('ok'));

app.get('/api/youtube', async (req, res) => {
  const query = (req.query.q || '').toString().trim().slice(0, 200);
  if (!query) return res.json([]);

  const results = await youtubeRunner(query);
  res.json(results);
});

app.get('/stream/:id', async (req, res) => {
  const track = getTrack(req.params.id);
  if (!track) return res.status(404).json({ error: 'Track expired, search again' });

  const headers = { ...track.headers };
  delete headers['Accept-Encoding']; // keep the body raw so ranges line up
  if (req.headers.range) headers.range = req.headers.range;

  let upstream;
  try {
    upstream = await request(track.audioUrl, { dispatcher: agent, headers });
  } catch (err) {
    console.warn('[stream] proxy error:', err.message);
    return res.status(502).json({ error: 'Phone proxy unreachable' });
  }

  if (upstream.statusCode === 403 || upstream.statusCode === 410) {
    upstream.body.destroy();
    invalidate(req.params.id);
    return res.status(410).json({ error: 'Stream URL expired, search again' });
  }

  res.status(upstream.statusCode);
  for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
    if (upstream.headers[h]) res.setHeader(h, upstream.headers[h]);
  }

  // stop pulling data through the phone if the listener skips or leaves
  res.on('close', () => upstream.body.destroy());
  upstream.body.on('error', () => res.end());
  upstream.body.pipe(res);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Spitify backend running on port ${PORT}`);
});