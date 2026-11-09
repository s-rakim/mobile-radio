import { audius, findLyrics } from './api.js';

/* ================= icons ================= */

const I = {
  play: '<svg class="fill" viewBox="0 0 24 24"><path d="M7 4.8v14.4c0 .8.9 1.3 1.6.9l11.3-7.2c.6-.4.6-1.4 0-1.8L8.6 3.9C7.9 3.5 7 4 7 4.8z"/></svg>',
  pause: '<svg class="fill" viewBox="0 0 24 24"><rect x="6" y="4" width="4" height="16" rx="1.2"/><rect x="14" y="4" width="4" height="16" rx="1.2"/></svg>',
  next: '<svg class="fill" viewBox="0 0 24 24"><path d="M5 5.6v12.8c0 .8.9 1.2 1.5.8l9-6.4c.6-.4.6-1.2 0-1.6l-9-6.4C5.9 4.4 5 4.8 5 5.6z"/><rect x="17" y="4.5" width="2.4" height="15" rx="1"/></svg>',
  prev: '<svg class="fill" viewBox="0 0 24 24"><path d="M19 5.6v12.8c0 .8-.9 1.2-1.5.8l-9-6.4c-.6-.4-.6-1.2 0-1.6l9-6.4c.6-.4 1.5 0 1.5.8z"/><rect x="4.6" y="4.5" width="2.4" height="15" rx="1"/></svg>',
  shuffle: '<svg viewBox="0 0 24 24"><path d="M16 4h4v4M4 20 20 4M20 16v4h-4M15 15l5 5M4 4l5 5"/></svg>',
  repeat: '<svg viewBox="0 0 24 24"><path d="M17 2l4 4-4 4"/><path d="M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4"/><path d="M21 13v2a3 3 0 0 1-3 3H3"/></svg>',
  repeatOne: '<svg viewBox="0 0 24 24"><path d="M17 2l4 4-4 4"/><path d="M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4"/><path d="M21 13v2a3 3 0 0 1-3 3H3"/><path d="M11 10.5 12.5 9.5V14.5" stroke-width="1.8"/></svg>',
  heart: '<svg viewBox="0 0 24 24"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg>',
  heartFill: '<svg class="fill" viewBox="0 0 24 24"><path d="M12 20.5s-7.5-4.6-7.5-10.5A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.8c0 5.9-7.5 10.5-7.5 10.5z"/></svg>',
  plusCircle: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/></svg>',
  check: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8 12.5 2.7 2.7L16 9.8" stroke="var(--bg)"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  queue: '<svg viewBox="0 0 24 24"><path d="M4 6h12M4 12h12M4 18h8"/><path d="M17 15.5v5l4-2.5z" fill="currentColor"/></svg>',
  lyrics: '<svg viewBox="0 0 24 24"><path d="M4 5h11M4 10h11M4 15h6"/><circle cx="17.5" cy="17" r="2.5"/><path d="M20 17V9l-2.5 1"/></svg>',
  nowPlaying: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M14 4v16"/></svg>',
  volume: '<svg viewBox="0 0 24 24"><path d="M4 9.5v5h3.5L12 18V6L7.5 9.5z" fill="currentColor"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/></svg>',
  volumeLow: '<svg viewBox="0 0 24 24"><path d="M4 9.5v5h3.5L12 18V6L7.5 9.5z" fill="currentColor"/><path d="M15.5 9a4 4 0 0 1 0 6"/></svg>',
  mute: '<svg viewBox="0 0 24 24"><path d="M4 9.5v5h3.5L12 18V6L7.5 9.5z" fill="currentColor"/><path d="m16 9.5 5 5M21 9.5l-5 5"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  library: '<svg viewBox="0 0 24 24"><path d="M4 4v16M9 4v16M14 4.5l5.5 15"/></svg>',
  note: '<svg viewBox="0 0 24 24"><path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/></svg>',
  person: '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
  dots: '<svg class="fill" viewBox="0 0 24 24"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>',
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/></svg>',
  clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/></svg>',
  link: '<svg viewBox="0 0 24 24"><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/></svg>',
  trash: '<svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
  pencil: '<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>',
  radio: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="2"/><path d="M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4M19 5a10 10 0 0 1 0 14M5 19A10 10 0 0 1 5 5"/></svg>',
  wifiOff: '<svg viewBox="0 0 24 24"><path d="M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 12.5a10 10 0 0 1 4-2.4M19 12.5a10 10 0 0 0-3-2M2 8.5a15 15 0 0 1 4.5-2.8M22 8.5A15 15 0 0 0 11 4.6"/><circle cx="12" cy="20" r="1" fill="currentColor"/></svg>',
};

/* ================= data ================= */

const GENRES = [
  ['Hip-Hop/Rap', '#EA580C'], ['Electronic', '#0891B2'], ['Pop', '#DB2777'], ['R&B/Soul', '#881337'],
  ['Lo-Fi', '#0D9488'], ['Alternative', '#0369A1'], ['House', '#86198F'], ['Rock', '#DC2626'],
  ['Ambient', '#059669'], ['Jazz', '#A16207'], ['Latin', '#C2410C'], ['Techno', '#4338CA'],
  ['Dubstep', '#7C3AED'], ['Trap', '#E11D48'], ['Acoustic', '#9A3412'], ['Reggae', '#16A34A'],
  ['Soundtrack', '#1E3A8A'], ['Experimental', '#164E63'], ['Funk', '#D97706'], ['Classical', '#831843'],
];
const genreColour = (name) => (GENRES.find(([g]) => g.toLowerCase() === String(name).toLowerCase()) || [0, '#535353'])[1];

// Same presets as the phone app.
const THEMES = [
  { id: 'spitify', name: 'Spitify', accent: '#2AD46B', bg: '#121212', base: '#000000' },
  { id: 'midnight', name: 'Midnight', accent: '#22C55E', bg: '#0F0F23', base: '#08081A' },
  { id: 'sunset', name: 'Sunset', accent: '#F97316', bg: '#0F0F23', base: '#08081A' },
  { id: 'cinema', name: 'Cinema', accent: '#E11D48', bg: '#000000', base: '#000000' },
  { id: 'spotlight', name: 'Spotlight', accent: '#CA8A04', bg: '#0F0F23', base: '#08081A' },
  { id: 'studio', name: 'Studio', accent: '#8B5CF6', bg: '#0F172A', base: '#080D1A' },
  { id: 'neon', name: 'Neon', accent: '#F43F5E', bg: '#0F0F23', base: '#08081A' },
  { id: 'lagoon', name: 'Lagoon', accent: '#14B8A6', bg: '#0F172A', base: '#080D1A' },
  { id: 'ember', name: 'Ember', accent: '#F59E0B', bg: '#1C1917', base: '#0C0A09' },
  { id: 'mono', name: 'Mono', accent: '#F8FAFC', bg: '#000000', base: '#000000' },
];

/* ================= storage ================= */

const store = {
  read(key, fallback) {
    try {
      const raw = localStorage.getItem('spitify.' + key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch { return fallback; }
  },
  write(key, value) {
    try { localStorage.setItem('spitify.' + key, JSON.stringify(value)); } catch { /* storage full or blocked */ }
  },
};

const lib = {
  liked: store.read('liked', []),           // tracks, newest first
  playlists: store.read('playlists', []),   // { id, name, tracks, created }
  saved: store.read('saved', []),           // Audius playlists saved to the library
  following: store.read('following', []),   // artists
  recent: store.read('recent', []),         // { kind, id, title, sub, art }
  searches: store.read('searches', []),
  history: store.read('history', []),       // tracks played, newest first
};
const saveLib = (key) => store.write(key, lib[key]);

const prefs = Object.assign({ theme: 'spitify', volume: 0.8, shuffle: false, repeat: 'off', side: 'now', autoplay: true, filter: 'all' }, store.read('prefs', {}));
const savePrefs = () => store.write('prefs', prefs);

const isLiked = (id) => lib.liked.some((t) => t.id === id);
function toggleLike(track) {
  if (isLiked(track.id)) {
    lib.liked = lib.liked.filter((t) => t.id !== track.id);
    toast('Removed from Liked Songs');
  } else {
    lib.liked.unshift(slim(track));
    toast('Added to Liked Songs');
  }
  saveLib('liked');
  refreshLikes();
}

// Only what's needed to play a song later. Stream links expire, so they aren't kept.
const slim = (t) => ({ id: t.id, title: t.title, artist: t.artist, artistId: t.artistId, artistImage: t.artistImage, artwork: t.artwork, artworkLarge: t.artworkLarge, duration: t.duration, genre: t.genre, plays: t.plays, link: t.link });

function recordVisit(item) {
  lib.recent = [item, ...lib.recent.filter((r) => !(r.kind === item.kind && r.id === item.id))].slice(0, 30);
  saveLib('recent');
}

/* ================= helpers ================= */

const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtTime = (sec) => {
  if (!isFinite(sec) || sec < 0) sec = 0;
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = Math.floor(sec % 60);
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
};
const fmtNum = (n) => new Intl.NumberFormat('en', { notation: n >= 10000 ? 'compact' : 'standard', maximumFractionDigits: 1 }).format(n || 0);
const totalTime = (tracks) => {
  const sec = tracks.reduce((a, t) => a + (t.duration || 0), 0);
  const h = Math.floor(sec / 3600), m = Math.round((sec % 3600) / 60);
  return h ? `${h} hr ${m} min` : `${m} min`;
};
const plural = (n, word) => `${fmtNum(n)} ${word}${n === 1 ? '' : 's'}`;

function art(url, cls = '', fallback = I.note) {
  return `<div class="art ${cls}">${fallback}${url ? `<img src="${esc(url)}" alt="" loading="lazy">` : ''}</div>`;
}
const likedArt = (cls = '') => `<div class="art liked ${cls}">${I.heartFill}</div>`;

function mosaic(tracks, cls = '') {
  const covers = [...new Set(tracks.map((t) => t.artwork).filter(Boolean))].slice(0, 4);
  if (covers.length < 4) return art(covers[0], cls);
  return `<div class="art ${cls}" style="display:grid;grid-template-columns:1fr 1fr">${covers.map((c) => `<img class="loaded" style="position:static" src="${esc(c)}" alt="">`).join('')}</div>`;
}

/* ================= colour from artwork ================= */

const colourCache = new Map();
function artColour(url) {
  if (!url) return Promise.resolve(null);
  if (colourCache.has(url)) return colourCache.get(url);
  const job = new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const c = document.createElement('canvas');
        c.width = c.height = 24;
        const ctx = c.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0, 24, 24);
        const px = ctx.getImageData(0, 0, 24, 24).data;
        // Weight saturated, mid-bright pixels so the result isn't a muddy average.
        let r = 0, g = 0, b = 0, w = 0;
        for (let i = 0; i < px.length; i += 4) {
          const [pr, pg, pb] = [px[i], px[i + 1], px[i + 2]];
          const max = Math.max(pr, pg, pb), min = Math.min(pr, pg, pb);
          const sat = max ? (max - min) / max : 0;
          const light = max / 255;
          const weight = 0.05 + sat * sat * (light > 0.15 && light < 0.95 ? 1 : 0.2);
          r += pr * weight; g += pg * weight; b += pb * weight; w += weight;
        }
        resolve(shade([r / w, g / w, b / w]));
      } catch { resolve(null); }
    };
    img.onerror = () => resolve(null);
    img.src = url;
  });
  colourCache.set(url, job);
  return job;
}

