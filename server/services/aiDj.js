// AI Playlist and AI DJ.
//
// An agent digs through Audius the way a DJ digs through crates: it searches for
// genres, scenes, artists and moods, looks at tempo, key and energy, then hands
// back a shortlist with an energy rating for each pick. djTheory.js turns that
// shortlist into a running order that mixes cleanly and says how to blend each
// transition.
//
// Any chat model behind an OpenAI-style /chat/completions endpoint works (OpenAI,
// OpenRouter, Groq, Together, Mistral, DeepSeek, Gemini's compatible endpoint,
// a local Ollama...). Set these on the server:
//
//   AI_API_KEY    the provider's key
//   AI_MODEL      the model name your provider lists
//   AI_BASE_URL   defaults to https://api.openai.com/v1
//
// With no key set, the same routes still work on rules alone: keywords in the
// prompt choose the searches, and the sequencer does the rest.

const theory = require('./djTheory');

const AUDIUS = 'https://api.audius.co';
const AUDIUS_TIMEOUT = 6000;
const AI_TIMEOUT = 90_000;
const MAX_AGENT_TURNS = 8;
const MAX_POOL = 220;

const config = () => ({
  key: process.env.AI_API_KEY || '',
  model: process.env.AI_MODEL || '',
  base: (process.env.AI_BASE_URL || 'https://api.openai.com/v1').replace(/\/+$/, ''),
  temperature: process.env.AI_TEMPERATURE ? Number(process.env.AI_TEMPERATURE) : undefined,
});

function status() {
  const c = config();
  const enabled = Boolean(c.key && c.model);
  return {
    enabled: true, // rules mode always works
    agent: enabled,
    model: enabled ? c.model : null,
    note: enabled ? null : 'Set AI_API_KEY and AI_MODEL on the server to let the agent pick songs. Until then the rules-based DJ runs.',
  };
}

// ---- Audius ----

const enc = encodeURIComponent;
const audiusCache = new Map();

async function audius(path, params = {}) {
  const qs = new URLSearchParams({ ...params, app_name: 'spitify' }).toString();
  const url = `${AUDIUS}${path}?${qs}`;
  const hit = audiusCache.get(url);
  if (hit && Date.now() - hit.at < 10 * 60_000) return hit.data;
  const res = await fetch(url, { signal: AbortSignal.timeout(AUDIUS_TIMEOUT), headers: { 'User-Agent': 'spitify/1.0' } });
  if (!res.ok) throw new Error(`Audius said ${res.status}`);
  const data = (await res.json()).data;
  audiusCache.set(url, { at: Date.now(), data });
  if (audiusCache.size > 400) audiusCache.delete(audiusCache.keys().next().value);
  return data;
}

/** Audius track → what the DJ cares about. */
function features(t) {
  if (!t || typeof t !== 'object' || !t.id || t.is_streamable === false) return null;
  if (t.is_stream_gated || t.stream_conditions) return null; // pay-walled, the app can't play it
  const bpm = Number(t.bpm) || null;
  const item = {
    id: String(t.id),
    title: t.title || '',
    artist: t.user?.name || '',
    artistId: t.user?.id || '',
    genre: t.genre || null,
    mood: t.mood || null,
    tags: t.tags || '',
    bpm: bpm && bpm > 40 && bpm < 220 ? Math.round(bpm) : null,
    camelot: theory.camelot(t.musical_key || t.key),
    duration: t.duration || 0,
    plays: t.play_count || 0,
  };
  item.energy = theory.estimateEnergy(item);
  return item;
}

const toFeatures = (list) => (Array.isArray(list) ? list : []).map(features).filter(Boolean);

const searchTracks = async (query, limit = 25) =>
  toFeatures(await audius('/v1/tracks/search', { query: String(query).slice(0, 80), limit: Math.min(limit, 40) }));

const trendingTracks = async (genre, time = 'month') =>
  toFeatures(await audius('/v1/tracks/trending', { ...(genre ? { genre } : {}), time, limit: 50 }));

async function artistTracks(name) {
  const users = await audius('/v1/users/search', { query: String(name).slice(0, 60), limit: 3 });
  const user = (users || []).sort((a, b) => (b.follower_count || 0) - (a.follower_count || 0))[0];
  if (!user) return [];
  const tracks = await audius(`/v1/users/${enc(user.id)}/tracks`, { limit: 30, sort_method: 'plays', sort_direction: 'desc' })
    .catch(() => audius(`/v1/users/${enc(user.id)}/tracks`, { limit: 30 }));
  return toFeatures(tracks);
}

