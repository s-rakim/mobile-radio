# Spitify Web

A small browser player that searches Audius (and Jamendo, if you give it a key) and streams tracks with a Spitify-style now playing bar.

## Run it

It's plain HTML, CSS and JavaScript. Open `index.html` in a browser, or serve the folder:

```sh
cd web
python3 -m http.server 8080
```

## Jamendo

Audius needs no key. Jamendo does: grab a free client id from https://devportal.jamendo.com and put it in `config.js`:

```js
window.SPITIFY_CONFIG = { jamendoClientId: 'your-id' };
```

With a key set, results from both catalogs are mixed together and the All / Jamendo / Audius chips appear.

## What's in it

- Search as you type, with stale requests cancelled so results never jump back to an old query
- Loading skeletons, empty, error and partial-source states, plus a retry button
- One click handler for the whole list; the playing row is highlighted
- Prev / play / next, a seek bar, Space to pause, and media key / lock screen controls
- Track titles and artist names are escaped before they hit the page
