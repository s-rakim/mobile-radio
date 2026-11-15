// The music side of the DJ: keys on the Camelot wheel, a rough energy reading for
// each song, and a sequencer that puts a pile of picks into an order that mixes
// well. It's the same rulebook a working DJ carries around in their head:
//
//  - keep the tempo within a few percent from one song to the next (or jump to
//    half or double time, which still lines up on the beat)
//  - move around the Camelot wheel one step at a time, or stay put
//  - shape the energy over the set instead of playing every banger at once
//  - don't play the same artist twice in a row, and don't lurch between genres
//
// No AI in here. The agent picks the songs; this decides the running order and
// how each transition should be mixed. It also runs on its own when no model is
// configured.

// ---- Keys ----

const MAJOR = { B: 1, 'F#': 2, Gb: 2, Db: 3, 'C#': 3, Ab: 4, 'G#': 4, Eb: 5, 'D#': 5, Bb: 6, 'A#': 6, F: 7, C: 8, G: 9, D: 10, A: 11, E: 12 };
const MINOR = { Ab: 1, 'G#': 1, Eb: 2, 'D#': 2, Bb: 3, 'A#': 3, F: 4, C: 5, G: 6, D: 7, A: 8, E: 9, B: 10, 'F#': 11, Gb: 11, Db: 12, 'C#': 12 };