async function trackById(id) {
  try {
    return features(await audius(`/v1/tracks/${enc(id)}`));
  } catch {
    return null;
  }
}

// ---- The model ----

async function chat(messages, { tools, toolChoice } = {}) {
  const c = config();
  const body = { model: c.model, messages };
  if (tools) body.tools = tools;
  if (toolChoice) body.tool_choice = toolChoice;
  if (Number.isFinite(c.temperature)) body.temperature = c.temperature;
  const res = await fetch(`${c.base}/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${c.key}` },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(AI_TIMEOUT),
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(`Model error ${res.status}: ${text.slice(0, 300)}`);
    err.status = res.status;
    err.body = text;
    throw err;
  }
  const msg = JSON.parse(text).choices?.[0]?.message;
  if (!msg) throw new Error('The model sent back nothing');
  return msg;
}

/** Pulls a JSON object out of a reply that may have prose or code fences around it. */
function parseJson(text) {
  if (!text) return null;
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const raw = fenced ? fenced[1] : text;
  const start = raw.indexOf('{');
  const end = raw.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(raw.slice(start, end + 1));
  } catch {
    return null;
  }
}

const GENRES =
  'Electronic, House, Deep House, Tech House, Future House, Progressive House, Tropical House, Techno, Trance, Electro, Disco, ' +
  'Dubstep, Drum & Bass, Jungle, Future Bass, Glitch Hop, Hardstyle, Jersey Club, Moombahton, Trap, Hip-Hop/Rap, R&B/Soul, Funk, ' +
  'Pop, Hyperpop, Latin, Reggae, Dancehall, World, Rock, Alternative, Punk, Metal, Folk, Country, Blues, Acoustic, Jazz, Classical, ' +
  'Soundtrack, Ambient, Lo-Fi, Downtempo, Vaporwave, Experimental, Devotional';

const PERSONA = `You are a professional DJ with twenty years behind the decks: clubs, radio, weddings, warm-up sets and closing sets. You pick music for Spitify, which plays songs from Audius, an open catalogue of independent artists. You only ever pick songs that your search tools returned, by their id.

How you work:
- Dig properly. Run several different searches: genres and sub-genres, scenes, moods, activities, artist names the listener mentioned and artists who sound like them. If results are thin or off, search again with other words.
- Read the room from the request: who is listening, where, what time of day, what they're doing.
- Rate each pick's energy from 1 (sleepy) to 10 (peak-time) from its genre, mood, tempo and title. Be honest; most songs sit between 3 and 8.
- Tempo matters: songs that sit next to each other should be within about 6% BPM, or at half or double time.
- Keys matter: favour moves of one step on the Camelot wheel, the same key, or the relative major/minor.
- Variety matters: no more than two songs by one artist, and spread them out.
- Shape the set: the opener sets the tone, build gradually, place the peak about two thirds in, and leave on something memorable. Pick the energy arc that suits the request: journey, build, peak, steady, wind-down or wave.
- Prefer songs people actually play (play counts help) but leave room for a couple of discoveries.
- Shortlist about one and a half times as many songs as asked for; the sequencing engine picks the final running order from your shortlist, so give it options at each energy level.
- When you talk, sound like a real radio DJ: warm, short, specific, no emojis, no hype words like "ultimate" or "vibes curated". One or two sentences.`;