// Keep header colours deep enough for white text on top.
function shade([r, g, b]) {
  const l = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  const k = l > 0.42 ? 0.42 / l : l < 0.18 ? Math.min(1.8, 0.2 / Math.max(l, 0.02)) : 1;
  const to = (v) => Math.round(Math.min(255, v * k)).toString(16).padStart(2, '0');
  return `#${to(r)}${to(g)}${to(b)}`;
}

function paintHeader(url, fallback = '#3a3a3a') {
  const main = $('#main');
  main.style.setProperty('--header', fallback);
  const token = (main.dataset.paint = String(Math.random()));
  artColour(url).then((c) => {
    if (c && main.dataset.paint === token) main.style.setProperty('--header', c);
  });
}

/* ================= theme ================= */

function mix(hex, other, t) {
  const a = hex.match(/\w\w/g).map((x) => parseInt(x, 16));
  const b = other.match(/\w\w/g).map((x) => parseInt(x, 16));
  return '#' + a.map((v, i) => Math.round(v + (b[i] - v) * t).toString(16).padStart(2, '0')).join('');
}
function readableOn(hex) {
  const [r, g, b] = hex.match(/\w\w/g).map((x) => parseInt(x, 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  const l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return (l + 0.05) / 0.05 >= 1.05 / (l + 0.05) ? '#000000' : '#ffffff';
}
function applyTheme() {
  const t = THEMES.find((x) => x.id === prefs.theme) || THEMES[0];
  const s = document.documentElement.style;
  s.setProperty('--base', t.base);
  s.setProperty('--bg', t.bg);
  s.setProperty('--elevated', mix(t.bg, '#ffffff', 0.06));
  s.setProperty('--highlight', mix(t.bg, '#ffffff', 0.11));
  s.setProperty('--pressed', mix(t.bg, '#ffffff', 0.17));
  s.setProperty('--divider', mix(t.bg, '#ffffff', 0.12));
  s.setProperty('--accent', t.accent);
  s.setProperty('--on-accent', readableOn(t.accent));
  $('meta[name="theme-color"]').setAttribute('content', t.base);
}

/* ================= router ================= */

const nav = { stack: [{ name: 'home' }], index: 0 };
const route = () => nav.stack[nav.index];
const scrollMemory = new Map();

function go(r, { replace = false } = {}) {
  scrollMemory.set(nav.index, $('#main').scrollTop);
  if (replace) nav.stack[nav.index] = r;
  else {
    nav.stack = nav.stack.slice(0, nav.index + 1);
    nav.stack.push(r);
    nav.index = nav.stack.length - 1;
  }
  render(true);
}
function back() { if (nav.index > 0) { scrollMemory.set(nav.index, $('#main').scrollTop); nav.index--; render(false); } }
function forward() { if (nav.index < nav.stack.length - 1) { scrollMemory.set(nav.index, $('#main').scrollTop); nav.index++; render(false); } }

let renderToken = 0;
async function render(fresh) {
  const r = route();
  const token = ++renderToken;
  const main = $('#main');
  $('[data-action="back"]').disabled = nav.index === 0;
  $('[data-action="forward"]').disabled = nav.index >= nav.stack.length - 1;
  $('.home-btn').classList.toggle('active', r.name === 'home');
  if (r.name !== 'search') $('#search').value = '';
  else if (document.activeElement !== $('#search')) $('#search').value = r.q || '';

  const view = VIEWS[r.name] || VIEWS.home;
  main.innerHTML = '';
  main.style.setProperty('--header', '#3a3a3a');
  try {
    await view(r, main, () => token === renderToken);
  } catch (err) {
    if (token !== renderToken) return;
    main.innerHTML = errorView(err);
  }
  if (token !== renderToken) return;
  main.scrollTop = fresh ? 0 : scrollMemory.get(nav.index) || 0;
  renderSidebar();
  markPlaying();
}

function errorView(err) {
  const offline = !navigator.onLine;
  return `<div class="view empty">
    ${offline ? I.wifiOff : I.radio}
    <h2>${offline ? "You're offline" : "Couldn't load this"}</h2>
    <div>${offline ? 'Check your connection and try again.' : esc(err?.message || 'Audius did not answer.')}</div>
    <button class="pill-btn" data-action="retry">Try again</button>
  </div>`;
}

/* ================= lists of tracks ================= */

// Every list on screen gets an id so a click on a row knows which songs to queue.
const lists = new Map();
let listSeq = 0;
function registerList(tracks, context) {
  const id = 'l' + ++listSeq;
  lists.set(id, { tracks, context });
  if (lists.size > 60) lists.delete(lists.keys().next().value);
  return id;
}

function trackTable(tracks, context, { compact = false, numbered = true, showArt = true, playlistId = null } = {}) {
  const list = registerList(tracks, context);
  const rows = tracks.map((t, i) => `
    <div class="row" data-list="${list}" data-i="${i}" data-id="${esc(t.id)}" ${playlistId ? `data-mine="${esc(playlistId)}"` : ''}>
      <div class="num">${numbered ? `<span class="n">${i + 1}</span>` : '<span class="n"></span>'}<button class="p" data-action="play-row" aria-label="Play ${esc(t.title)}">${I.play}</button></div>
      <div class="title">
        ${showArt ? art(t.artwork) : ''}
        <div class="txt">
          <div class="t1">${esc(t.title)}</div>
          <div class="t2">${t.artistId ? `<a href="#" data-action="artist" data-id="${esc(t.artistId)}">${esc(t.artist)}</a>` : esc(t.artist)}</div>
        </div>
      </div>
      <div class="col-genre">${esc(t.genre)}</div>
      <div class="col-plays r">${t.plays ? fmtNum(t.plays) : ''}</div>
      <div class="end">
        <button class="like ${isLiked(t.id) ? 'on' : ''}" data-action="like-row" aria-label="${isLiked(t.id) ? 'Remove from' : 'Save to'} Liked Songs">${isLiked(t.id) ? I.check : I.plusCircle}</button>
        <span>${fmtTime(t.duration)}</span>
      </div>
    </div>`).join('');
  return `<div class="tracks ${compact ? 'compact' : ''}">
    ${compact ? '' : `<div class="tracks-head"><div class="r">#</div><div>Title</div><div class="col-genre">Genre</div><div class="col-plays r">Plays</div><div class="end">${I.clock}</div></div>`}
    ${rows}
  </div>`;
}

function card({ kind, id, title, sub, artUrl, round = false, play = true, extra = '' }) {
  return `<div class="card ${round ? 'round' : ''}" role="button" tabindex="0" data-action="open" data-kind="${kind}" data-id="${esc(id)}" ${extra}>
    ${art(artUrl, '', round ? I.person : I.note)}
    ${play ? `<button class="play-fab" data-action="play-card" aria-label="Play ${esc(title)}">${I.play}</button>` : ''}
    <div class="t1">${esc(title)}</div>
    <div class="t2">${esc(sub)}</div>
  </div>`;
}

const skeletonShelf = (n = 6) => `<div class="shelf">${Array.from({ length: n }, () => '<div class="skeleton-card"><div class="skeleton"></div><div class="skeleton"></div></div>').join('')}</div>`;

/* ================= views ================= */

function greeting() {
  const h = new Date().getHours();
  return h < 5 ? 'Good night' : h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

const VIEWS = {
  async home(r, main, alive) {
    const filter = prefs.filter;
    main.innerHTML = `<div class="view">
      <div class="wash"></div>
      <div class="home-top">
        <div class="chips">
          ${[['all', 'All'], ['music', 'Music'], ['playlists', 'Playlists'], ['artists', 'Artists']].map(([id, label]) => `<button class="chip ${filter === id ? 'on' : ''}" data-action="home-filter" data-id="${id}">${label}</button>`).join('')}
        </div>
        <div class="greeting">${greeting()}</div>
        <div class="shortcuts">${shortcutTiles()}</div>
      </div>
      <div id="shelves">${['Trending this week', 'Made for you', 'Popular playlists'].map((t) => `<section class="section"><div class="section-head"><h2>${t}</h2></div>${skeletonShelf()}</section>`).join('')}</div>
    </div>`;
    paintHeader(lib.recent[0]?.art || lib.liked[0]?.artwork, '#2a3a30');

    const [week, month, all, lists] = await Promise.all([
      audius.trending({ time: 'week', limit: 30 }),
      audius.trending({ time: 'month', limit: 20 }).catch(() => []),
      audius.trending({ time: 'allTime', limit: 20 }).catch(() => []),
      audius.trendingPlaylists(20).catch(() => []),
    ]);
    if (!alive()) return;
    homeData = { week, lists };
    $('.shortcuts', main).innerHTML = shortcutTiles();

    const recentShelf = lib.recent.slice(0, 10);
    const artists = uniqueBy([...week, ...month, ...all].filter((t) => t.artistId), (t) => t.artistId).slice(0, 12);
    const sections = [];
    const showMusic = filter === 'all' || filter === 'music';
    const showLists = filter === 'all' || filter === 'playlists';
    const showArtists = filter === 'all' || filter === 'artists';

    if (recentShelf.length && filter === 'all') {
      sections.push(shelf('Recently played', recentShelf.map((x) => card({ kind: x.kind, id: x.id, title: x.title, sub: x.sub, artUrl: x.art, round: x.kind === 'artist', play: x.kind !== 'artist' }))));
    }
    if (showMusic) sections.push(shelf('Trending this week', week.slice(0, 16).map((t) => trackCard(t, week, 'Trending this week')), { list: registerList(week, { label: 'Trending', title: 'Trending this week' }) }));
    if (showLists) sections.push(shelf('Made for you', GENRES.slice(0, 12).map(([g, colour]) => mixCard(g, colour, week))));
    if (showLists && lists.length) sections.push(shelf('Popular playlists', lists.map((p) => card({ kind: 'playlist', id: p.id, title: p.name, sub: p.owner ? `By ${p.owner}` : `${p.count} songs`, artUrl: p.artwork }))));
    if (showArtists && artists.length) sections.push(shelf('Artists you might like', artists.map((t) => card({ kind: 'artist', id: t.artistId, title: t.artist, sub: 'Artist', artUrl: t.artistImage, round: true, play: false }))));
    if (showMusic && month.length) sections.push(shelf('Top of the month', month.map((t) => trackCard(t, month, 'Top of the month'))));
    if (showMusic && all.length) sections.push(shelf('All-time greats', all.map((t) => trackCard(t, all, 'All-time greats'))));
    $('#shelves', main).innerHTML = sections.join('');
  },

  async search(r, main, alive) {
    const q = (r.q || '').trim();
    if (!q) {
      main.innerHTML = `<div class="view">
        ${lib.searches.length ? `<section class="section"><div class="section-head"><h2>Recent searches</h2><button class="more" data-action="clear-searches">Clear</button></div>
          <div class="recent-searches">${lib.searches.map((s) => `<span class="chip" role="button" tabindex="0" data-action="search-again" data-q="${esc(s)}">${esc(s)}<button data-action="forget-search" data-q="${esc(s)}" aria-label="Remove ${esc(s)}">${I.close}</button></span>`).join('')}</div></section>` : ''}
        <section class="section"><div class="section-head"><h2>Browse all</h2></div>
          <div class="genres">${GENRES.map(([g, c]) => `<button class="genre" data-action="open" data-kind="mix" data-id="${esc(g)}" style="background:linear-gradient(135deg, ${mix(c.slice(1), '#ffffff', 0.08)}, ${c} 40%, ${mix(c.slice(1), '#000000', 0.35)})">${esc(g)}<span data-genre-cover="${esc(g)}"></span></button>`).join('')}</div>
        </section>
      </div>`;
      // Covers for the genre tiles trickle in after the grid is up.
      GENRES.forEach(([g]) => {
        audius.trending({ genre: g, limit: 1 }).then((l) => {
          const slot = main.querySelector(`[data-genre-cover="${CSS.escape(g)}"]`);
          if (slot && l[0]?.artwork && alive()) slot.outerHTML = art(l[0].artwork);
        }).catch(() => {});
      });
      return;
    }

    main.innerHTML = `<div class="view"><section class="section"><div class="search-top"><div class="skeleton" style="height:220px"></div><div class="skeleton" style="height:220px"></div></div></section>
      <section class="section">${skeletonShelf()}</section></div>`;
    const [tracks, artists, playlists] = await Promise.all([
      audius.searchTracks(q),
      audius.searchArtists(q).catch(() => []),
      audius.searchPlaylists(q).catch(() => []),
    ]);
    if (!alive()) return;
    rememberSearch(q);

    if (!tracks.length && !artists.length && !playlists.length) {
      main.innerHTML = `<div class="view empty">${I.search}<h2>No results found for "${esc(q)}"</h2><div>Check the spelling, or try fewer or different words.</div></div>`;
      return;
    }

    const lower = q.toLowerCase();
    const topArtist = artists.find((a) => a.name.toLowerCase() === lower);
    const top = topArtist
      ? `<div class="top-result round" role="button" tabindex="0" data-action="open" data-kind="artist" data-id="${esc(topArtist.id)}">${art(topArtist.picture, '', I.person)}<h3>${esc(topArtist.name)}</h3><div class="t2">Artist</div></div>`
      : tracks[0]
        ? `<div class="top-result" role="button" tabindex="0" data-action="play-row" data-list="${registerList(tracks, { label: 'Search', title: q })}" data-i="0">${art(tracks[0].artwork)}<h3>${esc(tracks[0].title)}</h3><div class="t2">${esc(tracks[0].artist)}<span class="pill">Song</span></div><button class="play-fab" aria-label="Play">${I.play}</button></div>`
        : '';

    main.innerHTML = `<div class="view">
      <div class="chips" style="padding:16px 24px 0">
        ${[['all', 'All'], ['songs', 'Songs'], ['artists', 'Artists'], ['playlists', 'Playlists']].map(([id, label]) => `<button class="chip ${(r.tab || 'all') === id ? 'on' : ''}" data-action="search-tab" data-id="${id}">${label}</button>`).join('')}
      </div>
      ${(r.tab || 'all') === 'all' ? `
        <section class="section"><div class="search-top">
          <div><div class="section-head"><h2>Top result</h2></div>${top}</div>
          <div><div class="section-head"><h2>Songs</h2></div>${trackTable(tracks.slice(0, 4), { label: 'Search', title: q }, { compact: true, numbered: false })}</div>
        </div></section>
        ${artists.length ? shelf('Artists', artists.map((a) => card({ kind: 'artist', id: a.id, title: a.name, sub: 'Artist', artUrl: a.picture, round: true, play: false }))) : ''}
        ${playlists.length ? shelf('Playlists', playlists.map((p) => card({ kind: 'playlist', id: p.id, title: p.name, sub: `By ${p.owner}`, artUrl: p.artwork }))) : ''}
        ${tracks.length > 4 ? `<section class="section"><div class="section-head"><h2>More songs</h2></div></section>${trackTable(tracks.slice(4), { label: 'Search', title: q })}` : ''}
      ` : r.tab === 'songs' ? `<div style="height:16px"></div>${trackTable(tracks, { label: 'Search', title: q })}`
        : r.tab === 'artists' ? `<section class="section"><div class="shelf wrap">${artists.map((a) => card({ kind: 'artist', id: a.id, title: a.name, sub: 'Artist', artUrl: a.picture, round: true, play: false })).join('')}</div></section>`
          : `<section class="section"><div class="shelf wrap">${playlists.map((p) => card({ kind: 'playlist', id: p.id, title: p.name, sub: `By ${p.owner}`, artUrl: p.artwork })).join('')}</div></section>`}
    </div>`;
  },

  async playlist(r, main, alive) {
    main.innerHTML = heroSkeleton();
    const [p, tracks] = await Promise.all([audius.playlist(r.id), audius.playlistTracks(r.id)]);
    if (!alive()) return;
    if (!p) throw new Error("This playlist isn't on Audius any more.");
    recordVisit({ kind: 'playlist', id: p.id, title: p.name, sub: `Playlist · ${p.owner}`, art: p.artwork });
    const saved = lib.saved.some((s) => s.id === p.id);
    main.innerHTML = `<div class="view">
      ${hero({ kind: 'Playlist', title: p.name, artHtml: art(p.artwork, ''), sub: `${p.ownerId ? `<a href="#" data-action="artist" data-id="${esc(p.ownerId)}"><b>${esc(p.owner)}</b></a>` : `<b>${esc(p.owner)}</b>`} · ${plural(tracks.length, 'song')}, ${totalTime(tracks)}`, desc: p.description })}
      ${toolbar(tracks, { label: 'Playlist', title: p.name, kind: 'playlist', id: p.id }, `<button class="icon-btn big ${saved ? 'on' : ''}" data-action="save-playlist" aria-label="${saved ? 'Remove from' : 'Save to'} Your Library">${saved ? I.check : I.plusCircle}</button>`)}
      ${tracks.length ? trackTable(tracks, { label: 'Playlist', title: p.name, kind: 'playlist', id: p.id }) : emptyList('This playlist is empty')}
    </div>`;
    paintHeader(p.artwork);
    currentPage = { playlist: p };
  },

  async mine(r, main) {
    const p = lib.playlists.find((x) => x.id === r.id);
    if (!p) { go({ name: 'home' }, { replace: true }); return; }
    recordVisit({ kind: 'mine', id: p.id, title: p.name, sub: 'Playlist · You', art: p.tracks[0]?.artwork });
    main.innerHTML = `<div class="view">
      ${hero({ kind: 'Playlist', title: p.name, artHtml: mosaic(p.tracks), sub: `<b>You</b> · ${plural(p.tracks.length, 'song')}${p.tracks.length ? ', ' + totalTime(p.tracks) : ''}` })}
      ${toolbar(p.tracks, { label: 'Playlist', title: p.name, kind: 'mine', id: p.id }, `<button class="icon-btn" data-action="playlist-menu" aria-label="More options">${I.dots}</button>`)}
      ${p.tracks.length ? trackTable(p.tracks, { label: 'Playlist', title: p.name, kind: 'mine', id: p.id }, { playlistId: p.id }) : emptyList("Let's find something for your playlist", 'Search for songs, then right-click one and pick Add to playlist.')}
    </div>`;
    paintHeader(p.tracks[0]?.artwork, '#3a3f58');
  },

  async liked(r, main) {
    const tracks = lib.liked;
    main.innerHTML = `<div class="view">
      ${hero({ kind: 'Playlist', title: 'Liked Songs', artHtml: likedArt(), sub: `<b>You</b> · ${plural(tracks.length, 'song')}` })}
      ${toolbar(tracks, { label: 'Library', title: 'Liked Songs', kind: 'liked', id: 'liked' })}
      ${tracks.length ? trackTable(tracks, { label: 'Library', title: 'Liked Songs', kind: 'liked', id: 'liked' }) : emptyList('Songs you like will appear here', 'Save songs by tapping the plus next to them.')}
    </div>`;
    $('#main').style.setProperty('--header', '#4a2bd8');
  },

  async mix(r, main, alive) {
    main.innerHTML = heroSkeleton();
    const tracks = await audius.trending({ genre: r.id, limit: 50 });
    if (!alive()) return;
    const colour = genreColour(r.id);
    recordVisit({ kind: 'mix', id: r.id, title: `${r.id} Mix`, sub: 'Made for you', art: tracks[0]?.artwork });
    main.innerHTML = `<div class="view">
      ${hero({ kind: 'Mix', title: `${r.id} Mix`, artHtml: mosaic(tracks), sub: `Trending ${esc(r.id)} on Audius · ${plural(tracks.length, 'song')}, ${totalTime(tracks)}` })}
      ${toolbar(tracks, { label: 'Mix', title: `${r.id} Mix`, kind: 'mix', id: r.id })}
      ${tracks.length ? trackTable(tracks, { label: 'Mix', title: `${r.id} Mix`, kind: 'mix', id: r.id }) : emptyList('Nothing trending here right now')}
    </div>`;
    $('#main').style.setProperty('--header', colour);
  },

  async artist(r, main, alive) {
    main.innerHTML = heroSkeleton();
    const [a, tracks] = await Promise.all([audius.artist(r.id), audius.artistTracks(r.id)]);
    if (!alive()) return;
    if (!a) throw new Error("This artist isn't on Audius any more.");
    recordVisit({ kind: 'artist', id: a.id, title: a.name, sub: 'Artist', art: a.picture });
    const following = lib.following.some((x) => x.id === a.id);
    const ctx = { label: 'Artist', title: a.name, kind: 'artist', id: a.id };
    const popular = r.all ? tracks : tracks.slice(0, 10);
    main.innerHTML = `<div class="view">
      ${hero({ kind: 'Artist', title: a.name, artHtml: art(a.pictureLarge || a.picture, '', I.person), sub: `${plural(a.followers, 'follower')} · ${plural(tracks.length, 'song')}`, cls: 'artist' })}
      ${toolbar(tracks, ctx, `<button class="outline-btn" data-action="follow">${following ? 'Following' : 'Follow'}</button>`)}
      <section class="section" style="margin-top:0"><div class="section-head"><h2>Popular</h2></div></section>
      ${trackTable(popular, ctx)}
      ${tracks.length > 10 ? `<section class="section" style="margin-top:12px"><button class="more" data-action="artist-all">${r.all ? 'Show less' : `See all ${tracks.length} songs`}</button></section>` : ''}
      ${a.bio ? `<section class="section"><div class="section-head"><h2>About</h2></div><div class="about" style="max-width:720px;cursor:default"><div class="about-body"><p style="font-size:15px;white-space:pre-line">${esc(a.bio)}</p></div></div></section>` : ''}
    </div>`;
    paintHeader(a.picture);
    currentPage = { artist: a };
  },

  async library(r, main) {
    // Narrow windows hide the sidebar text, so the library gets its own page too.
    main.innerHTML = `<div class="view"><section class="section"><div class="section-head"><h2>Your Library</h2></div>
      <div class="shelf wrap">${libraryEntries().map((e) => card({ kind: e.kind, id: e.id, title: e.title, sub: e.sub, artUrl: e.art, round: e.kind === 'artist', play: e.kind !== 'artist' })).join('')}</div></section></div>`;
  },

  async settings(r, main) {
    const version = await appVersion();
    main.innerHTML = `<div class="view settings">
      <h1>Settings</h1>
      <h2>Theme</h2>
      <div class="swatches">${THEMES.map((t) => `<button class="swatch ${prefs.theme === t.id ? 'on' : ''}" data-action="theme" data-id="${t.id}"><i style="background:radial-gradient(circle at 35% 35%, ${t.accent} 55%, ${t.bg} 56%)"></i>${t.name}</button>`).join('')}</div>
      <h2>Playback</h2>
      <div class="setting-row"><div><div>Autoplay</div><div class="t2">Keep playing similar songs when your music ends.</div></div><button class="switch ${prefs.autoplay ? 'on' : ''}" data-action="toggle-autoplay" role="switch" aria-checked="${prefs.autoplay}" aria-label="Autoplay"></button></div>
      <h2>Your library</h2>
      <div class="setting-row"><div><div>Listening history</div><div class="t2">${plural(lib.history.length, 'song')} played on this computer.</div></div><button class="outline-btn" data-action="clear-history">Clear</button></div>
      <div class="setting-row"><div><div>Recent searches</div><div class="t2">${plural(lib.searches.length, 'search')}.</div></div><button class="outline-btn" data-action="clear-searches">Clear</button></div>
      <h2>About</h2>
      <p>Spitify ${esc(version)}. Music comes from <a href="https://audius.co" target="_blank" rel="noopener"><u>Audius</u></a>, lyrics from <a href="https://lrclib.net" target="_blank" rel="noopener"><u>LRCLIB</u></a>. Your likes and playlists are saved in this browser.</p>
      <p>Spitify for Android lives on the <a href="https://github.com/s-rakim/mobile-radio/releases/latest" target="_blank" rel="noopener"><u>releases page</u></a>.</p>
    </div>`;
  },
};
VIEWS.album = VIEWS.playlist;

let homeData = null;
let currentPage = {};

function shelf(title, cards, { list } = {}) {
  return `<section class="section"><div class="section-head"><h2>${esc(title)}</h2></div><div class="shelf" ${list ? `data-list="${list}"` : ''}>${cards.join('')}</div></section>`;
}

function trackCard(t, all, title) {
  const list = registerList(all, { label: 'Home', title });
  return `<div class="card" role="button" tabindex="0" data-action="play-row" data-list="${list}" data-i="${all.indexOf(t)}">
    ${art(t.artwork)}
    <button class="play-fab" aria-label="Play ${esc(t.title)}">${I.play}</button>
    <div class="t1">${esc(t.title)}</div>
    <div class="t2">${esc(t.artist)}</div>
  </div>`;
}

function mixCard(genre, colour, pool) {
  const cover = pool.find((t) => t.genre === genre)?.artwork;
  return `<div class="card" role="button" tabindex="0" data-action="open" data-kind="mix" data-id="${esc(genre)}">
    <div class="art" style="background:${colour}">${cover ? `<img src="${esc(cover)}" alt="" style="opacity:.55" class="loaded">` : ''}<span style="position:absolute;left:12px;bottom:10px;font-weight:900;font-size:20px;text-shadow:0 2px 10px rgba(0,0,0,.4)">${esc(genre)}</span><span style="position:absolute;inset:0 0 auto;height:6px;background:${colour}"></span></div>
    <button class="play-fab" data-action="play-card" aria-label="Play ${esc(genre)} Mix">${I.play}</button>
    <div class="t1">${esc(genre)} Mix</div>
    <div class="t2">Trending ${esc(genre)} picks, refreshed every week</div>
  </div>`;
}

function shortcutTiles() {
  const tiles = [];
  if (lib.liked.length || !lib.recent.length) tiles.push({ kind: 'liked', id: 'liked', title: 'Liked Songs', liked: true });
  for (const r of lib.recent) {
    if (tiles.length >= 8) break;
    tiles.push({ kind: r.kind, id: r.id, title: r.title, art: r.art });
  }
  for (const p of homeData?.lists || []) {
    if (tiles.length >= 8) break;
    if (!tiles.some((t) => t.kind === 'playlist' && t.id === p.id)) tiles.push({ kind: 'playlist', id: p.id, title: p.name, art: p.artwork });
  }
  return tiles.map((t) => `<div class="shortcut" role="button" tabindex="0" data-action="open" data-kind="${t.kind}" data-id="${esc(t.id)}">
    ${t.liked ? likedArt() : art(t.art, '', t.kind === 'artist' ? I.person : I.note)}
    <span>${esc(t.title)}</span>
    ${t.kind !== 'artist' ? `<button class="play-fab" data-action="play-card" aria-label="Play ${esc(t.title)}">${I.play}</button>` : ''}
  </div>`).join('');
}

function hero({ kind, title, artHtml, sub, desc, cls = '' }) {
  return `<div class="hero ${cls}">${artHtml}<div class="meta"><div class="kind">${esc(kind)}</div><h1>${esc(title)}</h1>${desc ? `<div class="sub" style="margin-bottom:8px;color:rgba(255,255,255,.7)">${esc(desc)}</div>` : ''}<div class="sub">${sub}</div></div></div>`;
}
const heroSkeleton = () => `<div class="view"><div class="hero"><div class="skeleton" style="width:232px;height:232px"></div><div class="meta" style="flex:1"><div class="skeleton" style="height:14px;width:80px"></div><div class="skeleton" style="height:64px;width:60%;margin:14px 0"></div><div class="skeleton" style="height:14px;width:40%"></div></div></div></div>`;

function toolbar(tracks, ctx, extra = '') {
  const list = registerList(tracks, ctx);
  const playingHere = player.context && ctx.kind && player.context.kind === ctx.kind && player.context.id === ctx.id;
  return `<div class="toolbar" data-list="${list}">
    <button class="play-fab" data-action="play-list" ${tracks.length ? '' : 'disabled'} aria-label="Play">${playingHere && player.playing ? I.pause : I.play}</button>
    <button class="icon-btn big ${prefs.shuffle ? 'on' : ''}" data-action="shuffle-list" aria-label="Shuffle play">${I.shuffle}</button>
    ${extra}
  </div>`;
}

const emptyList = (title, body = '') => `<div class="empty" style="padding:40px 24px">${I.note}<h2>${esc(title)}</h2><div>${esc(body)}</div></div>`;

const uniqueBy = (arr, key) => {
  const seen = new Set();
  return arr.filter((x) => { const k = key(x); if (seen.has(k)) return false; seen.add(k); return true; });
};

function rememberSearch(q) {
  lib.searches = [q, ...lib.searches.filter((s) => s.toLowerCase() !== q.toLowerCase())].slice(0, 12);
  saveLib('searches');
}

/* ================= sidebar ================= */

function libraryEntries() {
  const f = prefs.libFilter || 'all';
  const out = [];
  if (f === 'all' || f === 'playlists') {
    out.push({ kind: 'liked', id: 'liked', title: 'Liked Songs', sub: `Playlist · ${plural(lib.liked.length, 'song')}`, liked: true, pinned: true });
    for (const p of lib.playlists) out.push({ kind: 'mine', id: p.id, title: p.name, sub: 'Playlist · You', art: p.tracks[0]?.artwork, tracks: p.tracks });
    for (const p of lib.saved) out.push({ kind: 'playlist', id: p.id, title: p.name, sub: `Playlist · ${p.owner}`, art: p.artwork });
  }
  if (f === 'all' || f === 'artists') for (const a of lib.following) out.push({ kind: 'artist', id: a.id, title: a.name, sub: 'Artist', art: a.picture });
  return out;
}

function renderSidebar() {
  const r = route();
  const f = prefs.libFilter || 'all';
  $('#sidebar').innerHTML = `
    <div class="sidebar-head">
      <h2 data-action="go" data-route="library" title="Your Library">${I.library}<span>Your Library</span></h2>
      <button class="round-btn" data-action="new-playlist" title="Create playlist" aria-label="Create playlist">${I.plus}</button>
    </div>
    <div class="chips">${[['all', 'All'], ['playlists', 'Playlists'], ['artists', 'Artists']].map(([id, label]) => `<button class="chip ${f === id ? 'on' : ''}" data-action="lib-filter" data-id="${id}">${label}</button>`).join('')}</div>
    <div class="lib-list">
      ${libraryEntries().map((e) => {
        const active = r.name === e.kind && String(r.id ?? 'liked') === e.id;
        const playing = player.context?.kind === e.kind && player.context?.id === e.id;
        return `<button class="lib-item ${e.kind === 'artist' ? 'artist' : ''} ${active ? 'active' : ''} ${playing ? 'playing' : ''}" data-action="open" data-kind="${e.kind}" data-id="${esc(e.id)}" title="${esc(e.title)}">
          ${e.liked ? likedArt() : e.tracks ? mosaic(e.tracks) : art(e.art, '', e.kind === 'artist' ? I.person : I.note)}
          <div class="txt"><div class="t1">${esc(e.title)}</div><div class="t2">${e.pinned ? `<svg class="pin fill" viewBox="0 0 24 24"><path d="M15 3l6 6-3 1-4 4 1 5-2 2-4-4-5 5-1-1 5-5-4-4 2-2 5 1 4-4z"/></svg>` : ''}${esc(e.sub)}</div></div>
        </button>`;
      }).join('')}
      ${!lib.playlists.length && !lib.saved.length && !lib.following.length && f === 'all' ? `<div class="about" style="margin:12px 8px;cursor:default"><div class="about-body"><b>Create your first playlist</b><p>It's easy, we'll help you.</p><button class="pill-btn" style="height:32px;padding:0 16px;font-size:13px" data-action="new-playlist">Create playlist</button></div></div>` : ''}
    </div>`;
}

/* ================= player ================= */

const audio = $('#audio');
const player = {
  queue: [],        // tracks in play order
  order: [],        // original order, for turning shuffle off again
  index: -1,
  context: null,
  playing: false,
  get current() { return this.queue[this.index] || null; },
};
audio.volume = prefs.volume;

function playList(tracks, start = 0, context = null, { shuffle = prefs.shuffle } = {}) {
  if (!tracks.length) return;
  const first = tracks[start] || tracks[0];
  player.order = tracks.slice();
  player.context = context;
  if (shuffle) {
    const rest = tracks.filter((t) => t !== first);
    for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]]; }
    player.queue = [first, ...rest];
    player.index = 0;
  } else {
    player.queue = tracks.slice();
    player.index = tracks.indexOf(first);
  }
  load(true);
}

let triedDirect = false;
function load(autoplay) {
  const t = player.current;
  if (!t) return;
  triedDirect = Boolean(t.direct);
  audio.src = t.direct || audius.streamUrl(t.id);
  if (autoplay) audio.play().catch(() => {});
  lib.history = [slim(t), ...lib.history.filter((h) => h.id !== t.id)].slice(0, 100);
  saveLib('history');
  store.write('last', { queue: player.queue.map(slim), index: player.index, context: player.context });
  renderPlayer();
  renderSide();
  markPlaying();
  renderSidebar();
  updateMediaSession();
  if (t.artwork) artColour(t.artwork).then((c) => { if (player.current === t) document.documentElement.style.setProperty('--now', c || '#3a3a3a'); });
}

function toggle() {
  if (!player.current) {
    if (homeData?.week?.length) playList(homeData.week, 0, { label: 'Trending', title: 'Trending this week' });
    return;
  }
  if (audio.paused) audio.play().catch(() => {}); else audio.pause();
}

function next(auto = false) {
  if (!player.queue.length) return;
  if (auto && prefs.repeat === 'one') { audio.currentTime = 0; audio.play(); return; }
  if (player.index < player.queue.length - 1) { player.index++; load(true); return; }
  if (prefs.repeat === 'all') { player.index = 0; load(true); return; }
  if (auto && prefs.autoplay) { autoplayMore(); return; }
  if (!auto) { player.index = 0; load(false); }
}

function prev() {
  if (audio.currentTime > 3 || player.index === 0) { audio.currentTime = 0; return; }
  player.index--;
  load(true);
}

// When a list ends, keep going with trending songs from the same genre.
async function autoplayMore() {
  const last = player.current;
  try {
    const more = await audius.trending({ genre: last?.genre || undefined, limit: 30 });
    const fresh = more.filter((t) => !player.queue.some((q) => q.id === t.id));
    if (!fresh.length) return;
    player.queue.push(...fresh.slice(0, 15));
    player.index++;
    player.context = { label: 'Autoplay', title: `More ${last?.genre || 'music'} like this` };
    load(true);
  } catch { /* stay stopped */ }
}

function addToQueue(track) {
  if (!player.current) { playList([track], 0, null); return; }
  // "Add to queue" lands right after whatever you've already queued up.
  const at = player.index + 1 + (player.queuedAhead || 0);
  player.queue.splice(at, 0, track);
  player.queuedAhead = (player.queuedAhead || 0) + 1;
  toast('Added to queue');
  renderSide();
}

audio.addEventListener('play', () => { player.playing = true; renderPlayButtons(); });
audio.addEventListener('pause', () => { player.playing = false; renderPlayButtons(); });
audio.addEventListener('ended', () => { player.queuedAhead = Math.max(0, (player.queuedAhead || 0) - 1); next(true); });
audio.addEventListener('timeupdate', tick);
audio.addEventListener('loadedmetadata', tick);
audio.addEventListener('error', () => {
  const t = player.current;
  if (!t || !audio.src) return;
  // The direct node link can go stale. Fall back to the API's redirect before giving up.
  if (triedDirect) {
    triedDirect = false;
    audio.src = audius.streamUrl(t.id);
    audio.play().catch(() => {});
    return;
  }
  toast(navigator.onLine ? `Couldn't play ${t.title}` : "You're offline");
  if (navigator.onLine && player.index < player.queue.length - 1) setTimeout(() => next(), 800);
});

function setVolume(v) {
  audio.volume = Math.max(0, Math.min(1, v));
  audio.muted = false;
  prefs.volume = audio.volume;
  savePrefs();
  renderVolume();
}

function renderPlayer() {
  const t = player.current;
  const liked = t && isLiked(t.id);
  $('#player').innerHTML = `
    <div class="now">
      ${t ? `${art(t.artwork)}<div class="txt"><div class="t1"><a href="#" data-action="open-context">${esc(t.title)}</a></div><div class="t2">${t.artistId ? `<a href="#" data-action="artist" data-id="${esc(t.artistId)}">${esc(t.artist)}</a>` : esc(t.artist)}</div></div>
      <button class="icon-btn like-now ${liked ? 'on' : ''}" data-action="like-now" aria-label="${liked ? 'Remove from' : 'Save to'} Liked Songs">${liked ? I.check : I.plusCircle}</button>` : ''}
    </div>
    <div class="controls">
      <div class="buttons">
        <button class="icon-btn shuffle ${prefs.shuffle ? 'on' : ''}" data-action="shuffle" aria-label="${prefs.shuffle ? 'Turn shuffle off' : 'Turn shuffle on'}" title="Shuffle">${I.shuffle}</button>
        <button class="icon-btn" data-action="prev" aria-label="Previous" title="Previous">${I.prev}</button>
        <button class="play-btn" data-action="toggle" aria-label="Play">${I.play}</button>
        <button class="icon-btn" data-action="next" aria-label="Next" title="Next">${I.next}</button>
        <button class="icon-btn repeat ${prefs.repeat !== 'off' ? 'on' : ''}" data-action="repeat" aria-label="Repeat" title="${prefs.repeat === 'off' ? 'Repeat' : prefs.repeat === 'all' ? 'Repeat one' : 'Turn repeat off'}">${prefs.repeat === 'one' ? I.repeatOne : I.repeat}</button>
      </div>
      <div class="seek"><span id="pos">0:00</span><input class="range" id="seek" type="range" min="0" max="1000" value="0" aria-label="Seek"><span id="dur">${t ? fmtTime(t.duration) : '0:00'}</span></div>
    </div>
    <div class="extras">
      <button class="icon-btn ${prefs.sideOpen && prefs.side === 'now' ? 'on' : ''}" data-action="side" data-id="now" title="Now playing view" aria-label="Now playing view">${I.nowPlaying}</button>
      <button class="icon-btn ${prefs.sideOpen && prefs.side === 'lyrics' ? 'on' : ''}" data-action="side" data-id="lyrics" title="Lyrics" aria-label="Lyrics">${I.lyrics}</button>
      <button class="icon-btn ${prefs.sideOpen && prefs.side === 'queue' ? 'on' : ''}" data-action="side" data-id="queue" title="Queue" aria-label="Queue">${I.queue}</button>
      <button class="icon-btn vol" data-action="mute" aria-label="Mute" title="Mute"></button>
      <input class="range" id="volume" type="range" min="0" max="100" aria-label="Volume">
    </div>`;
  renderPlayButtons();
  renderVolume();
  tick();
  document.title = t ? `${t.title} · ${t.artist}` : 'Spitify';
}

function renderVolume() {
  const v = audio.muted ? 0 : audio.volume;
  const vol = $('#volume');
  if (vol) { vol.value = Math.round(v * 100); vol.style.setProperty('--pct', `${v * 100}%`); }
  const btn = $('.extras .vol');
  if (btn) btn.innerHTML = v === 0 ? I.mute : v < 0.5 ? I.volumeLow : I.volume;
}

function renderPlayButtons() {
  const playing = player.playing;
  const btn = $('.play-btn');
  if (btn) { btn.innerHTML = playing ? I.pause : I.play; btn.setAttribute('aria-label', playing ? 'Pause' : 'Play'); }
  const fab = $('.toolbar .play-fab');
  if (fab) {
    const list = lists.get($('.toolbar').dataset.list);
    const here = list && player.context && list.context.kind && player.context.kind === list.context.kind && player.context.id === list.context.id;
    fab.innerHTML = here && playing ? I.pause : I.play;
  }
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = playing ? 'playing' : 'paused';
  markPlaying();
}

let seeking = false;
function tick() {
  if (seeking) return;
  const dur = isFinite(audio.duration) && audio.duration > 0 ? audio.duration : player.current?.duration || 0;
  const pct = dur ? (audio.currentTime / dur) * 100 : 0;
  const s = $('#seek');
  if (s) { s.value = Math.round(pct * 10); s.style.setProperty('--pct', `${pct}%`); }
  const p = $('#pos'); if (p) p.textContent = fmtTime(audio.currentTime);
  const d = $('#dur'); if (d) d.textContent = fmtTime(dur);
  syncLyrics();
  if ('mediaSession' in navigator && dur && navigator.mediaSession.setPositionState) {
    try { navigator.mediaSession.setPositionState({ duration: dur, position: Math.min(audio.currentTime, dur), playbackRate: 1 }); } catch { /* ignore */ }
  }
}

function markPlaying() {
  const id = player.current?.id;
  document.querySelectorAll('.row').forEach((row) => {
    const on = row.dataset.id === id;
    row.classList.toggle('playing', on);
    const n = row.querySelector('.num .n');
    if (!n) return;
    if (on && !n.dataset.orig) n.dataset.orig = n.textContent || ' ';
    if (on) n.innerHTML = `<span class="bars ${player.playing ? '' : 'paused'}"><i></i><i></i><i></i></span>`;
    else if (n.dataset.orig) { n.textContent = n.dataset.orig.trim(); delete n.dataset.orig; }
  });
}

function refreshLikes() {
  document.querySelectorAll('.row').forEach((row) => {
    const btn = row.querySelector('.like');
    const on = isLiked(row.dataset.id);
    btn.classList.toggle('on', on);
    btn.innerHTML = on ? I.check : I.plusCircle;
  });
  renderPlayer();
  renderSide();
  renderSidebar();
  if (route().name === 'liked') render(false);
}

function updateMediaSession() {
  if (!('mediaSession' in navigator)) return;
  const t = player.current;
  if (!t) return;
  navigator.mediaSession.metadata = new MediaMetadata({
    title: t.title,
    artist: t.artist,
    album: player.context?.title || 'Spitify',
    artwork: [t.artwork && { src: t.artwork, sizes: '480x480' }, t.artworkLarge && { src: t.artworkLarge, sizes: '1000x1000' }].filter(Boolean),
  });
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => audio.play());
  ms.setActionHandler('pause', () => audio.pause());
  ms.setActionHandler('previoustrack', prev);
  ms.setActionHandler('nexttrack', () => next());
  try { ms.setActionHandler('seekto', (e) => { audio.currentTime = e.seekTime; }); } catch { /* older browsers */ }
}