/** "A minor", "Am", "F#maj", "C♯ minor", "8A" → "8A". Unknown → null. */
function camelot(key) {
  if (!key) return null;
  const s = String(key).trim().replace(/♯/g, '#').replace(/♭/g, 'b');
  const wheel = s.match(/^(1[0-2]|[1-9])\s*([ABab])$/);
  if (wheel) return `${wheel[1]}${wheel[2].toUpperCase()}`;
  const m = s.match(/^([A-Ga-g])([#b]?)\s*(.*)$/);
  if (!m) return null;
  const note = m[1].toUpperCase() + m[2];
  const rest = m[3].toLowerCase();
  const minor = /^(m(?!aj)|min|minor)/.test(rest);
  const n = (minor ? MINOR : MAJOR)[note];
  return n ? `${n}${minor ? 'A' : 'B'}` : null;
}

/** 0 = perfect, ~1 = clashes. Unknown keys sit in the middle. */
function keyCost(a, b) {
  if (!a || !b) return 0.35;
  const na = parseInt(a, 10), nb = parseInt(b, 10);
  const la = a.slice(-1), lb = b.slice(-1);
  const step = Math.min((na - nb + 12) % 12, (nb - na + 12) % 12);
  if (na === nb && la === lb) return 0;
  if (la === lb && step === 1) return 0.1; // next door on the wheel
  if (na === nb) return 0.2; // relative major / minor
  if (la === lb && step === 2) return 0.55; // the "energy boost" jump, use sparingly
  if (la !== lb && step === 1) return 0.6; // diagonal
  return 1.2;
}

// ---- Tempo ----

/** How far apart two tempos are in percent, allowing half and double time. */
function bpmGap(a, b) {
  if (!a || !b) return null;
  const options = [b / a, (b * 2) / a, b / (a * 2)];
  return Math.min(...options.map((r) => Math.abs(Math.log(r)))) * 100;
}

function bpmCost(a, b) {
  const gap = bpmGap(a, b);
  if (gap == null) return 0.35;
  if (gap <= 3) return 0;
  if (gap <= 6) return 0.3;
  if (gap <= 10) return 0.9;
  return 2;
}

// ---- Energy ----

const GENRE_ENERGY = {
  Audiobooks: 0.1, Podcasts: 0.1, 'Spoken Word': 0.2, Ambient: 0.2, Devotional: 0.3, Classical: 0.3, 'Lo-Fi': 0.3,
  Comedy: 0.3, Acoustic: 0.35, Folk: 0.35, Downtempo: 0.38, Jazz: 0.4, Vaporwave: 0.4, Soundtrack: 0.45, Blues: 0.45,
  Country: 0.5, 'R&B/Soul': 0.5, Reggae: 0.5, World: 0.5, Experimental: 0.5, Kids: 0.5, Alternative: 0.55,
  'Tropical House': 0.6, Pop: 0.62, Funk: 0.65, Disco: 0.68, 'Hip-Hop/Rap': 0.66, Latin: 0.68, 'Deep House': 0.68,
  Dancehall: 0.7, Rock: 0.7, Hyperpop: 0.75, Electronic: 0.74, 'Future Bass': 0.76, House: 0.76, 'Future House': 0.78,
  Moombahton: 0.78, 'Glitch Hop': 0.78, 'Progressive House': 0.78, 'Tech House': 0.8, Electro: 0.8, Trap: 0.8,
  Trance: 0.82, Techno: 0.84, Punk: 0.85, 'Jersey Club': 0.85, Jungle: 0.86, Metal: 0.88, 'Drum & Bass': 0.9,
  Dubstep: 0.9, Hardstyle: 0.95,
};

const MOOD_SHIFT = {
  Peaceful: -0.15, Tender: -0.12, Melancholy: -0.12, Sentimental: -0.1, Romantic: -0.08, Easygoing: -0.08,
  Yearning: -0.06, Sensual: -0.06, Sophisticated: -0.04, Brooding: -0.04, Cool: 0, Serious: 0, Gritty: 0.05,
  Stirring: 0.06, Defiant: 0.08, Empowering: 0.08, Upbeat: 0.1, Fiery: 0.12, Rowdy: 0.12, Excited: 0.12,
  Energizing: 0.15, Aggressive: 0.15,
};

const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));

/** A rough 0..1 energy from what the catalogue tells us. The agent's own rating wins when it gives one. */
function estimateEnergy(t) {
  let e = GENRE_ENERGY[t.genre] ?? 0.55;
  e += MOOD_SHIFT[t.mood] ?? 0;
  if (t.bpm) e += clamp((t.bpm - 110) / 200, -0.15, 0.2);
  const words = `${t.title} ${t.tags || ''}`.toLowerCase();
  if (/\b(chill|relax|sleep|calm|lofi|lo-fi|acoustic|piano|ambient)\b/.test(words)) e -= 0.1;
  if (/\b(hype|banger|rage|club|festival|drop|turn up|bounce|vip)\b/.test(words)) e += 0.08;
  return clamp(e, 0.05, 0.98);
}

// ---- Genres ----

const FAMILIES = [
  ['Electronic', 'House', 'Deep House', 'Tech House', 'Future House', 'Progressive House', 'Tropical House', 'Techno', 'Trance', 'Electro', 'Disco', 'Jersey Club', 'Moombahton'],
  ['Electronic', 'Dubstep', 'Drum & Bass', 'Jungle', 'Future Bass', 'Glitch Hop', 'Hardstyle', 'Trap', 'Hyperpop'],
  ['Hip-Hop/Rap', 'Trap', 'R&B/Soul', 'Funk', 'Dancehall', 'Pop'],
  ['Lo-Fi', 'Ambient', 'Downtempo', 'Jazz', 'Classical', 'Soundtrack', 'Acoustic', 'Vaporwave'],
  ['Rock', 'Alternative', 'Punk', 'Metal', 'Folk', 'Blues', 'Country', 'Acoustic'],
  ['Pop', 'Latin', 'Reggae', 'Dancehall', 'World', 'Funk', 'Disco', 'Hyperpop'],
];

function genreCost(a, b) {
  if (!a || !b || a === b) return 0;
  return FAMILIES.some((f) => f.includes(a) && f.includes(b)) ? 0.2 : 0.6;
}

// ---- Energy arcs ----

function lerpPoints(points, t) {
  for (let i = 1; i < points.length; i++) {
    const [x1, y1] = points[i];
    const [x0, y0] = points[i - 1];
    if (t <= x1) return y0 + ((y1 - y0) * (t - x0)) / (x1 - x0 || 1);
  }
  return points[points.length - 1][1];
}

const ARCS = {
  // Warm up, climb, peak around two thirds in, then bring it home.
  journey: (t) => lerpPoints([[0, 0.45], [0.35, 0.64], [0.68, 0.88], [0.85, 0.8], [1, 0.6]], t),
  build: (t) => 0.35 + 0.55 * Math.pow(t, 0.9),
  peak: (t) => 0.82 + 0.06 * Math.sin(t * Math.PI * 3),
  steady: (t) => 0.55 + 0.05 * Math.sin(t * Math.PI * 4),
  'wind-down': (t) => 0.72 - 0.47 * t,
  wave: (t) => 0.58 + 0.2 * Math.sin(t * Math.PI * 3 - Math.PI / 2),
};

/**
 * Target energy for each slot. [level] moves the whole curve (0..1, 0.5 = as drawn).
 * With a [start] energy (DJ mode), the curve begins where the current song is.
 */
function arcTargets(n, arc = 'journey', level = 0.5, start = null, direction = 0) {
  const shape = ARCS[arc] || ARCS.journey;
  const shift = (level - 0.5) * 0.5;
  return Array.from({ length: n }, (_, i) => {
    const t = n <= 1 ? 0 : i / (n - 1);
    if (start != null) {
      // Continue from the current song, drifting the way the listener asked.
      return clamp(start + direction * 0.3 * Math.min(1, (i + 1) / Math.max(3, n * 0.6)) + 0.04 * Math.sin(i * 1.3), 0.08, 0.97);
    }
    return clamp(shape(t) + shift, 0.08, 0.97);
  });
}

// ---- Sequencing ----

function transitionCost(prev, next, recent) {
  let cost = 0;
  cost += 1.2 * bpmCost(prev.bpm, next.bpm);
  cost += 1.0 * keyCost(prev.camelot, next.camelot);
  cost += 0.8 * genreCost(prev.genre, next.genre);
  const jump = Math.abs(prev.energy - next.energy);
  if (jump > 0.22) cost += 2.2 * (jump - 0.22);
  const artist = (next.artist || '').toLowerCase();
  if (artist && (prev.artist || '').toLowerCase() === artist) cost += 3;
  else if (artist && recent.some((r) => (r.artist || '').toLowerCase() === artist)) cost += 1;
  return cost;
}

function slotCost(item, target) {
  return 3 * Math.abs(item.energy - target) + 1.2 * (1 - (item.fit ?? 0.5));
}

function totalCost(order, targets, start) {
  let cost = 0;
  order.forEach((item, i) => {
    cost += slotCost(item, targets[i]);
    const prev = i === 0 ? start : order[i - 1];
    if (prev) cost += transitionCost(prev, item, order.slice(Math.max(0, i - 4), Math.max(0, i - 1)));
  });
  return cost;
}

/**
 * Chooses [size] songs from [pool] and orders them. Beam search over the slots, then a few
 * rounds of swapping pairs while that lowers the cost. Pool items need
 * { id, artist, genre, bpm, camelot, energy, fit }.
 */
function sequence(pool, { size, arc = 'journey', level = 0.5, start = null, direction = 0, beam = 14 } = {}) {
  const items = pool.filter((p) => p && p.id && (!start || p.id !== start.id));
  const n = Math.min(size || items.length, items.length);
  if (n === 0) return [];
  const targets = arcTargets(n, arc, level, start ? start.energy : null, direction);

  let beams = [{ order: [], used: new Set(), cost: 0 }];
  for (let i = 0; i < n; i++) {
    const next = [];
    for (const b of beams) {
      const prev = b.order.length ? b.order[b.order.length - 1] : start;
      const recent = b.order.slice(-4, -1);
      for (const item of items) {
        if (b.used.has(item.id)) continue;
        let cost = b.cost + slotCost(item, targets[i]);
        if (prev) cost += transitionCost(prev, item, recent);
        next.push({ b, item, cost });
      }
    }
    next.sort((x, y) => x.cost - y.cost);
    beams = next.slice(0, beam).map(({ b, item, cost }) => {
      const used = new Set(b.used);
      used.add(item.id);
      return { order: [...b.order, item], used, cost };
    });
  }

  let order = beams[0].order;
  let best = totalCost(order, targets, start);
  for (let pass = 0; pass < 3; pass++) {
    let improved = false;
    for (let i = 0; i < order.length - 1; i++) {
      for (let j = i + 1; j < order.length; j++) {
        const swapped = order.slice();
        [swapped[i], swapped[j]] = [swapped[j], swapped[i]];
        const c = totalCost(swapped, targets, start);
        if (c + 1e-9 < best) {
          order = swapped;
          best = c;
          improved = true;
        }
      }
    }
    if (!improved) break;
  }
  return order.map((item, i) => ({ ...item, target: Number(targets[i].toFixed(2)) }));
}

/** How to get from one song to the next, the way you'd mark it on a set list. */
function mixNote(prev, next) {
  const gap = bpmGap(prev.bpm, next.bpm);
  const keys = keyCost(prev.camelot, next.camelot);
  const lift = next.energy - prev.energy;
  const beatSec = (bpm) => 60 / (bpm || 120);
  const fade = (bars) => Math.round(bars * 4 * beatSec(prev.bpm) * 10) / 10;
  if (gap != null && gap <= 3 && keys <= 0.2) return { technique: 'long blend', bars: 32, fadeSec: Math.min(fade(32), 12) };
  if (lift > 0.2) return { technique: 'cut on the drop', bars: 1, fadeSec: 0.5 };
  if (gap != null && gap > 10) return { technique: 'echo out', bars: 4, fadeSec: Math.min(fade(4), 4) };
  if (keys >= 1) return { technique: 'filter swap', bars: 8, fadeSec: Math.min(fade(8), 6) };
  if (lift < -0.2) return { technique: 'slow fade', bars: 16, fadeSec: Math.min(fade(16), 9) };
  return { technique: 'blend', bars: 16, fadeSec: Math.min(fade(16), 8) };
}

module.exports = {
  camelot,
  keyCost,
  bpmGap,
  bpmCost,
  estimateEnergy,
  genreCost,
  arcTargets,
  sequence,
  mixNote,
  ARCS,
  GENRE_ENERGY,
  clamp,
};
