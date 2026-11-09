// Keeps the app shell on the device so Spitify opens instantly and installs as an app.
// Music and API calls always go to the network.
const SHELL = 'spitify-shell-v1';
const FILES = [
  './',
  'index.html',
  'style.css',
  'app.js',
  'api.js',
  'manifest.webmanifest',
  'assets/figtree.ttf',
  'assets/logo.png',
  'assets/favicon.png',
  'assets/icon-192.png',
  'assets/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Network first for the shell so a new deploy shows up on the next launch, cache when offline.
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  event.respondWith(
    fetch(event.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(SHELL).then((c) => c.put(event.request, copy));
        return res;
      })
      .catch(() => caches.match(event.request).then((hit) => hit || caches.match('index.html'))),
  );
});
