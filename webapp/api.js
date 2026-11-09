// Audius public API. No key needed, just an app name. Responses are kept for a few minutes
// so moving back and forth between pages is instant.

const BASE = 'https://api.audius.co';
const APP = 'spitify';
const TTL = 5 * 60 * 1000;
const cache = new Map();

async function get(path, params = {}) {
  const url = new URL(BASE + path);
  for (const [k, v] of Object.entries(params)) if (v != null) url.searchParams.set(k, v);
  url.searchParams.set('app_name', APP);
  const key = url.toString();
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL) return hit.data;

  const res = await fetch(key, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Audius said ${res.status}`);
  const body = await res.json();
  cache.set(key, { at: Date.now(), data: body.data });
  return body.data;
}

const pick = (art, ...sizes) => {
  if (!art) return null;
  for (const s of sizes) if (art[s]) return art[s];
  return null;
};

export function toTrack(t) {
  return {
    id: String(t.id),
    title: t.title || 'Untitled',
    artist: t.user?.name || 'Unknown artist',
    artistId: t.user?.id ? String(t.user.id) : null,
    artistImage: pick(t.user?.profile_picture, '480x480', '150x150'),
    artwork: pick(t.artwork, '480x480', '150x150'),
    artworkLarge: pick(t.artwork, '1000x1000', '480x480'),
    duration: t.duration || 0,
    genre: t.genre || '',
    plays: t.play_count || 0,
    link: t.permalink ? `https://audius.co${t.permalink}` : null,
    direct: t.stream?.url && t.stream.url.startsWith('https://') ? t.stream.url : null,
  };
}

const playable = (t) => t && t.is_streamable !== false && t.access?.stream !== false;
const tracks = (list) => (list || []).filter(playable).map(toTrack);

function toPlaylist(p) {
  return {
    id: String(p.id),
    name: p.playlist_name || 'Playlist',
    owner: p.user?.name || '',
    ownerId: p.user?.id ? String(p.user.id) : null,
    ownerImage: pick(p.user?.profile_picture, '150x150'),
    artwork: pick(p.artwork, '480x480', '1000x1000', '150x150'),
    description: p.description || '',
    count: p.track_count || 0,
  };
}

function toArtist(u) {
  return {
    id: String(u.id),
    name: u.name || u.handle || 'Artist',
    handle: u.handle || '',
    picture: pick(u.profile_picture, '480x480', '150x150'),
    pictureLarge: pick(u.profile_picture, '1000x1000', '480x480'),
    cover: pick(u.cover_photo, '2000x', '640x'),
    followers: u.follower_count || 0,
    tracks: u.track_count || 0,
    bio: u.bio || '',
  };
}

export const audius = {
  trending: (opts = {}) => get('/v1/tracks/trending', { time: opts.time || 'week', limit: opts.limit || 40, genre: opts.genre }).then(tracks),
  trendingPlaylists: (limit = 20) => get('/v1/playlists/trending', { time: 'week', limit }).then((l) => (l || []).map(toPlaylist)),
  searchTracks: (q) => get('/v1/tracks/search', { query: q, limit: 30 }).then(tracks),
  searchArtists: (q) => get('/v1/users/search', { query: q, limit: 20 }).then((l) => (l || []).map(toArtist)),
  searchPlaylists: (q) => get('/v1/playlists/search', { query: q, limit: 20 }).then((l) => (l || []).map(toPlaylist)),
  playlist: (id) => get(`/v1/playlists/${id}`).then((l) => (l && l[0] ? toPlaylist(l[0]) : null)),
  playlistTracks: (id) => get(`/v1/playlists/${id}/tracks`).then(tracks),
  artist: (id) => get(`/v1/users/${id}`).then((u) => (u ? toArtist(u) : null)),
  async artistTracks(id) {
    const all = [];
    for (let offset = 0; offset < 300; offset += 100) {
      const page = await get(`/v1/users/${id}/tracks`, { limit: 100, offset });
      all.push(...(page || []));
      if (!page || page.length < 100) break;
    }
    return tracks(all).sort((a, b) => b.plays - a.plays);
  },
  streamUrl: (id) => `${BASE}/v1/tracks/${id}/stream?app_name=${APP}`,
};

// Lyrics from LRCLIB, same as the phone app. Synced lines when the song is listed with times.
const lyricCache = new Map();
export async function findLyrics(track) {
  if (lyricCache.has(track.id)) return lyricCache.get(track.id);
  let result = null;
  try {
    const url = new URL('https://lrclib.net/api/search');
    url.searchParams.set('track_name', track.title);
    url.searchParams.set('artist_name', track.artist);
    const res = await fetch(url);
    if (res.ok) {
      const list = await res.json();
      const best = list.find((l) => l.syncedLyrics && Math.abs((l.duration || 0) - track.duration) < 4)
        || list.find((l) => l.syncedLyrics)
        || list.find((l) => l.plainLyrics);
      if (best) result = parseLyrics(best);
    }
  } catch { /* no lyrics is fine */ }
  lyricCache.set(track.id, result);
  return result;
}

function parseLyrics(entry) {
  if (entry.syncedLyrics) {
    const lines = [];
    for (const raw of entry.syncedLyrics.split('\n')) {
      const m = raw.match(/^\[(\d+):(\d+(?:\.\d+)?)\]\s?(.*)$/);
      if (m) lines.push({ time: (+m[1] * 60 + +m[2]) * 1000, text: m[3] || '♪' });
    }
    if (lines.length) return { synced: true, lines };
  }
  const plain = (entry.plainLyrics || '').split('\n').map((text) => ({ time: null, text }));
  return plain.length ? { synced: false, lines: plain } : null;
}
