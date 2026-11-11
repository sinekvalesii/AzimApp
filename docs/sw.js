// AZİM web sürümü: çevrimdışı çalışma için önbellek. Yeni sürüm yayınlarken VERSION değerini artır.
const VERSION = 'azim-v3.1.0';
const FILES = ['./', 'index.html', 'privacy.html', 'manifest.webmanifest', 'fonts/fonts.css',
  'fonts/archivo-latin-standard-normal.woff2', 'fonts/archivo-latin-ext-standard-normal.woff2',
  'fonts/jetbrains-mono-latin-500-normal.woff2', 'fonts/jetbrains-mono-latin-700-normal.woff2',
  'fonts/jetbrains-mono-latin-ext-500-normal.woff2', 'fonts/jetbrains-mono-latin-ext-700-normal.woff2',
  'icons/icon-192.png', 'icons/icon-512.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// Ağ öncelikli: güncel sürümü al, bağlantı yoksa önbellekten ver.
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(fetch(e.request).then(r => {
    const copy = r.clone(); caches.open(VERSION).then(c => c.put(e.request, copy)); return r;
  }).catch(() => caches.match(e.request).then(m => m || caches.match('index.html'))));
});