/* ================= right panel ================= */

let lyricsFor = null;
let lyrics = null;

function renderSide() {
  const side = $('#side');
  const t = player.current;
  side.hidden = !prefs.sideOpen;
  side.classList.toggle('lyrics-mode', prefs.side === 'lyrics');
  document.querySelectorAll('.extras [data-action="side"]').forEach((b) => b.classList.toggle('on', Boolean(prefs.sideOpen && b.dataset.id === prefs.side)));
  if (!prefs.sideOpen) return;

  const head = (title) => `<div class="side-head"><h3>${esc(title)}</h3><button class="round-btn" data-action="close-side" aria-label="Close">${I.close}</button></div>`;

  if (prefs.side === 'queue') {
    const upcoming = player.queue.slice(player.index + 1);
    side.innerHTML = `${head('Queue')}<div class="side-body">
      ${t ? `<div class="q-label" style="margin-top:0">Now playing</div>${queueItem(t, player.index, true)}` : '<p style="color:var(--text-2)">Nothing playing yet.</p>'}
      ${upcoming.length ? `<div class="q-label">Next from: ${esc(player.context?.title || 'your queue')}</div>${upcoming.slice(0, 60).map((q, i) => queueItem(q, player.index + 1 + i)).join('')}` : ''}
    </div>`;
    return;
  }

  if (prefs.side === 'lyrics') {
    side.style.setProperty('--header', 'var(--now, #3a3a3a)');
    if (!t) { side.innerHTML = `${head('Lyrics')}<div class="side-body"><p>Play something to see its lyrics.</p></div>`; return; }
    if (lyricsFor !== t.id) {
      lyricsFor = t.id;
      lyrics = undefined;
      findLyrics(t).then((l) => { if (lyricsFor === t.id) { lyrics = l; renderSide(); } });
    }
    side.innerHTML = `${head(t.title)}<div class="side-body">${
      lyrics === undefined ? '<p style="color:rgba(255,255,255,.7)">Looking for lyrics…</p>'
        : !lyrics ? `<p style="color:rgba(255,255,255,.8)">No lyrics for this one yet.</p>`
          : `<div class="lyrics ${lyrics.synced ? '' : 'plain'}">${lyrics.lines.map((l, i) => `<p data-action="lyric" data-i="${i}">${esc(l.text || '♪')}</p>`).join('')}</div>`
    }</div>`;
    syncLyrics(true);
    return;
  }

  if (!t) { side.innerHTML = `${head('Now playing')}<div class="side-body"><p style="color:var(--text-2)">Pick a song to get started.</p></div>`; return; }
  const liked = isLiked(t.id);
  const upNext = player.queue[player.index + 1];
  side.innerHTML = `${head(player.context?.title || 'Now playing')}<div class="side-body">
    ${art(t.artworkLarge || t.artwork, 'np-art')}
    <div class="np-title"><div class="txt"><h2>${esc(t.title)}</h2><div class="t2">${t.artistId ? `<a href="#" data-action="artist" data-id="${esc(t.artistId)}">${esc(t.artist)}</a>` : esc(t.artist)}</div></div>
      <button class="icon-btn ${liked ? 'on' : ''}" data-action="like-now" aria-label="${liked ? 'Remove from' : 'Save to'} Liked Songs">${liked ? I.check : I.plusCircle}</button></div>
    ${t.artistId ? `<div class="about" data-action="artist" data-id="${esc(t.artistId)}"><div style="position:relative"><span class="about-label">About the artist</span>${art(t.artistImage, '', I.person)}</div><div class="about-body"><b>${esc(t.artist)}</b><p>See their top songs</p></div></div>` : ''}
    ${t.link ? `<div class="about" data-action="external" data-url="${esc(t.link)}"><div class="about-body"><b>On Audius</b><p>Open this song on audius.co to follow the artist or repost it.</p></div></div>` : ''}
    ${upNext ? `<div class="next-card"><div class="label">Next in queue<a href="#" data-action="side" data-id="queue">Open queue</a></div>${queueItem(upNext, player.index + 1)}</div>` : ''}
  </div>`;
}

