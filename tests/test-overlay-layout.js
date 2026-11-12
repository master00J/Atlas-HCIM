'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const read = (name) => fs.readFileSync(path.join(root, name), 'utf8');

const compact = read('assets/overlay.html');
const classic = read('assets/overlay-classic.html');
const webcam = read('assets/webcam-frame.html');
const script = read('assets/live.js');
const server = read('bridge/server.js');

for (const id of ['goalText','stepText','chapterText','percentText','stepCount',
  'overallFill','hcStatus','deathCount','liveIndicator']) {
  assert.match(compact, new RegExp('id="'+id+'"'), 'missing live binding '+id);
  assert.match(script, new RegExp(id), 'missing update logic for '+id);
}
assert.match(compact, /background:transparent/, 'game viewport must remain transparent');
assert.doesNotMatch(compact, /stream-frame\.png|class="art"/, 'compact mode must not render full-screen artwork');
assert.match(compact, /id="track"/);
assert.match(compact, /data-span="100"/);
assert.match(classic, /stream-frame\.png/, 'legacy overlay should remain available');
assert.match(webcam, /background:transparent/, 'webcam frame must be transparent');
assert.match(server, /'\/overlay-classic':'overlay-classic\.html'/);
assert.match(server, /'\/webcam-frame':'webcam-frame\.html'/);
console.log('PASS: compact overlay is transparent, live-wired and has optional legacy/webcam routes');