const TOOLS = [
  {
    type: 'function',
    function: {
      name: 'search_tracks',
      description: 'Search Audius for songs by any words: genre, scene, mood, activity, artist or title. Returns id | title | artist | genre | mood | bpm | key | energy (0-1 estimate) | plays.',
      parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'] },
    },
  },
  {
    type: 'function',
    function: {
      name: 'trending_tracks',
      description: `Songs trending on Audius, optionally in one genre. Genres: ${GENRES}.`,
      parameters: {
        type: 'object',
        properties: { genre: { type: 'string' }, time: { type: 'string', enum: ['week', 'month', 'year', 'allTime'] } },
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'artist_tracks',
      description: "An Audius artist's most played songs, found by name.",
      parameters: { type: 'object', properties: { name: { type: 'string' } }, required: ['name'] },
    },
  },
  {
    type: 'function',
    function: {
      name: 'submit_set',
      description: 'Hand over the shortlist. Only ids returned by the search tools count.',
      parameters: {
        type: 'object',
        properties: {
          title: { type: 'string', description: 'Playlist name, short, like a real one (no quotes, no emojis).' },
          description: { type: 'string', description: 'One sentence about the set.' },
          arc: { type: 'string', enum: Object.keys(theory.ARCS) },
          level: { type: 'number', description: 'Overall energy 1-10.' },
          intro: { type: 'string', description: 'What you would say on air before the first song.' },
          picks: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                id: { type: 'string' },
                energy: { type: 'number', description: '1-10' },
                fit: { type: 'number', description: 'How well it suits the request, 1-10' },
                note: { type: 'string', description: 'Optional few words on why.' },
              },
              required: ['id', 'energy'],
            },
          },
        },
        required: ['picks'],
      },
    },
  },
];

function table(items) {
  if (!items.length) return 'No results.';
  return items
    .slice(0, 30)
    .map((t) => [t.id, t.title, t.artist, t.genre || '-', t.mood || '-', t.bpm || '-', t.camelot || '-', t.energy.toFixed(2), t.plays].join(' | '))
    .join('\n');
}

async function runTool(name, args, pool, skip) {
  let found = [];
  try {
    if (name === 'search_tracks') found = await searchTracks(args.query || '');
    else if (name === 'trending_tracks') found = await trendingTracks(args.genre, args.time || 'month');
    else if (name === 'artist_tracks') found = await artistTracks(args.name || '');
    else return 'Unknown tool.';
  } catch (e) {
    return `That search failed (${e.message}). Try different words.`;
  }
  found = found.filter((t) => !skip.has(t.id));
  for (const t of found) if (pool.size < MAX_POOL) pool.set(t.id, t);
  return table(found);
}

/**
 * The agent loop: let the model search until it submits a shortlist. Falls back to a plain
 * JSON exchange for providers that don't support tool calls.
 */
async function agentPick(task, skip, seed = []) {
  const pool = new Map(seed.map((t) => [t.id, t]));
  const messages = [{ role: 'system', content: PERSONA }, { role: 'user', content: task }];
  let submitted = null;
  try {
    for (let turn = 0; turn < MAX_AGENT_TURNS && !submitted; turn++) {
      const last = turn === MAX_AGENT_TURNS - 1;
      const msg = await chat(messages, {
        tools: TOOLS,
        toolChoice: last ? { type: 'function', function: { name: 'submit_set' } } : 'auto',
      });
      messages.push({ role: 'assistant', content: msg.content ?? null, ...(msg.tool_calls ? { tool_calls: msg.tool_calls } : {}) });
      const calls = msg.tool_calls || [];
      if (!calls.length) {
        // Some models answer in text even with tools on. Take a JSON shortlist if there is one.
        const parsed = parseJson(msg.content);
        if (parsed?.picks) submitted = parsed;
        else messages.push({ role: 'user', content: 'Use the tools to search, then call submit_set.' });
        continue;
      }
      for (const call of calls) {
        let args = {};
        try {
          args = JSON.parse(call.function?.arguments || '{}');
        } catch {}
        if (call.function?.name === 'submit_set') {
          submitted = args;
          messages.push({ role: 'tool', tool_call_id: call.id, content: 'Got it.' });
        } else {
          const out = await runTool(call.function?.name, args, pool, skip);
          messages.push({ role: 'tool', tool_call_id: call.id, content: out });
        }
      }
    }
  } catch (e) {
    if (e.status >= 400 && e.status < 500 && /tool/i.test(e.body || '')) return jsonPick(task, skip, pool);
    throw e;
  }
  return { plan: submitted || {}, pool };
}