function queueItem(t, i, now = false) {
  return `<div class="q-item ${now ? 'now' : ''}" role="button" tabindex="0" data-action="queue-jump" data-i="${i}">
    ${art(t.artwork)}<div class="txt"><div class="t1">${esc(t.title)}</div><div class="t2">${esc(t.artist)}</div></div>
    ${now ? '' : `<button class="x" data-action="queue-remove" data-i="${i}" aria-label="Remove from queue">${I.close}</button>`}
  </div>`;
}

let lastLine = -2;
function syncLyrics(force = false) {
  if (!prefs.sideOpen || prefs.side !== 'lyrics' || !lyrics?.synced) return;
  const ms = audio.currentTime * 1000 + 300;
  let line = -1;
  for (let i = 0; i < lyrics.lines.length; i++) if (lyrics.lines[i].time <= ms) line = i; else break;
  if (line === lastLine && !force) return;
  lastLine = line;
  const ps = document.querySelectorAll('.lyrics p');
  ps.forEach((p, i) => { p.classList.toggle('now', i === line); p.classList.toggle('past', i < line); });
  const el = ps[line];
  if (el) {
    const side = $('#side');
    const target = el.offsetTop - side.clientHeight / 3;
    side.scrollTo({ top: target, behavior: force ? 'auto' : 'smooth' });
  }
}

