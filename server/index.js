// --- NEW AUDIUS ROUTE (Full Tracks) ---
app.get('/api/audius', async (req, res) => {
    const query = req.query.q;
    // You can pick any active discovery node from https://api.audius.co/
    const AUDIUS_DISCOVERY_NODE = 'https://discoveryprovider.audius.co';
    const AUDIUS_APP_NAME = 'SpitifyClone';

    try {
        const url = `${AUDIUS_DISCOVERY_NODE}/v1/tracks/search?query=${encodeURIComponent(query)}&app_name=${AUDIUS_APP_NAME}`;
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (error) {
        console.error("Audius Proxy Error:", error);
        res.status(500).json({ error: "Failed to fetch from Audius" });
    }
});
