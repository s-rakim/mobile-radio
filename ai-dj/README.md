# Spitify DJ (Python)

The same DJ brain as the server's `services/aiDj.js`, in one Python file with no packages to install. Use it to try prompts from the command line, tune the rules, or run the DJ on its own.

```
export AI_API_KEY=your-key
export AI_MODEL=the-model-name-your-provider-lists
export AI_BASE_URL=https://api.openai.com/v1   # or any OpenAI-style endpoint

python spitify_dj.py playlist "sunset deep house for a rooftop" --size 25
python spitify_dj.py dj --track <audius track id> --request "pick it up a bit"
python spitify_dj.py serve --port 8765
```

Leave the key out and it runs on rules only.

## How it picks

1. **Digging.** The model gets three tools: search Audius, trending by genre, and an artist's most played songs. It searches as many times as it needs (up to eight rounds), reading back each song's genre, mood, BPM, key and play count.
2. **Shortlist.** It rates every pick's energy from 1 to 10 and how well it fits the request, chooses an energy arc (journey, build, peak, steady, wind-down or wave) and writes a short on-air intro.
3. **Running order.** The sequencer chooses the final songs from the shortlist with a beam search, scoring every slot on:
   - distance from the arc's target energy at that point in the set
   - tempo gap to the previous song (3% is seamless, over 10% needs a trick), half and double time allowed
   - Camelot key distance (same key, one step, or relative major/minor are clean)
   - genre jumps, energy jumps of more than about two points, and the same artist too close together
   Then it swaps pairs while that makes the set flow better.
4. **Mix notes.** Each song says how to get into it: a 32-bar blend when tempo and key line up, a cut on the drop for a big lift, an echo out when the tempo jump is large, a filter swap when the keys clash.

`serve` exposes `/api/ai/status`, `/api/ai/playlist` and `/api/ai/dj` with the same request and reply shapes as the Node server. Set `SPITIFY_DJ_TOKEN` to require `Authorization: Bearer <token>` if it's reachable from outside your PC.

Audius doesn't have tempo and key for every upload. When they're missing, energy comes from genre, mood and the title, and the model's own rating carries more weight.