/* ================= menus, modals, toasts ================= */

let toastTimer;
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.hidden = false;
  el.style.animation = 'none';
  void el.offsetWidth;
  el.style.animation = '';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2600);
}

function openMenu(x, y, items) {
  const menu = $('#menu');
  menu.innerHTML = items.map((it) => (it === '-' ? '<hr>' : it.label && !it.run ? `<div class="menu-label">${esc(it.label)}</div>` : `<button data-menu="${items.indexOf(it)}">${it.icon || ''}${esc(it.text)}</button>`)).join('');
  menu.hidden = false;
  menu._items = items;
  const rect = menu.getBoundingClientRect();
  menu.style.left = `${Math.min(x, innerWidth - rect.width - 8)}px`;
  menu.style.top = `${Math.min(y, innerHeight - rect.height - 8)}px`;
  menu.querySelector('button')?.focus();
}
function closeMenu() { $('#menu').hidden = true; }

function trackMenu(track, x, y, { playlistId } = {}) {
  const items = [
    { text: 'Add to queue', icon: I.queue, run: () => addToQueue(track) },
    { text: isLiked(track.id) ? 'Remove from Liked Songs' : 'Save to Liked Songs', icon: isLiked(track.id) ? I.heartFill : I.heart, run: () => toggleLike(track) },
    '-',
    { label: 'Add to playlist' },
    { text: 'New playlist', icon: I.plus, run: () => newPlaylist(track) },
    ...lib.playlists.slice(0, 8).map((p) => ({ text: p.name, icon: I.note, run: () => addToPlaylist(p.id, track) })),
  ];
  if (playlistId) items.push('-', { text: 'Remove from this playlist', icon: I.trash, run: () => removeFromPlaylist(playlistId, track.id) });
  items.push('-');
  if (track.artistId) items.push({ text: 'Go to artist', icon: I.person, run: () => go({ name: 'artist', id: track.artistId }) });
  if (track.link) items.push({ text: 'Copy song link', icon: I.link, run: () => navigator.clipboard?.writeText(track.link).then(() => toast('Link copied')) });
  openMenu(x, y, items);
}

