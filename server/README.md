# Spitify web server

A small Express server with a test page that searches several catalogues at once. The Android app doesn't need it; it talks to its sources directly.

```
cd server
npm install
npm start          # http://localhost:3000
```

Needs Node 20 or newer. Set `PORT` to change the port and `JAMENDO_CLIENT_ID` to turn Jamendo on. Without an ID the Jamendo route just returns nothing, because the old shared ID no longer works.

The page lives in `public/` and calls the API on whatever host served it, so the same files work locally and on Render.

## Routes

All take `?q=` and answer 400 without it. Each upstream call gives up after 10 seconds.

| Route | Source |
| --- | --- |
| `/api/audius` | Audius search (api.audius.co) |
| `/api/archive` | Internet Archive netlabels and live shows |
| `/api/ccmixter` | ccMixter Creative Commons tracks |
| `/api/deezer` | Deezer's official API, 30 second previews only |
| `/api/jamendo` | Jamendo, only with `JAMENDO_CLIENT_ID` set |