/** For models without tool calls: we do the digging from a plan the model writes, then it picks. */
async function jsonPick(task, skip, pool) {
  const planMsg = await chat([
    { role: 'system', content: PERSONA },
    {
      role: 'user',
      content: `${task}\n\nFirst plan the dig. Reply with JSON only: {"searches": [up to 6 search phrases], "genres": [up to 3 of: ${GENRES}], "artists": [up to 3 names]}`,
    },
  ]);
  const plan = parseJson(planMsg.content) || {};
  await gather(plan, pool, skip);
  const pickMsg = await chat([
    { role: 'system', content: PERSONA },
    {
      role: 'user',
      content: `${task}\n\nHere is what the crates turned up (id | title | artist | genre | mood | bpm | key | energy | plays):\n${table([...pool.values()].slice(0, 120))}\n\nReply with JSON only: {"title": "", "description": "", "arc": "journey|build|peak|steady|wind-down|wave", "level": 1-10, "intro": "", "picks": [{"id": "", "energy": 1-10, "fit": 1-10}]}`,
    },
  ]);
  return { plan: parseJson(pickMsg.content) || {}, pool };
}

async function gather(plan, pool, skip) {
  const jobs = [
    ...(plan.searches || []).slice(0, 6).map((q) => searchTracks(q)),
    ...(plan.genres || []).slice(0, 3).map((g) => trendingTracks(g)),
    ...(plan.artists || []).slice(0, 3).map((a) => artistTracks(a)),
  ];
  if (!jobs.length) jobs.push(trendingTracks(null, 'week'));
  const results = await Promise.allSettled(jobs);
  for (const r of results) {
    if (r.status !== 'fulfilled') continue;
    for (const t of r.value) if (!skip.has(t.id) && pool.size < MAX_POOL) pool.set(t.id, t);
  }
}

// ---- Rules mode (no model) ----

const WORDS = [
  [/hip ?hop|rap|bars/, { genres: ['Hip-Hop/Rap'] }],
  [/trap|drill/, { genres: ['Trap'], searches: ['drill'] }],
  [/edm|electronic|electro|synth/, { genres: ['Electronic'] }],
  [/house|amapiano/, { genres: ['House', 'Deep House'], searches: ['amapiano'] }],
  [/techno|rave|warehouse/, { genres: ['Techno'], arc: 'peak' }],
  [/dnb|drum and bass|drum & bass|jungle/, { genres: ['Drum & Bass'] }],
  [/dubstep|riddim|bass music/, { genres: ['Dubstep'] }],
  [/pop|catchy/, { genres: ['Pop'] }],
  [/r&b|rnb|soul|slow jam/, { genres: ['R&B/Soul'] }],
  [/lo-?fi|study|focus|homework|coding|reading/, { genres: ['Lo-Fi'], arc: 'steady', level: 0.3 }],
  [/ambient|calm|relax|meditat|spa|peaceful/, { genres: ['Ambient'], arc: 'steady', level: 0.2 }],
  [/sleep|bedtime|wind down/, { genres: ['Ambient', 'Lo-Fi'], arc: 'wind-down', level: 0.2 }],
  [/jazz|coffee|cafe/, { genres: ['Jazz'], arc: 'steady', level: 0.4 }],
  [/latin|reggaeton|salsa|bachata/, { genres: ['Latin'], searches: ['reggaeton'] }],
  [/afro|naija|afrobeat/, { searches: ['afrobeats', 'afropop'] }],
  [/rock|guitar|punk|grunge/, { genres: ['Rock', 'Alternative'] }],
  [/metal/, { genres: ['Metal'], level: 0.85 }],
  [/indie|alternative/, { genres: ['Alternative'] }],
  [/acoustic|unplugged|folk/, { genres: ['Acoustic', 'Folk'], level: 0.35 }],
  [/reggae|dancehall/, { genres: ['Reggae', 'Dancehall'] }],
  [/disco|funk|groove/, { genres: ['Disco', 'Funk'] }],
  [/classical|piano|orchestra/, { genres: ['Classical'], arc: 'steady', level: 0.3 }],
  [/cinematic|epic|soundtrack|film/, { genres: ['Soundtrack'], arc: 'build' }],
  [/gym|workout|run|cardio|lift|hype/, { genres: ['Hip-Hop/Rap', 'Electronic'], searches: ['workout'], arc: 'peak', level: 0.8 }],
  [/party|club|pregame|turn up|dance/, { genres: ['House', 'Hip-Hop/Rap'], searches: ['party'], arc: 'build', level: 0.75 }],
  [/sad|heartbreak|breakup|cry|lonely/, { genres: ['R&B/Soul', 'Alternative'], searches: ['sad'], arc: 'steady', level: 0.3 }],
  [/happy|sunny|summer|feel good|upbeat/, { genres: ['Pop', 'Funk'], searches: ['summer'], level: 0.65 }],
  [/love|romantic|date night/, { genres: ['R&B/Soul'], searches: ['love songs'], level: 0.4 }],
  [/road trip|drive|driving/, { genres: ['Pop', 'Rock'], searches: ['road trip'], arc: 'journey' }],
  [/morning|wake up|breakfast/, { genres: ['Acoustic', 'Pop'], arc: 'build', level: 0.45 }],
  [/rain|cozy|autumn/, { genres: ['Lo-Fi', 'Jazz'], arc: 'steady', level: 0.35 }],
  [/gaming|game/, { genres: ['Electronic', 'Dubstep'], searches: ['gaming'], level: 0.75 }],
  [/gospel|worship|church/, { searches: ['gospel', 'worship'] }],
];

