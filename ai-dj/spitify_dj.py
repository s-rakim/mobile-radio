#!/usr/bin/env python3
"""
Spitify DJ, the Python version.

Does the same job as server/services/aiDj.js: an agent digs through Audius for songs
that fit a request, rates their energy, and a sequencer puts them in an order that
mixes like a real DJ set (tempo within a few percent, neighbouring keys on the Camelot
wheel, an energy arc, no artist twice in a row). No packages needed, only Python 3.9+.

Any OpenAI-style chat endpoint works. Set:

    AI_API_KEY    your provider's key
    AI_MODEL      the model name your provider lists
    AI_BASE_URL   defaults to https://api.openai.com/v1

Without a key it still runs on rules alone.

    python spitify_dj.py playlist "sunset deep house for a rooftop" --size 25
    python spitify_dj.py dj --track <audius id> --request "pick it up a bit"
    python spitify_dj.py serve --port 8765      # same /api/ai/* routes as the Node server
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

AUDIUS = "https://api.audius.co"
MAX_AGENT_TURNS = 8
MAX_POOL = 220

# --------------------------------------------------------------------------- keys

MAJOR = {"B": 1, "F#": 2, "Gb": 2, "Db": 3, "C#": 3, "Ab": 4, "G#": 4, "Eb": 5, "D#": 5, "Bb": 6, "A#": 6,
         "F": 7, "C": 8, "G": 9, "D": 10, "A": 11, "E": 12}
MINOR = {"Ab": 1, "G#": 1, "Eb": 2, "D#": 2, "Bb": 3, "A#": 3, "F": 4, "C": 5, "G": 6, "D": 7, "A": 8,
         "E": 9, "B": 10, "F#": 11, "Gb": 11, "Db": 12, "C#": 12}


def camelot(key):
    """'A minor', 'Am', 'F#maj', 'C♯ minor', '8A' -> '8A'. Unknown -> None."""
    if not key:
        return None
    s = str(key).strip().replace("♯", "#").replace("♭", "b")
    wheel = re.match(r"^(1[0-2]|[1-9])\s*([ABab])$", s)
    if wheel:
        return f"{wheel.group(1)}{wheel.group(2).upper()}"
    m = re.match(r"^([A-Ga-g])([#b]?)\s*(.*)$", s)
    if not m:
        return None
    note = m.group(1).upper() + m.group(2)
    minor = bool(re.match(r"^(m(?!aj)|min|minor)", m.group(3).lower()))
    n = (MINOR if minor else MAJOR).get(note)
    return f"{n}{'A' if minor else 'B'}" if n else None


def key_cost(a, b):
    if not a or not b:
        return 0.35
    na, nb = int(a[:-1]), int(b[:-1])
    la, lb = a[-1], b[-1]
    step = min((na - nb) % 12, (nb - na) % 12)
    if na == nb and la == lb:
        return 0.0
    if la == lb and step == 1:
        return 0.1
    if na == nb:
        return 0.2
    if la == lb and step == 2:
        return 0.55
    if la != lb and step == 1:
        return 0.6
    return 1.2


# --------------------------------------------------------------------------- tempo

def bpm_gap(a, b):
    """Percent apart, allowing half and double time."""
    if not a or not b:
        return None
    return min(abs(math.log(r)) for r in (b / a, b * 2 / a, b / (a * 2))) * 100


def bpm_cost(a, b):
    gap = bpm_gap(a, b)
    if gap is None:
        return 0.35
    if gap <= 3:
        return 0.0
    if gap <= 6:
        return 0.3
    if gap <= 10:
        return 0.9
    return 2.0


# --------------------------------------------------------------------------- energy

GENRE_ENERGY = {
    "Audiobooks": .1, "Podcasts": .1, "Spoken Word": .2, "Ambient": .2, "Devotional": .3, "Classical": .3, "Lo-Fi": .3,
    "Comedy": .3, "Acoustic": .35, "Folk": .35, "Downtempo": .38, "Jazz": .4, "Vaporwave": .4, "Soundtrack": .45,
    "Blues": .45, "Country": .5, "R&B/Soul": .5, "Reggae": .5, "World": .5, "Experimental": .5, "Kids": .5,
    "Alternative": .55, "Tropical House": .6, "Pop": .62, "Funk": .65, "Disco": .68, "Hip-Hop/Rap": .66, "Latin": .68,
    "Deep House": .68, "Dancehall": .7, "Rock": .7, "Hyperpop": .75, "Electronic": .74, "Future Bass": .76,
    "House": .76, "Future House": .78, "Moombahton": .78, "Glitch Hop": .78, "Progressive House": .78,
    "Tech House": .8, "Electro": .8, "Trap": .8, "Trance": .82, "Techno": .84, "Punk": .85, "Jersey Club": .85,
    "Jungle": .86, "Metal": .88, "Drum & Bass": .9, "Dubstep": .9, "Hardstyle": .95,
}

MOOD_SHIFT = {
    "Peaceful": -.15, "Tender": -.12, "Melancholy": -.12, "Sentimental": -.1, "Romantic": -.08, "Easygoing": -.08,
    "Yearning": -.06, "Sensual": -.06, "Sophisticated": -.04, "Brooding": -.04, "Gritty": .05, "Stirring": .06,
    "Defiant": .08, "Empowering": .08, "Upbeat": .1, "Fiery": .12, "Rowdy": .12, "Excited": .12,
    "Energizing": .15, "Aggressive": .15,
}


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def estimate_energy(t):
    e = GENRE_ENERGY.get(t.get("genre"), .55) + MOOD_SHIFT.get(t.get("mood"), 0)
    if t.get("bpm"):
        e += clamp((t["bpm"] - 110) / 200, -.15, .2)
    words = f"{t.get('title', '')} {t.get('tags') or ''}".lower()
    if re.search(r"\b(chill|relax|sleep|calm|lofi|lo-fi|acoustic|piano|ambient)\b", words):
        e -= .1
    if re.search(r"\b(hype|banger|rage|club|festival|drop|turn up|bounce|vip)\b", words):
        e += .08
    return clamp(e, .05, .98)


FAMILIES = [
    {"Electronic", "House", "Deep House", "Tech House", "Future House", "Progressive House", "Tropical House", "Techno",
     "Trance", "Electro", "Disco", "Jersey Club", "Moombahton"},
    {"Electronic", "Dubstep", "Drum & Bass", "Jungle", "Future Bass", "Glitch Hop", "Hardstyle", "Trap", "Hyperpop"},
    {"Hip-Hop/Rap", "Trap", "R&B/Soul", "Funk", "Dancehall", "Pop"},
    {"Lo-Fi", "Ambient", "Downtempo", "Jazz", "Classical", "Soundtrack", "Acoustic", "Vaporwave"},
    {"Rock", "Alternative", "Punk", "Metal", "Folk", "Blues", "Country", "Acoustic"},
    {"Pop", "Latin", "Reggae", "Dancehall", "World", "Funk", "Disco", "Hyperpop"},
]


def genre_cost(a, b):
    if not a or not b or a == b:
        return 0.0
    return .2 if any(a in f and b in f for f in FAMILIES) else .6


# --------------------------------------------------------------------------- arcs

def _lerp(points, t):
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        if t <= x1:
            return y0 + (y1 - y0) * (t - x0) / ((x1 - x0) or 1)
    return points[-1][1]


ARCS = {
    "journey": lambda t: _lerp([(0, .45), (.35, .64), (.68, .88), (.85, .8), (1, .6)], t),
    "build": lambda t: .35 + .55 * t ** .9,
    "peak": lambda t: .82 + .06 * math.sin(t * math.pi * 3),
    "steady": lambda t: .55 + .05 * math.sin(t * math.pi * 4),
    "wind-down": lambda t: .72 - .47 * t,
    "wave": lambda t: .58 + .2 * math.sin(t * math.pi * 3 - math.pi / 2),
}


def arc_targets(n, arc="journey", level=.5, start=None, direction=0):
    shape = ARCS.get(arc, ARCS["journey"])
    shift = (level - .5) * .5
    out = []
    for i in range(n):
        t = 0 if n <= 1 else i / (n - 1)
        if start is not None:
            out.append(clamp(start + direction * .3 * min(1, (i + 1) / max(3, n * .6)) + .04 * math.sin(i * 1.3), .08, .97))
        else:
            out.append(clamp(shape(t) + shift, .08, .97))
    return out


# --------------------------------------------------------------------------- sequencing

def transition_cost(prev, nxt, recent):
    cost = 1.2 * bpm_cost(prev.get("bpm"), nxt.get("bpm"))
    cost += key_cost(prev.get("camelot"), nxt.get("camelot"))
    cost += .8 * genre_cost(prev.get("genre"), nxt.get("genre"))
    jump = abs(prev["energy"] - nxt["energy"])
    if jump > .22:
        cost += 2.2 * (jump - .22)
    artist = (nxt.get("artist") or "").lower()
    if artist and (prev.get("artist") or "").lower() == artist:
        cost += 3
    elif artist and any((r.get("artist") or "").lower() == artist for r in recent):
        cost += 1
    return cost


def slot_cost(item, target):
    return 3 * abs(item["energy"] - target) + 1.2 * (1 - item.get("fit", .5))


def total_cost(order, targets, start):
    cost = 0.0
    for i, item in enumerate(order):
        cost += slot_cost(item, targets[i])
        prev = start if i == 0 else order[i - 1]
        if prev:
            cost += transition_cost(prev, item, order[max(0, i - 4):max(0, i - 1)])
    return cost


def sequence(pool, size, arc="journey", level=.5, start=None, direction=0, beam=14):
    items = [p for p in pool if p and p.get("id") and (not start or p["id"] != start.get("id"))]
    n = min(size or len(items), len(items))
    if n == 0:
        return []
    targets = arc_targets(n, arc, level, start["energy"] if start else None, direction)
    beams = [([], frozenset(), 0.0)]
    for i in range(n):
        nxt = []
        for order, used, cost in beams:
            prev = order[-1] if order else start
            recent = order[-4:-1]
            for item in items:
                if item["id"] in used:
                    continue
                c = cost + slot_cost(item, targets[i])
                if prev:
                    c += transition_cost(prev, item, recent)
                nxt.append((c, order, used, item))
        nxt.sort(key=lambda x: x[0])
        beams = [(order + [item], used | {item["id"]}, c) for c, order, used, item in nxt[:beam]]
    order = beams[0][0]
    best = total_cost(order, targets, start)
    for _ in range(3):
        improved = False
        for i in range(len(order) - 1):
            for j in range(i + 1, len(order)):
                swapped = order[:]
                swapped[i], swapped[j] = swapped[j], swapped[i]
                c = total_cost(swapped, targets, start)
                if c + 1e-9 < best:
                    order, best, improved = swapped, c, True
        if not improved:
            break
    return [dict(item, target=round(targets[i], 2)) for i, item in enumerate(order)]


def mix_note(prev, nxt):
    gap = bpm_gap(prev.get("bpm"), nxt.get("bpm"))
    keys = key_cost(prev.get("camelot"), nxt.get("camelot"))
    lift = nxt["energy"] - prev["energy"]

    def fade(bars):
        return round(bars * 4 * 60 / (prev.get("bpm") or 120), 1)

    if gap is not None and gap <= 3 and keys <= .2:
        return {"technique": "long blend", "bars": 32, "fadeSec": min(fade(32), 12)}
    if lift > .2:
        return {"technique": "cut on the drop", "bars": 1, "fadeSec": .5}
    if gap is not None and gap > 10:
        return {"technique": "echo out", "bars": 4, "fadeSec": min(fade(4), 4)}
    if keys >= 1:
        return {"technique": "filter swap", "bars": 8, "fadeSec": min(fade(8), 6)}
    if lift < -.2:
        return {"technique": "slow fade", "bars": 16, "fadeSec": min(fade(16), 9)}
    return {"technique": "blend", "bars": 16, "fadeSec": min(fade(16), 8)}


# --------------------------------------------------------------------------- Audius

_cache: dict = {}


def http_json(url, body=None, headers=None, timeout=8):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers={"User-Agent": "spitify/1.0", **(headers or {})})
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as res:
        return json.loads(res.read().decode())


def audius(path, **params):
    params["app_name"] = "spitify"
    url = f"{AUDIUS}{path}?{urllib.parse.urlencode(params)}"
    hit = _cache.get(url)
    if hit and time.time() - hit[0] < 600:
        return hit[1]
    data = http_json(url).get("data")
    _cache[url] = (time.time(), data)
    return data


def features(t):
    if not isinstance(t, dict) or not t.get("id") or t.get("is_streamable") is False or t.get("is_stream_gated") or t.get("stream_conditions"):
        return None
    try:
        bpm = float(t.get("bpm") or 0)
    except (TypeError, ValueError):
        bpm = 0
    item = {
        "id": str(t["id"]),
        "title": t.get("title") or "",
        "artist": (t.get("user") or {}).get("name") or "",
        "genre": t.get("genre"),
        "mood": t.get("mood"),
        "tags": t.get("tags") or "",
        "bpm": round(bpm) if 40 < bpm < 220 else None,
        "camelot": camelot(t.get("musical_key") or t.get("key")),
        "plays": t.get("play_count") or 0,
    }
    item["energy"] = estimate_energy(item)
    return item


def _many(raw):
    return [f for f in (features(t) for t in (raw or [])) if f]


def search_tracks(query, limit=25):
    return _many(audius("/v1/tracks/search", query=str(query)[:80], limit=min(limit, 40)))


def trending_tracks(genre=None, period="month"):
    params = {"time": period, "limit": 50}
    if genre:
        params["genre"] = genre
    return _many(audius("/v1/tracks/trending", **params))


def artist_tracks(name):
    users = audius("/v1/users/search", query=str(name)[:60], limit=3) or []
    if not users:
        return []
    user = max(users, key=lambda u: u.get("follower_count") or 0)
    try:
        raw = audius(f"/v1/users/{urllib.parse.quote(user['id'])}/tracks", limit=30, sort_method="plays", sort_direction="desc")
    except urllib.error.URLError:
        raw = audius(f"/v1/users/{urllib.parse.quote(user['id'])}/tracks", limit=30)
    return _many(raw)


def track_by_id(track_id):
    try:
        return features(audius(f"/v1/tracks/{urllib.parse.quote(str(track_id))}"))
    except (urllib.error.URLError, ValueError):
        return None


# --------------------------------------------------------------------------- the model

def config():
    return {
        "key": os.environ.get("AI_API_KEY", ""),
        "model": os.environ.get("AI_MODEL", ""),
        "base": os.environ.get("AI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
        "temperature": os.environ.get("AI_TEMPERATURE"),
    }


def agent_ready():
    c = config()
    return bool(c["key"] and c["model"])


class ModelError(Exception):
    def __init__(self, status, body):
        super().__init__(f"Model error {status}: {body[:300]}")
        self.status, self.body = status, body


def chat(messages, tools=None, tool_choice=None):
    c = config()
    body = {"model": c["model"], "messages": messages}
    if tools:
        body["tools"] = tools
    if tool_choice:
        body["tool_choice"] = tool_choice
    if c["temperature"]:
        body["temperature"] = float(c["temperature"])
    req = urllib.request.Request(f"{c['base']}/chat/completions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "Authorization": f"Bearer {c['key']}"})
    try:
        with urllib.request.urlopen(req, timeout=90) as res:
            reply = json.loads(res.read().decode())
    except urllib.error.HTTPError as e:
        raise ModelError(e.code, e.read().decode(errors="replace")) from None
    msg = (reply.get("choices") or [{}])[0].get("message")
    if not msg:
        raise RuntimeError("The model sent back nothing")
    return msg


def parse_json(text):
    if not text:
        return None
    fenced = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    raw = fenced.group(1) if fenced else text
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(raw[start:end + 1])
    except json.JSONDecodeError:
        return None


GENRES = ("Electronic, House, Deep House, Tech House, Future House, Progressive House, Tropical House, Techno, Trance, "
          "Electro, Disco, Dubstep, Drum & Bass, Jungle, Future Bass, Glitch Hop, Hardstyle, Jersey Club, Moombahton, "
          "Trap, Hip-Hop/Rap, R&B/Soul, Funk, Pop, Hyperpop, Latin, Reggae, Dancehall, World, Rock, Alternative, Punk, "
          "Metal, Folk, Country, Blues, Acoustic, Jazz, Classical, Soundtrack, Ambient, Lo-Fi, Downtempo, Vaporwave, "
          "Experimental, Devotional")

PERSONA = """You are a professional DJ with twenty years behind the decks: clubs, radio, weddings, warm-up sets and closing sets. You pick music for Spitify, which plays songs from Audius, an open catalogue of independent artists. You only ever pick songs that your search tools returned, by their id.

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
- When you talk, sound like a real radio DJ: warm, short, specific, no emojis, no hype words like "ultimate" or "vibes curated". One or two sentences."""


def _fn(name, description, properties, required=None):
    return {"type": "function", "function": {"name": name, "description": description,
            "parameters": {"type": "object", "properties": properties, **({"required": required} if required else {})}}}


TOOLS = [
    _fn("search_tracks", "Search Audius for songs by any words: genre, scene, mood, activity, artist or title. Returns "
        "id | title | artist | genre | mood | bpm | key | energy (0-1 estimate) | plays.", {"query": {"type": "string"}}, ["query"]),
    _fn("trending_tracks", f"Songs trending on Audius, optionally in one genre. Genres: {GENRES}.",
        {"genre": {"type": "string"}, "time": {"type": "string", "enum": ["week", "month", "year", "allTime"]}}),
    _fn("artist_tracks", "An Audius artist's most played songs, found by name.", {"name": {"type": "string"}}, ["name"]),
    _fn("submit_set", "Hand over the shortlist. Only ids returned by the search tools count.", {
        "title": {"type": "string"},
        "description": {"type": "string"},
        "arc": {"type": "string", "enum": list(ARCS)},
        "level": {"type": "number", "description": "Overall energy 1-10."},
        "intro": {"type": "string", "description": "What you would say on air before the first song."},
        "picks": {"type": "array", "items": {"type": "object", "properties": {
            "id": {"type": "string"}, "energy": {"type": "number"}, "fit": {"type": "number"}, "note": {"type": "string"}},
            "required": ["id", "energy"]}},
    }, ["picks"]),
]


def table(items):
    if not items:
        return "No results."
    return "\n".join(" | ".join(str(x) for x in (t["id"], t["title"], t["artist"], t["genre"] or "-", t["mood"] or "-",
                                                  t["bpm"] or "-", t["camelot"] or "-", f"{t['energy']:.2f}", t["plays"]))
                     for t in items[:30])


def run_tool(name, args, pool, skip):
    try:
        if name == "search_tracks":
            found = search_tracks(args.get("query", ""))
        elif name == "trending_tracks":
            found = trending_tracks(args.get("genre"), args.get("time", "month"))
        elif name == "artist_tracks":
            found = artist_tracks(args.get("name", ""))
        else:
            return "Unknown tool."
    except (urllib.error.URLError, ValueError, TimeoutError) as e:
        return f"That search failed ({e}). Try different words."
    found = [t for t in found if t["id"] not in skip]
    for t in found:
        if len(pool) < MAX_POOL:
            pool[t["id"]] = t
    return table(found)


def gather(plan, pool, skip):
    jobs = ([lambda q=q: search_tracks(q) for q in (plan.get("searches") or [])[:6]] +
            [lambda g=g: trending_tracks(g) for g in (plan.get("genres") or [])[:3]] +
            [lambda a=a: artist_tracks(a) for a in (plan.get("artists") or [])[:3]])
    if not jobs:
        jobs = [lambda: trending_tracks(None, "week")]
    for job in jobs:
        try:
            for t in job():
                if t["id"] not in skip and len(pool) < MAX_POOL:
                    pool[t["id"]] = t
        except (urllib.error.URLError, ValueError, TimeoutError):
            continue


def json_pick(task, skip, pool):
    plan = parse_json(chat([{"role": "system", "content": PERSONA}, {"role": "user", "content":
        f'{task}\n\nFirst plan the dig. Reply with JSON only: {{"searches": [up to 6 phrases], "genres": [up to 3 of: {GENRES}], "artists": [up to 3 names]}}'}])
        .get("content")) or {}
    gather(plan, pool, skip)
    picked = parse_json(chat([{"role": "system", "content": PERSONA}, {"role": "user", "content":
        f"{task}\n\nHere is what the crates turned up (id | title | artist | genre | mood | bpm | key | energy | plays):\n"
        f"{table(list(pool.values())[:120])}\n\nReply with JSON only: "
        '{"title": "", "description": "", "arc": "journey|build|peak|steady|wind-down|wave", "level": 1-10, "intro": "", '
        '"picks": [{"id": "", "energy": 1-10, "fit": 1-10}]}'}]).get("content")) or {}
    return picked, pool


def agent_pick(task, skip):
    pool: dict = {}
    messages = [{"role": "system", "content": PERSONA}, {"role": "user", "content": task}]
    submitted = None
    try:
        for turn in range(MAX_AGENT_TURNS):
            if submitted:
                break
            last = turn == MAX_AGENT_TURNS - 1
            msg = chat(messages, TOOLS, {"type": "function", "function": {"name": "submit_set"}} if last else "auto")
            entry = {"role": "assistant", "content": msg.get("content")}
            if msg.get("tool_calls"):
                entry["tool_calls"] = msg["tool_calls"]
            messages.append(entry)
            calls = msg.get("tool_calls") or []
            if not calls:
                parsed = parse_json(msg.get("content"))
                if parsed and parsed.get("picks"):
                    submitted = parsed
                else:
                    messages.append({"role": "user", "content": "Use the tools to search, then call submit_set."})
                continue
            for call in calls:
                fn = call.get("function") or {}
                try:
                    args = json.loads(fn.get("arguments") or "{}")
                except json.JSONDecodeError:
                    args = {}
                if fn.get("name") == "submit_set":
                    submitted = args
                    messages.append({"role": "tool", "tool_call_id": call.get("id"), "content": "Got it."})
                else:
                    messages.append({"role": "tool", "tool_call_id": call.get("id"), "content": run_tool(fn.get("name"), args, pool, skip)})
    except ModelError as e:
        if 400 <= e.status < 500 and "tool" in e.body.lower():
            return json_pick(task, skip, pool)
        raise
    return submitted or {}, pool


# --------------------------------------------------------------------------- rules mode

WORDS = [
    (r"hip ?hop|rap|bars", {"genres": ["Hip-Hop/Rap"]}),
    (r"trap|drill", {"genres": ["Trap"], "searches": ["drill"]}),
    (r"edm|electronic|electro|synth", {"genres": ["Electronic"]}),
    (r"house|amapiano", {"genres": ["House", "Deep House"], "searches": ["amapiano"]}),
    (r"techno|rave|warehouse", {"genres": ["Techno"], "arc": "peak"}),
    (r"dnb|drum and bass|drum & bass|jungle", {"genres": ["Drum & Bass"]}),
    (r"dubstep|riddim|bass music", {"genres": ["Dubstep"]}),
    (r"pop|catchy", {"genres": ["Pop"]}),
    (r"r&b|rnb|soul|slow jam", {"genres": ["R&B/Soul"]}),
    (r"lo-?fi|study|focus|homework|coding|reading", {"genres": ["Lo-Fi"], "arc": "steady", "level": .3}),
    (r"ambient|calm|relax|meditat|spa|peaceful", {"genres": ["Ambient"], "arc": "steady", "level": .2}),
    (r"sleep|bedtime|wind down", {"genres": ["Ambient", "Lo-Fi"], "arc": "wind-down", "level": .2}),
    (r"jazz|coffee|cafe", {"genres": ["Jazz"], "arc": "steady", "level": .4}),
    (r"latin|reggaeton|salsa|bachata", {"genres": ["Latin"], "searches": ["reggaeton"]}),
    (r"afro|naija|afrobeat", {"searches": ["afrobeats", "afropop"]}),
    (r"rock|guitar|punk|grunge", {"genres": ["Rock", "Alternative"]}),
    (r"metal", {"genres": ["Metal"], "level": .85}),
    (r"indie|alternative", {"genres": ["Alternative"]}),
    (r"acoustic|unplugged|folk", {"genres": ["Acoustic", "Folk"], "level": .35}),
    (r"reggae|dancehall", {"genres": ["Reggae", "Dancehall"]}),
    (r"disco|funk|groove", {"genres": ["Disco", "Funk"]}),
    (r"classical|piano|orchestra", {"genres": ["Classical"], "arc": "steady", "level": .3}),
    (r"cinematic|epic|soundtrack|film", {"genres": ["Soundtrack"], "arc": "build"}),
    (r"gym|workout|run|cardio|lift|hype", {"genres": ["Hip-Hop/Rap", "Electronic"], "searches": ["workout"], "arc": "peak", "level": .8}),
    (r"party|club|pregame|turn up|dance", {"genres": ["House", "Hip-Hop/Rap"], "searches": ["party"], "arc": "build", "level": .75}),
    (r"sad|heartbreak|breakup|cry|lonely", {"genres": ["R&B/Soul", "Alternative"], "searches": ["sad"], "arc": "steady", "level": .3}),
    (r"happy|sunny|summer|feel good|upbeat", {"genres": ["Pop", "Funk"], "searches": ["summer"], "level": .65}),
    (r"love|romantic|date night", {"genres": ["R&B/Soul"], "searches": ["love songs"], "level": .4}),
    (r"road trip|drive|driving", {"genres": ["Pop", "Rock"], "searches": ["road trip"], "arc": "journey"}),
    (r"morning|wake up|breakfast", {"genres": ["Acoustic", "Pop"], "arc": "build", "level": .45}),
    (r"rain|cozy|autumn", {"genres": ["Lo-Fi", "Jazz"], "arc": "steady", "level": .35}),
    (r"gaming|game", {"genres": ["Electronic", "Dubstep"], "searches": ["gaming"], "level": .75}),
    (r"gospel|worship|church", {"searches": ["gospel", "worship"]}),
]


def rules_plan(prompt):
    text = str(prompt or "").lower()
    plan = {"searches": [], "genres": [], "artists": [], "arc": "journey", "level": .5}
    for pattern, hint in WORDS:
        if re.search(pattern, text):
            plan["genres"] += hint.get("genres", [])
            plan["searches"] += hint.get("searches", [])
            plan["arc"] = hint.get("arc", plan["arc"])
            plan["level"] = hint.get("level", plan["level"])
    like = re.search(r"(?:like|by|similar to|sounds like|inspired by) ([a-z0-9&' .-]{2,40})", text)
    if like:
        plan["artists"].append(re.split(r" and | but | for | with ", like.group(1))[0].strip())
    if not (plan["genres"] or plan["searches"] or plan["artists"]) and text.strip():
        plan["searches"].append(text.strip()[:60])
    plan["genres"] = list(dict.fromkeys(plan["genres"]))[:3]
    plan["searches"] = list(dict.fromkeys(plan["searches"]))[:4]
    return plan


# --------------------------------------------------------------------------- sets

def finish(plan, pool, size, start=None, direction=0):
    picks = []
    if plan.get("picks"):
        for p in plan["picks"]:
            t = pool.get(str(p.get("id")))
            if not t:
                continue
            try:
                energy = clamp(.65 * float(p.get("energy")) / 10 + .35 * t["energy"], .05, .98)
            except (TypeError, ValueError):
                energy = t["energy"]
            try:
                fit = clamp(float(p.get("fit")) / 10)
            except (TypeError, ValueError):
                fit = .8
            picks.append(dict(t, energy=energy, fit=fit, note=p.get("note")))
    else:
        picks = [dict(t, fit=.5 + min(.3, math.log10((t.get("plays") or 0) + 1) / 20)) for t in pool.values()]

    per_artist: dict = {}
    capped = []
    for t in picks:
        k = (t.get("artist") or "").lower()
        per_artist[k] = per_artist.get(k, 0) + 1
        if per_artist[k] <= 2:
            capped.append(t)

    try:
        level = float(plan.get("level"))
        level = level / 10 if level > 1 else level
    except (TypeError, ValueError):
        level = .5
    ordered = sequence(capped, size, plan.get("arc") or "journey", level, start, direction)
    out = []
    for i, t in enumerate(ordered):
        prev = start if i == 0 else ordered[i - 1]
        out.append({"id": t["id"], "title": t["title"], "artist": t["artist"], "genre": t["genre"], "bpm": t["bpm"],
                    "key": t["camelot"], "energy": round(t["energy"], 2), "note": t.get("note"),
                    "mixIn": mix_note(prev, t) if prev else None})
    return out


def _size(n, fallback):
    try:
        return max(5, min(100, round(float(n))))
    except (TypeError, ValueError):
        return fallback


def playlist(prompt, size=30, skip=()):
    text = str(prompt or "").strip()[:400]
    if not text:
        raise ValueError("Tell the DJ what you want to hear")
    n = _size(size, 30)
    skip = set(map(str, skip))
    if agent_ready():
        try:
            plan, pool = agent_pick(f'Make a {n}-song playlist for this request: "{text}". Shortlist about {round(n * 1.5)} songs.', skip)
            tracks = finish(plan, pool, n)
            if len(tracks) >= min(5, n):
                return {"engine": "agent", "title": (plan.get("title") or text)[:60],
                        "description": plan.get("description") or f'Made from "{text}"',
                        "intro": plan.get("intro"), "arc": plan.get("arc") or "journey", "tracks": tracks}
        except (ModelError, RuntimeError, urllib.error.URLError, TimeoutError) as e:
            print(f"Agent failed, using rules: {e}", file=sys.stderr)
    plan = rules_plan(text)
    pool: dict = {}
    gather(plan, pool, skip)
    return {"engine": "rules", "title": text[:1].upper() + text[1:40], "description": f'Made from "{text}"',
            "intro": None, "arc": plan["arc"], "tracks": finish(plan, pool, n)}


def direction_of(request):
    r = str(request or "").lower()
    if re.search(r"more energy|turn it up|hype|harder|faster|pick it up|party|banger", r):
        return 1
    if re.search(r"chill|calm|slow|softer|wind down|relax|mellow|sleep", r):
        return -1
    return 0


def dj(current=None, history=(), request=None, count=8, skip=()):
    n = _size(count, 8)
    history = list(history or [])
    skip = set(map(str, skip)) | {str(h.get("id")) for h in history if h.get("id")}
    current = current or {}
    if current.get("id"):
        skip.add(str(current["id"]))
    direction = direction_of(request)
    start = track_by_id(current["id"]) if current.get("id") else None
    if not start and current:
        start = {"id": str(current.get("id") or "now"), "title": current.get("title", ""), "artist": current.get("artist", ""),
                 "genre": current.get("genre"), "bpm": None, "camelot": None, "tags": ""}
        start["energy"] = estimate_energy(start)
    recent = "\n".join(f"{h.get('title')} - {h.get('artist')}" for h in history[-15:])

    if agent_ready():
        try:
            parts = [f"You're live, keeping a DJ set going. Pick the next {n} songs. Shortlist about {round(n * 1.8)}."]
            if start:
                parts.append(f"Playing now: {start['title']} by {start['artist']} | {start.get('genre') or '?'} | "
                             f"{start.get('bpm') or '?'} BPM | key {start.get('camelot') or '?'} | energy {start['energy']:.2f}. "
                             "The first pick has to mix out of this.")
            if recent:
                parts.append(f"Recently played, don't repeat these:\n{recent}")
            parts.append(f'The listener just asked: "{str(request)[:200]}"' if request else
                         "No request; read the room from what has been playing and keep it flowing, with a little lift now and then.")
            parts.append("In the intro field, say what you would on air going into the next song: one or two short sentences.")
            plan, pool = agent_pick("\n\n".join(parts), skip)
            tracks = finish(plan, pool, n, start, direction)
            if tracks:
                return {"engine": "agent", "intro": plan.get("intro"), "tracks": tracks}
        except (ModelError, RuntimeError, urllib.error.URLError, TimeoutError) as e:
            print(f"Agent failed, using rules: {e}", file=sys.stderr)

    plan = rules_plan(request or "")
    if start and start.get("genre"):
        plan["genres"].insert(0, start["genre"])
    if start and start.get("artist"):
        plan["searches"].append(start["artist"])
    plan["genres"] = list(dict.fromkeys(plan["genres"]))[:3]
    pool: dict = {}
    gather(plan, pool, skip)
    tracks = finish(dict(plan, picks=None), pool, n, start, direction)
    return {"engine": "rules", "intro": f"Up next, {tracks[0]['artist']} with {tracks[0]['title']}." if tracks else None,
            "tracks": tracks}


# --------------------------------------------------------------------------- serve

class Handler(BaseHTTPRequestHandler):
    """Same routes as the Node server. Set SPITIFY_DJ_TOKEN to require 'Authorization: Bearer <token>'."""

    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _authorised(self):
        token = os.environ.get("SPITIFY_DJ_TOKEN")
        return not token or self.headers.get("Authorization") == f"Bearer {token}"

    def do_GET(self):
        if not self._authorised():
            return self._send(401, {"error": "Missing token"})
        if self.path.startswith("/api/ai/status"):
            return self._send(200, {"enabled": True, "agent": agent_ready(), "model": config()["model"] or None})
        self._send(404, {"error": "Not found"})

    def do_POST(self):
        if not self._authorised():
            return self._send(401, {"error": "Missing token"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length") or 0)) or b"{}")
            if self.path.startswith("/api/ai/playlist"):
                return self._send(200, playlist(body.get("prompt"), body.get("size", 30), body.get("skip") or ()))
            if self.path.startswith("/api/ai/dj"):
                return self._send(200, dj(body.get("current"), body.get("history") or (), body.get("request"),
                                          body.get("count", 8), body.get("skip") or ()))
            self._send(404, {"error": "Not found"})
        except ValueError as e:
            self._send(400, {"error": str(e)})
        except Exception as e:  # keep the server up whatever a request does
            print(f"Request failed: {e}", file=sys.stderr)
            self._send(502, {"error": "The DJ couldn't put that together right now"})


def print_set(result):
    print(f"\n{result.get('title') or 'DJ set'}  [{result['engine']}]")
    if result.get("intro"):
        print(f'  "{result["intro"]}"')
    for i, t in enumerate(result["tracks"], 1):
        mix = t["mixIn"]["technique"] if t.get("mixIn") else "start"
        print(f"{i:>3}. {t['title']} - {t['artist']}  |  {t['bpm'] or '?'} BPM  {t['key'] or '?'}  "
              f"energy {t['energy']:.2f}  |  {mix}")


def main():
    ap = argparse.ArgumentParser(description="Spitify DJ: playlists and DJ sets from Audius that mix like a real set.")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("playlist", help="make a playlist from a prompt")
    p.add_argument("prompt")
    p.add_argument("--size", type=int, default=30)
    d = sub.add_parser("dj", help="pick what plays next after a song")
    d.add_argument("--track", help="Audius id of the song playing now")
    d.add_argument("--request", help='e.g. "more energy" or "something chill"')
    d.add_argument("--count", type=int, default=8)
    s = sub.add_parser("serve", help="run the /api/ai routes over HTTP")
    s.add_argument("--port", type=int, default=8765)
    s.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--json", action="store_true", help="print raw JSON")
    args = ap.parse_args()

    if args.cmd == "serve":
        print(f"Spitify DJ on http://{args.host}:{args.port} ({'agent' if agent_ready() else 'rules only'})")
        ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
        return
    result = playlist(args.prompt, args.size) if args.cmd == "playlist" else dj({"id": args.track} if args.track else None,
                                                                                  request=args.request, count=args.count)
    print(json.dumps(result, indent=2) if args.json else "", end="")
    if not args.json:
        print_set(result)


if __name__ == "__main__":
    main()