function modal(html, onOpen) {
  const m = $('#modal');
  m.innerHTML = `<div class="modal" role="dialog" aria-modal="true">${html}</div>`;
  m.hidden = false;
  onOpen?.(m);
}
function closeModal() { $('#modal').hidden = true; $('#modal').innerHTML = ''; }

function askName(title, initial, done) {
  modal(`<h2>${esc(title)}</h2><form><input name="name" maxlength="100" value="${esc(initial)}" aria-label="Playlist name"><div class="actions"><button type="button" class="ghost" data-action="modal-cancel">Cancel</button><button class="pill-btn" type="submit">Save</button></div></form>`, (m) => {
    const input = m.querySelector('input');
    input.focus();
    input.select();
    m.querySelector('form').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = input.value.trim();
      if (!name) return;
      closeModal();
      done(name);
    });
  });
}

function newPlaylist(withTrack = null) {
  askName('Create playlist', `My Playlist #${lib.playlists.length + 1}`, (name) => {
    const p = { id: 'p' + Date.now().toString(36), name, tracks: withTrack ? [slim(withTrack)] : [], created: Date.now() };
    lib.playlists.unshift(p);
    saveLib('playlists');
    if (withTrack) toast(`Added to ${name}`);
    go({ name: 'mine', id: p.id });
  });
}

