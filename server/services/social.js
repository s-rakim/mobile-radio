// The social side: friends, what they're playing, messages, collaborative playlists, Blends
// and weekly listening stats. Only people with a Spitify account on this same server can
// take part; nothing here talks to any outside service.
//
// Stored like accounts.js: one JSON file under DATA_DIR, rewritten atomically. Fine for a
// group of friends; it would want a real database long before it got slow.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const accounts = require('./accounts');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const SOCIAL_FILE = path.join(DATA_DIR, 'social.json');

function load() {
  try {
    return JSON.parse(fs.readFileSync(SOCIAL_FILE, 'utf8'));
  } catch {
    return {};
  }
}

const db = Object.assign(
  { profiles: {}, following: {}, nowPlaying: {}, stats: {}, conversations: {}, lastRead: {}, collabs: {} },
  load(),
);

let saveTimer = null;
// Heartbeats come in every minute or so from every phone; batch the writes.
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    const tmp = `${SOCIAL_FILE}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(db));
    fs.renameSync(tmp, SOCIAL_FILE);
  }, 500);
}

class SocialError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const exists = (name) => accounts.userExists(name);

function requireUser(raw) {
  const name = String(raw || '').trim().toLowerCase();
  if (!exists(name)) throw new SocialError(404, 'No Spitify user with that name');
  return name;
}

function profile(name) {
  const p = db.profiles[name] || {};
  return {
    username: name,
    displayName: p.displayName || name,
    color: p.color || colorFor(name),
    activity: p.activity !== false,
  };
}

// A stable colour per user for their avatar, from the app's palette.
const AVATAR_COLORS = ['#22C55E', '#F97316', '#E11D48', '#8B5CF6', '#14B8A6', '#F59E0B', '#0EA5E9', '#EC4899'];
function colorFor(name) {
  const h = crypto.createHash('md5').update(name).digest()[0];
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

function updateProfile(name, body) {
  const p = db.profiles[name] || {};
  if (typeof body.displayName === 'string') p.displayName = body.displayName.trim().slice(0, 40) || undefined;
  if (typeof body.activity === 'boolean') p.activity = body.activity;
  db.profiles[name] = p;
  if (p.activity === false) delete db.nowPlaying[name];
  save();
  return profile(name);
}

function searchUsers(me, q) {
  const needle = String(q || '').trim().toLowerCase();
  if (needle.length < 2) return [];
  return accounts
    .allUsernames()
    .filter((n) => n !== me && (n.includes(needle) || (db.profiles[n]?.displayName || '').toLowerCase().includes(needle)))
    .slice(0, 20)
    .map((n) => ({ ...profile(n), following: (db.following[me] || []).includes(n) }));
}

// ---- Friends: you follow people, like Spotify. Mutual follows are "friends". ----

function follow(me, raw) {
  const name = requireUser(raw);
  if (name === me) throw new SocialError(400, "That's you");
  const list = new Set(db.following[me] || []);
  list.add(name);
  db.following[me] = [...list];
  save();
  return friends(me);
}

function unfollow(me, raw) {
  const name = String(raw || '').toLowerCase();
  db.following[me] = (db.following[me] || []).filter((n) => n !== name);
  save();
  return friends(me);
}

const followersOf = (name) => Object.keys(db.following).filter((n) => (db.following[n] || []).includes(name));

function friends(me) {
  const following = db.following[me] || [];
  const followers = followersOf(me);
  const now = Date.now();
  const people = [...new Set([...following, ...followers])].map((n) => {
    const p = profile(n);
    const np = p.activity ? db.nowPlaying[n] : null;
    return {
      ...p,
      following: following.includes(n),
      followsYou: followers.includes(n),
      // "Playing now" if the phone checked in within 3 minutes, else when it last did.
      nowPlaying: np ? { track: np.track, at: np.at, live: np.playing && now - np.at < 180_000 } : null,
    };
  });
  people.sort((a, b) => (b.nowPlaying?.at || 0) - (a.nowPlaying?.at || 0));
  return { me: profile(me), friends: people };
}

function setNowPlaying(me, body) {
  if (db.profiles[me]?.activity === false) return { ok: true };
  if (!body || !body.track) {
    if (db.nowPlaying[me]) db.nowPlaying[me].playing = false;
  } else {
    db.nowPlaying[me] = { track: slimTrack(body.track), playing: body.playing !== false, at: Date.now() };
  }
  save();
  return { ok: true };
}

// Keep stored songs small: only what another phone needs to show and play it.
function slimTrack(t) {
  if (!t || typeof t !== 'object' || !t.id) return null;
  const keep = ['id', 'title', 'artist', 'artistId', 'artwork', 'artworkLarge', 'durationSec', 'genre', 'link', 'streamUrl', 'origin', 'album', 'albumId', 'hls'];
  const out = {};
  for (const k of keep) if (t[k] !== undefined && t[k] !== null) out[k] = typeof t[k] === 'string' ? t[k].slice(0, 500) : t[k];
  return out;
}

// ---- Listening stats: each phone uploads its weekly totals; friends can compare. ----

function putStats(me, body) {
  const week = String(body?.week || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(week)) throw new SocialError(400, 'week must be the Monday, as YYYY-MM-DD');
  const mine = db.stats[me] || {};
  mine[week] = {
    minutes: Math.max(0, Math.round(Number(body.minutes) || 0)),
    topTracks: (body.topTracks || []).slice(0, 10).map((e) => ({ track: slimTrack(e.track), plays: Number(e.plays) || 0 })).filter((e) => e.track),
    topArtists: (body.topArtists || []).slice(0, 10).map((e) => ({ name: String(e.name || '').slice(0, 100), image: e.image || null, plays: Number(e.plays) || 0 })),
  };
  // Keep about a year.
  db.stats[me] = Object.fromEntries(Object.entries(mine).sort().slice(-53));
  save();
  return { ok: true };
}

function friendStats(me, week) {
  const people = [me, ...(db.following[me] || [])];
  return {
    week,
    people: people.map((n) => ({ ...profile(n), ...(db.stats[n]?.[week] || { minutes: 0, topTracks: [], topArtists: [] }) })),
  };
}

// ---- Messages: one conversation per pair of users. ----

const convoKey = (a, b) => [a, b].sort().join('|');

function conversations(me) {
  return Object.entries(db.conversations)
    .filter(([key]) => key.split('|').includes(me))
    .map(([key, msgs]) => {
      const other = key.split('|').find((n) => n !== me) || me;
      const last = msgs[msgs.length - 1];
      const read = db.lastRead[me]?.[key] || 0;
      return { with: profile(other), last, unread: msgs.filter((m) => m.from !== me && m.at > read).length };
    })
    .filter((c) => c.last)
    .sort((a, b) => b.last.at - a.last.at);
}

function messagesWith(me, raw) {
  const other = requireUser(raw);
  const key = convoKey(me, other);
  db.lastRead[me] = { ...(db.lastRead[me] || {}), [key]: Date.now() };
  save();
  return { with: profile(other), messages: db.conversations[key] || [] };
}

function send(me, raw, body) {
  const other = requireUser(raw);
  const kind = ['text', 'track', 'playlist'].includes(body?.kind) ? body.kind : 'text';
  const msg = { id: crypto.randomBytes(6).toString('hex'), from: me, at: Date.now(), kind, reactions: {} };
  if (typeof body.text === 'string') msg.text = body.text.trim().slice(0, 1000);
  if (kind === 'track') {
    msg.track = slimTrack(body.track);
    if (!msg.track) throw new SocialError(400, 'Missing song');
  }
  if (kind === 'playlist') {
    const p = body.playlist || {};
    msg.playlist = {
      id: String(p.id || ''),
      name: String(p.name || 'Playlist').slice(0, 100),
      artwork: p.artwork || null,
      tracks: (p.tracks || []).slice(0, 200).map(slimTrack).filter(Boolean),
    };
  }
  if (kind === 'text' && !msg.text) throw new SocialError(400, 'Empty message');
  const key = convoKey(me, other);
  const list = db.conversations[key] || [];
  list.push(msg);
  db.conversations[key] = list.slice(-500);
  db.lastRead[me] = { ...(db.lastRead[me] || {}), [key]: msg.at };
  save();
  return msg;
}

function react(me, raw, id, emoji) {
  const other = requireUser(raw);
  const msg = (db.conversations[convoKey(me, other)] || []).find((m) => m.id === id);
  if (!msg) throw new SocialError(404, 'Message not found');
  if (emoji) msg.reactions[me] = String(emoji).slice(0, 8);
  else delete msg.reactions[me];
  save();
  return msg;
}

// ---- Collaborative playlists: anyone with the invite link can join and add songs. ----

function collabView(c) {
  return { ...c, members: c.members.map(profile) };
}

function myCollabs(me) {
  return Object.values(db.collabs)
    .filter((c) => c.members.includes(me))
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .map(collabView);
}

function getCollab(id, me) {
  const c = db.collabs[id];
  if (!c) throw new SocialError(404, 'That playlist is gone');
  if (me && !c.members.includes(me)) throw new SocialError(403, 'Join the playlist first');
  return c;
}

function createCollab(me, body) {
  const id = crypto.randomBytes(8).toString('hex');
  const c = {
    id,
    name: String(body?.name || 'Collaborative playlist').trim().slice(0, 100) || 'Collaborative playlist',
    owner: me,
    members: [me],
    tracks: (body?.tracks || []).slice(0, 500).map(slimTrack).filter(Boolean).map((t) => ({ track: t, addedBy: me, at: Date.now() })),
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
  db.collabs[id] = c;
  save();
  return collabView(c);
}

function joinCollab(me, id) {
  const c = getCollab(id);
  if (!c.members.includes(me)) c.members.push(me);
  c.updatedAt = Date.now();
  save();
  return collabView(c);
}

function leaveCollab(me, id) {
  const c = getCollab(id, me);
  c.members = c.members.filter((n) => n !== me);
  if (c.members.length === 0) delete db.collabs[id];
  else if (c.owner === me) c.owner = c.members[0];
  save();
  return { ok: true };
}

function addToCollab(me, id, track) {
  const c = getCollab(id, me);
  const t = slimTrack(track);
  if (!t) throw new SocialError(400, 'Missing song');
  if (!c.tracks.some((e) => e.track.id === t.id)) c.tracks.push({ track: t, addedBy: me, at: Date.now() });
  c.updatedAt = Date.now();
  save();
  return collabView(c);
}

function removeFromCollab(me, id, trackId) {
  const c = getCollab(id, me);
  c.tracks = c.tracks.filter((e) => e.track.id !== trackId);
  c.updatedAt = Date.now();
  save();
  return collabView(c);
}

function renameCollab(me, id, name) {
  const c = getCollab(id, me);
  c.name = String(name || '').trim().slice(0, 100) || c.name;
  c.updatedAt = Date.now();
  save();
  return collabView(c);
}

// ---- Blend: a playlist from two people's libraries, shared taste first. ----

function tracksOf(name) {
  const lib = accounts.getLibrary(name).library || {};
  const out = new Map();
  for (const t of lib.liked || []) out.set(t.id, { t, w: 3 });
  for (const p of lib.playlists || []) for (const t of p.tracks || []) if (!out.has(t.id)) out.set(t.id, { t, w: 1 });
  const weekly = Object.values(db.stats[name] || {}).slice(-4);
  for (const wk of weekly) for (const e of wk.topTracks || []) {
    const cur = out.get(e.track.id);
    out.set(e.track.id, { t: cur?.t || e.track, w: (cur?.w || 0) + Math.min(e.plays, 5) });
  }
  return out;
}

function blend(me, raw) {
  const other = requireUser(raw);
  const a = tracksOf(me);
  const b = tracksOf(other);
  if (a.size === 0 || b.size === 0) {
    throw new SocialError(409, `${a.size === 0 ? 'You need' : `${profile(other).displayName} needs`} some liked songs first, and library sync turned on`);
  }
  const shared = [...a.keys()].filter((id) => b.has(id));
  const artistsA = new Set([...a.values()].map((e) => e.t.artist));
  const artistsB = new Set([...b.values()].map((e) => e.t.artist));
  const sharedArtists = [...artistsA].filter((x) => artistsB.has(x));
  // Overlap of songs and artists, scaled so a decent overlap reads as a high match.
  const songScore = shared.length / Math.max(1, Math.min(a.size, b.size));
  const artistScore = sharedArtists.length / Math.max(1, Math.min(artistsA.size, artistsB.size));
  const match = Math.min(99, Math.round(35 + 65 * Math.sqrt(0.4 * songScore + 0.6 * artistScore)));

  const pick = (m) => [...m.values()].sort((x, y) => y.w - x.w).map((e) => e.t);
  const first = shared.map((id) => a.get(id).t);
  const restA = pick(a).filter((t) => !b.has(t.id));
  const restB = pick(b).filter((t) => !a.has(t.id));
  const tracks = [...first];
  while (tracks.length < 50 && (restA.length || restB.length)) {
    if (restA.length) tracks.push(restA.shift());
    if (restB.length) tracks.push(restB.shift());
  }
  return {
    name: `${profile(me).displayName} + ${profile(other).displayName}`,
    with: profile(other),
    match,
    sharedArtists: sharedArtists.slice(0, 5),
    tracks: tracks.slice(0, 50).map(slimTrack),
  };
}

// ---- Jams: the host's queue and playhead, so friends can join from anywhere or nearby. ----

const jams = new Map(); // host -> { tracks, index, positionMs, at }, kept in memory only

function hostJam(me, body) {
  jams.set(me, {
    tracks: (body?.tracks || []).slice(0, 100).map(slimTrack).filter(Boolean),
    index: Math.max(0, Number(body?.index) || 0),
    positionMs: Math.max(0, Number(body?.positionMs) || 0),
    at: Date.now(),
  });
  return { ok: true };
}

function endJam(me) {
  jams.delete(me);
  return { ok: true };
}

function jamOf(raw) {
  const host = requireUser(raw);
  const jam = jams.get(host);
  // A Jam the host hasn't updated in 20 minutes is over.
  if (!jam || Date.now() - jam.at > 20 * 60_000) throw new SocialError(404, `${profile(host).displayName} isn't hosting a Jam right now`);
  return { host: profile(host), ...jam };
}

module.exports = {
  hostJam,
  endJam,
  jamOf,
  SocialError,
  profile,
  updateProfile,
  searchUsers,
  follow,
  unfollow,
  friends,
  setNowPlaying,
  putStats,
  friendStats,
  conversations,
  messagesWith,
  send,
  react,
  myCollabs,
  getCollab: (me, id) => collabView(getCollab(id, me)),
  previewCollab: (id) => {
    const c = getCollab(id);
    return { id: c.id, name: c.name, owner: profile(c.owner), members: c.members.length, tracks: c.tracks.length };
  },
  createCollab,
  joinCollab,
  leaveCollab,
  addToCollab,
  removeFromCollab,
  renameCollab,
  blend,
};