function rulesPlan(prompt) {
  const text = String(prompt || '').toLowerCase();
  const plan = { searches: [], genres: [], artists: [], arc: 'journey', level: 0.5 };
  for (const [re, hint] of WORDS) {
    if (!re.test(text)) continue;
    plan.genres.push(...(hint.genres || []));
    plan.searches.push(...(hint.searches || []));
    if (hint.arc) plan.arc = hint.arc;
    if (hint.level != null) plan.level = hint.level;
  }
  const like = text.match(/(?:like|by|similar to|sounds like|inspired by) ([a-z0-9&' .-]{2,40})/);
  if (like) plan.artists.push(like[1].split(/ and | but | for | with /)[0].trim());
  if (!plan.genres.length && !plan.searches.length && !plan.artists.length && text.trim()) plan.searches.push(text.trim().slice(0, 60));
  plan.genres = [...new Set(plan.genres)].slice(0, 3);
  plan.searches = [...new Set(plan.searches)].slice(0, 4);
  return plan;
}

// ---- Putting a set together ----

function finish(plan, pool, { size, start = null, direction = 0 }) {
  const picks = Array.isArray(plan.picks) && plan.picks.length
    ? plan.picks
        .map((p) => {
          const t = pool.get(String(p.id));
          if (!t) return null;
          const rated = Number(p.energy);
          const energy = Number.isFinite(rated) ? theory.clamp((0.65 * rated) / 10 + 0.35 * t.energy, 0.05, 0.98) : t.energy;
          const fit = Number.isFinite(Number(p.fit)) ? theory.clamp(Number(p.fit) / 10) : 0.8;
          return { ...t, energy, fit, note: p.note || null };
        })
        .filter(Boolean)
    : [...pool.values()].map((t) => ({ ...t, fit: 0.5 + Math.min(0.3, Math.log10((t.plays || 0) + 1) / 20) }));

  // Two songs per artist at most, before sequencing.
  const perArtist = new Map();
  const capped = picks.filter((t) => {
    const k = (t.artist || '').toLowerCase();
    const n = perArtist.get(k) || 0;
    perArtist.set(k, n + 1);
    return n < 2;
  });

  const levelRaw = Number(plan.level);
  const level = Number.isFinite(levelRaw) ? (levelRaw > 1 ? levelRaw / 10 : levelRaw) : 0.5;
  const ordered = theory.sequence(capped, { size, arc: plan.arc, level, start, direction });
  return ordered.map((t, i) => {
    const prev = i === 0 ? start : ordered[i - 1];
    return {
      id: t.id,
      title: t.title,
      artist: t.artist,
      genre: t.genre,
      bpm: t.bpm,
      key: t.camelot,
      energy: Number(t.energy.toFixed(2)),
      note: t.note || null,
      mixIn: prev ? theory.mixNote(prev, t) : null,
    };
  });
}

function clampSize(n, fallback) {
  const v = Number(n);
  return Number.isFinite(v) ? Math.max(5, Math.min(100, Math.round(v))) : fallback;
}

/** A playlist from a prompt. */
async function playlist({ prompt, size, skip } = {}) {
  const text = String(prompt || '').trim().slice(0, 400);
  if (!text) {
    const e = new Error('Tell the DJ what you want to hear');
    e.status = 400;
    throw e;
  }
  const n = clampSize(size, 30);
  const skipSet = new Set((skip || []).map(String));
  const c = config();

  if (c.key && c.model) {
    try {
      const task = `Make a ${n}-song playlist for this request: "${text}". Shortlist about ${Math.round(n * 1.5)} songs.`;
      const { plan, pool } = await agentPick(task, skipSet);
      const tracks = finish(plan, pool, { size: n });
      if (tracks.length >= Math.min(5, n)) {
        return {
          engine: 'agent',
          title: (plan.title || text).slice(0, 60),
          description: plan.description || `Made from "${text}"`,
          intro: plan.intro || null,
          arc: plan.arc || 'journey',
          tracks,
        };
      }
    } catch (e) {
      console.warn('AI playlist agent failed, using rules:', e.message);
    }
  }

  const plan = rulesPlan(text);
  const pool = new Map();
  await gather(plan, pool, skipSet);
  return {
    engine: 'rules',
    title: text.charAt(0).toUpperCase() + text.slice(1, 40),
    description: `Made from "${text}"`,
    intro: null,
    arc: plan.arc,
    tracks: finish(plan, pool, { size: n }),
  };
}

/** Which way the listener wants the energy to go, from words like "turn it up". */
function directionOf(request) {
  const r = String(request || '').toLowerCase();
  if (/more energy|turn it up|hype|harder|faster|pick it up|party|banger/.test(r)) return 1;
  if (/chill|calm|slow|softer|wind down|relax|mellow|sleep/.test(r)) return -1;
  return 0;
}

/**
 * The DJ: keeps a set going from what's playing now. Picks the next [count] songs so the
 * first one mixes out of the current song, and says something on air when it switches.
 */
async function dj({ current, history, request, count, skip } = {}) {
  const n = clampSize(count, 8);
  const skipSet = new Set([...(skip || []), ...(history || []).map((h) => h?.id)].filter(Boolean).map(String));
  if (current?.id) skipSet.add(String(current.id));
  const direction = directionOf(request);

  // Tempo and key of the song playing, from Audius when it's one of theirs.
  let start = current?.id ? await trackById(String(current.id)) : null;
  if (!start && current) {
    start = { id: String(current.id || 'now'), title: current.title || '', artist: current.artist || '', genre: current.genre || null, bpm: null, camelot: null, tags: '' };
    start.energy = theory.estimateEnergy(start);
  }
  const recent = (history || []).slice(-15).map((h) => `${h.title} - ${h.artist}${h.genre ? ` (${h.genre})` : ''}`).join('\n');
  const c = config();

  if (c.key && c.model) {
    try {
      const task = [
        `You're live, keeping a DJ set going. Pick the next ${n} songs. Shortlist about ${Math.round(n * 1.8)}.`,
        start ? `Playing now: ${start.title} by ${start.artist} | ${start.genre || '?'} | ${start.bpm || '?'} BPM | key ${start.camelot || '?'} | energy ${start.energy.toFixed(2)}. The first pick has to mix out of this.` : 'Nothing is playing yet; open the set.',
        recent ? `Recently played, don't repeat these:\n${recent}` : '',
        request ? `The listener just asked: "${String(request).slice(0, 200)}"` : 'No request; read the room from what has been playing and keep it flowing, with a little lift now and then.',
        'In the intro field, say what you would on air going into the next song: one or two short sentences, mention the next artist naturally.',
      ].filter(Boolean).join('\n\n');
      const { plan, pool } = await agentPick(task, skipSet);
      const tracks = finish(plan, pool, { size: n, start, direction });
      if (tracks.length) return { engine: 'agent', intro: plan.intro || null, tracks };
    } catch (e) {
      console.warn('AI DJ agent failed, using rules:', e.message);
    }
  }

  const pool = new Map();
  const plan = rulesPlan(request || '');
  if (start?.genre) plan.genres.unshift(start.genre);
  if (start?.artist) plan.searches.push(start.artist);
  plan.genres = [...new Set(plan.genres)].slice(0, 3);
  await gather(plan, pool, skipSet);
  const tracks = finish({ ...plan, picks: null }, pool, { size: n, start, direction });
  const next = tracks[0];
  return {
    engine: 'rules',
    intro: next ? `Up next, ${next.artist} with ${next.title}.` : null,
    tracks,
  };
}

module.exports = { status, playlist, dj, rulesPlan, finish, features };