function addToPlaylist(id, track) {
  const p = lib.playlists.find((x) => x.id === id);
  if (!p) return;
  if (p.tracks.some((t) => t.id === track.id)) { toast(`Already in ${p.name}`); return; }
  p.tracks.push(slim(track));
  saveLib('playlists');
  toast(`Added to ${p.name}`);
  renderSidebar();
}

function removeFromPlaylist(id, trackId) {
  const p = lib.playlists.find((x) => x.id === id);
  if (!p) return;
  p.tracks = p.tracks.filter((t) => t.id !== trackId);
  saveLib('playlists');
  toast(`Removed from ${p.name}`);
  render(false);
}

/* ================= events ================= */

function rowTrack(el) {
  const holder = el.closest('[data-list]');
  if (!holder) return null;
  const list = lists.get(holder.dataset.list);
  const i = +(el.closest('[data-i]')?.dataset.i ?? 0);
  return list ? { list, i, track: list.tracks[i] } : null;
}

function openKind(kind, id) {
  if (kind === 'liked') go({ name: 'liked' });
  else if (kind === 'mine') go({ name: 'mine', id });
  else if (kind === 'playlist') go({ name: 'playlist', id });
  else if (kind === 'artist') go({ name: 'artist', id });
  else if (kind === 'mix') go({ name: 'mix', id });
}

async function playKind(kind, id) {
  try {
    let tracks = [], ctx = { kind, id };
    if (kind === 'liked') { tracks = lib.liked; Object.assign(ctx, { label: 'Library', title: 'Liked Songs' }); }
    else if (kind === 'mine') { const p = lib.playlists.find((x) => x.id === id); tracks = p?.tracks || []; Object.assign(ctx, { label: 'Playlist', title: p?.name }); }
    else if (kind === 'playlist') { const [p, t] = await Promise.all([audius.playlist(id), audius.playlistTracks(id)]); tracks = t; Object.assign(ctx, { label: 'Playlist', title: p?.name || 'Playlist' }); }
    else if (kind === 'mix') { tracks = await audius.trending({ genre: id, limit: 50 }); Object.assign(ctx, { label: 'Mix', title: `${id} Mix` }); }
    else if (kind === 'artist') { tracks = await audius.artistTracks(id); Object.assign(ctx, { label: 'Artist', title: tracks[0]?.artist }); }
    if (!tracks.length) { toast('Nothing to play here yet'); return; }
    playList(tracks, 0, ctx);
  } catch {
    toast("Couldn't reach Audius");
  }
}

const ACTIONS = {
  back, forward,
  go: (el) => go({ name: el.dataset.route }),
  retry: () => render(false),
  open: (el) => openKind(el.dataset.kind, el.dataset.id),
  'play-card': (el, e) => { e.stopPropagation(); const c = el.closest('[data-kind]'); playKind(c.dataset.kind, c.dataset.id); },
  'play-row': (el, e) => {
    e.stopPropagation();
    const r = rowTrack(el);
    if (!r) return;
    if (player.current?.id === r.track.id) { toggle(); return; }
    playList(r.list.tracks, r.i, r.list.context, { shuffle: false });
  },
  'play-list': (el) => {
    const list = lists.get(el.closest('[data-list]').dataset.list);
    if (!list?.tracks.length) return;
    const here = player.context?.kind && player.context.kind === list.context.kind && player.context.id === list.context.id;
    if (here) toggle(); else playList(list.tracks, prefs.shuffle ? Math.floor(Math.random() * list.tracks.length) : 0, list.context);
  },
  'shuffle-list': (el) => {
    prefs.shuffle = !prefs.shuffle;
    savePrefs();
    el.classList.toggle('on', prefs.shuffle);
    renderPlayer();
  },
  'like-row': (el, e) => { e.stopPropagation(); const r = rowTrack(el); if (r) toggleLike(r.track); },
  'like-now': () => player.current && toggleLike(player.current),
  artist: (el, e) => { e.preventDefault(); e.stopPropagation(); go({ name: 'artist', id: el.dataset.id }); },
  'open-context': (el, e) => {
    e.preventDefault();
    const c = player.context;
    if (c?.kind) openKind(c.kind, c.id);
    else if (player.current?.artistId) go({ name: 'artist', id: player.current.artistId });
  },
  toggle, prev, next: () => next(),
  shuffle: () => { prefs.shuffle = !prefs.shuffle; savePrefs(); renderPlayer(); toast(prefs.shuffle ? 'Shuffle on' : 'Shuffle off'); },
  repeat: () => { prefs.repeat = { off: 'all', all: 'one', one: 'off' }[prefs.repeat]; savePrefs(); renderPlayer(); },
  mute: () => { audio.muted = !audio.muted; renderVolume(); },
  side: (el, e) => {
    e.preventDefault();
    const id = el.dataset.id;
    prefs.sideOpen = !(prefs.sideOpen && prefs.side === id);
    prefs.side = id;
    savePrefs();
    renderSide();
  },
  'close-side': () => { prefs.sideOpen = false; savePrefs(); renderSide(); },
  'queue-jump': (el) => { player.index = +el.dataset.i; player.queuedAhead = 0; load(true); },
  'queue-remove': (el, e) => { e.stopPropagation(); player.queue.splice(+el.dataset.i, 1); renderSide(); },
  lyric: (el) => { const l = lyrics?.lines[+el.dataset.i]; if (l?.time != null) audio.currentTime = l.time / 1000; },
  'home-filter': (el) => { prefs.filter = el.dataset.id; savePrefs(); render(false); },
  'lib-filter': (el) => { prefs.libFilter = el.dataset.id; savePrefs(); renderSidebar(); },
  'search-tab': (el) => go({ ...route(), tab: el.dataset.id }, { replace: true }),
  'search-again': (el) => { $('#search').value = el.dataset.q; go({ name: 'search', q: el.dataset.q }); },
  'forget-search': (el, e) => { e.stopPropagation(); lib.searches = lib.searches.filter((s) => s !== el.dataset.q); saveLib('searches'); render(false); },
  'clear-searches': () => { lib.searches = []; saveLib('searches'); render(false); },
  'clear-history': () => { lib.history = []; lib.recent = []; saveLib('history'); saveLib('recent'); toast('History cleared'); render(false); },
  'new-playlist': () => newPlaylist(),
  'save-playlist': (el) => {
    const p = currentPage.playlist;
    if (!p) return;
    const saved = lib.saved.some((s) => s.id === p.id);
    lib.saved = saved ? lib.saved.filter((s) => s.id !== p.id) : [{ id: p.id, name: p.name, owner: p.owner, artwork: p.artwork }, ...lib.saved];
    saveLib('saved');
    toast(saved ? 'Removed from Your Library' : 'Saved to Your Library');
    el.classList.toggle('on', !saved);
    el.innerHTML = saved ? I.plusCircle : I.check;
    renderSidebar();
  },
  follow: (el) => {
    const a = currentPage.artist;
    if (!a) return;
    const on = lib.following.some((x) => x.id === a.id);
    lib.following = on ? lib.following.filter((x) => x.id !== a.id) : [{ id: a.id, name: a.name, picture: a.picture }, ...lib.following];
    saveLib('following');
    el.textContent = on ? 'Follow' : 'Following';
    renderSidebar();
  },
  'artist-all': () => go({ ...route(), all: !route().all }, { replace: true }),
  'playlist-menu': (el) => {
    const r = el.getBoundingClientRect();
    const p = lib.playlists.find((x) => x.id === route().id);
    openMenu(r.left, r.bottom + 4, [
      { text: 'Rename', icon: I.pencil, run: () => askName('Rename playlist', p.name, (name) => { p.name = name; saveLib('playlists'); render(false); }) },
      { text: 'Delete', icon: I.trash, run: () => {
        lib.playlists = lib.playlists.filter((x) => x.id !== p.id);
        saveLib('playlists');
        toast(`Deleted ${p.name}`);
        go({ name: 'home' }, { replace: true });
      } },
    ]);
  },
  theme: (el) => { prefs.theme = el.dataset.id; savePrefs(); applyTheme(); render(false); },
  'toggle-autoplay': () => { prefs.autoplay = !prefs.autoplay; savePrefs(); render(false); },
  'modal-cancel': closeModal,
  external: (el) => window.open(el.dataset.url, '_blank', 'noopener'),
  install: async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice.catch(() => {});
    installPrompt = null;
    $('.install-btn').hidden = true;
  },
};

