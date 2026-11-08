const express = require('express');
const cors = require('cors');
const youtubeRunner = require('./services/youtubeRunner');

const app = express();
app.use(cors());

app.get('/api/youtube', async (req, res) => {
  const query = req.query.q || '';
  if (!query) return res.json([]);
  
  const results = await youtubeRunner(query);
  res.json(results);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Spitify backend running on port ${PORT}`);
});