document.addEventListener('click', (e) => {
  const menuBtn = e.target.closest('[data-menu]');
  if (menuBtn) {
    const it = $('#menu')._items[+menuBtn.dataset.menu];
    closeMenu();
    it?.run?.();
    return;
  }
  if (!$('#menu').hidden && !e.target.closest('#menu')) closeMenu();
  if (e.target.id === 'modal') { closeModal(); return; }

  const el = e.target.closest('[data-action]');
  if (el) {
    const fn = ACTIONS[el.dataset.action];
    if (fn) { if (el.tagName === 'A') e.preventDefault(); fn(el, e); }
    return;
  }
  const row = e.target.closest('.row');
  if (row) {
    document.querySelectorAll('.row.selected').forEach((r) => r.classList.remove('selected'));
    row.classList.add('selected');
  }
});

// Double-click a song row to play it, like a desktop player.
document.addEventListener('dblclick', (e) => {
  const row = e.target.closest('.row');
  if (!row || e.target.closest('button, a')) return;
  const r = rowTrack(row);
  if (r) playList(r.list.tracks, r.i, r.list.context, { shuffle: false });
});

document.addEventListener('contextmenu', (e) => {
  const row = e.target.closest('.row, .q-item, .card[data-action="play-row"], .top-result[data-list]');
  if (row) {
    let track = null;
    if (row.classList.contains('q-item')) track = player.queue[+row.dataset.i];
    else track = rowTrack(row)?.track;
    if (!track) return;
    e.preventDefault();
    trackMenu(track, e.clientX, e.clientY, { playlistId: row.dataset.mine });
    return;
  }
  const owned = e.target.closest('[data-kind="mine"]');
  if (owned) {
    e.preventDefault();
    const p = lib.playlists.find((x) => x.id === owned.dataset.id);
    if (!p) return;
    openMenu(e.clientX, e.clientY, [
      { text: 'Play', icon: I.play, run: () => playKind('mine', p.id) },
      { text: 'Rename', icon: I.pencil, run: () => askName('Rename playlist', p.name, (name) => { p.name = name; saveLib('playlists'); render(false); }) },
      { text: 'Delete', icon: I.trash, run: () => { lib.playlists = lib.playlists.filter((x) => x.id !== p.id); saveLib('playlists'); toast(`Deleted ${p.name}`); if (route().id === p.id) go({ name: 'home' }, { replace: true }); else renderSidebar(); } },
    ]);
  }
});

document.addEventListener('input', (e) => {
  if (e.target.id === 'seek') {
    seeking = true;
    const dur = isFinite(audio.duration) ? audio.duration : player.current?.duration || 0;
    const pct = e.target.value / 10;
    e.target.style.setProperty('--pct', `${pct}%`);
    $('#pos').textContent = fmtTime((pct / 100) * dur);
  } else if (e.target.id === 'volume') {
    setVolume(e.target.value / 100);
  }
});
document.addEventListener('change', (e) => {
  if (e.target.id === 'seek') {
    const dur = isFinite(audio.duration) ? audio.duration : 0;
    if (dur) audio.currentTime = (e.target.value / 1000) * dur;
    seeking = false;
    tick();
  }
});

// Search as you type, with a short pause so every keystroke isn't a request.
let searchTimer;
$('#search').addEventListener('input', (e) => {
  clearTimeout(searchTimer);
  const q = e.target.value;
  searchTimer = setTimeout(() => {
    if (route().name === 'search') go({ name: 'search', q, tab: route().tab }, { replace: true });
    else go({ name: 'search', q });
  }, 280);
});
$('#search').addEventListener('focus', () => { if (route().name !== 'search') go({ name: 'search', q: $('#search').value }); });

document.addEventListener('keydown', (e) => {
  const typing = e.target.matches('input, textarea');
  if (e.key === 'Escape') {
    if (!$('#menu').hidden) { closeMenu(); return; }
    if (!$('#modal').hidden) { closeModal(); return; }
    if (typing) e.target.blur();
    return;
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); $('#search').focus(); $('#search').select(); return; }
  if (e.altKey && e.key === 'ArrowLeft') { e.preventDefault(); back(); return; }
  if (e.altKey && e.key === 'ArrowRight') { e.preventDefault(); forward(); return; }
  if (typing) return;
  if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[role="button"]')) { e.preventDefault(); e.target.click(); return; }
  if (e.key === ' ') { e.preventDefault(); toggle(); }
  else if (e.ctrlKey && e.key === 'ArrowRight') { e.preventDefault(); next(); }
  else if (e.ctrlKey && e.key === 'ArrowLeft') { e.preventDefault(); prev(); }
  else if (e.ctrlKey && e.key === 'ArrowUp') { e.preventDefault(); setVolume(audio.volume + 0.1); }
  else if (e.ctrlKey && e.key === 'ArrowDown') { e.preventDefault(); setVolume(audio.volume - 0.1); }
  else if (e.ctrlKey && e.key.toLowerCase() === 's') { e.preventDefault(); ACTIONS.shuffle(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'r') { e.preventDefault(); ACTIONS.repeat(); }
  else if (e.key === 'Enter' && e.target.closest('.row')) { const r = rowTrack(e.target); if (r) playList(r.list.tracks, r.i, r.list.context, { shuffle: false }); }
});

// Mouse back/forward buttons.
document.addEventListener('mouseup', (e) => {
  if (e.button === 3) back();
  if (e.button === 4) forward();
});

/* ================= install + version ================= */

let installPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  installPrompt = e;
  $('.install-btn').hidden = false;
});
window.addEventListener('appinstalled', () => { $('.install-btn').hidden = true; toast('Spitify is installed'); });

async function appVersion() {
  try {
    const res = await fetch('version.txt', { cache: 'no-store' });
    if (res.ok) return (await res.text()).trim();
  } catch { /* local copy */ }
  return 'web';
}

/* ================= start ================= */

// Covers fade in once they arrive and quietly drop out if they fail. Inline handlers are
// blocked by the content security policy, so this listens for every image at once.
document.addEventListener('load', (e) => { if (e.target.tagName === 'IMG') e.target.classList.add('loaded'); }, true);
document.addEventListener('error', (e) => { if (e.target.tagName === 'IMG' && e.target.closest('.art')) e.target.remove(); }, true);

applyTheme();
// Pick up where you left off, paused, so opening the app never blasts music.
const last = store.read('last', null);
if (last?.queue?.length) {
  player.queue = last.queue;
  player.order = last.queue.slice();
  player.index = Math.min(last.index || 0, last.queue.length - 1);
  player.context = last.context;
  audio.src = audius.streamUrl(player.current.id);
  audio.preload = 'none';
  updateMediaSession();
  if (player.current.artwork) artColour(player.current.artwork).then((c) => document.documentElement.style.setProperty('--now', c || '#3a3a3a'));
}
renderPlayer();
renderSidebar();
renderSide();
render(true);

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
