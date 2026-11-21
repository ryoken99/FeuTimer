/*
 * FEU BJJ showcase video recorder.
 *   1. Opens web/showcase/index.html (TV simulator + phone console, both live) in headless Chromium.
 *   2. Captures every frame through CDP screencast with exact wall-clock timestamps.
 *   3. Pulls the real audio cues (voice + synthesized horn/bell/whistle) the TV page scheduled, with their timing.
 *   4. Mixes cues + UI sounds + a ducked music bed (mix-audio.cjs) and encodes an H.264/AAC mp4 with ffmpeg.
 * Requires the dev server (`node web/serve.cjs`) on :8788.
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');
const ffmpeg = require('ffmpeg-static');
const { mixAudio } = require('./mix-audio.cjs');

const OUT = path.join(__dirname, 'out');
const FRAMES = path.join(OUT, 'frames');
const FINAL = process.argv[2] || path.resolve(__dirname, '../../dist/FeuTimer-v1.3.4-showcase.mp4');
const URL = 'http://127.0.0.1:8788/showcase/index.html';

(async () => {
  fs.rmSync(OUT, { recursive: true, force: true });
  fs.mkdirSync(FRAMES, { recursive: true });

  const browser = await chromium.launch({
    args: ['--autoplay-policy=no-user-gesture-required', '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows'],
  });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.error('[pageerror]', e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.error('[console.error]', m.text()); });
  await page.goto(URL, { waitUntil: 'load' });
  await page.waitForTimeout(800);

  const cdp = await page.context().newCDPSession(page);
  const frames = [];
  let n = 0;
  cdp.on('Page.screencastFrame', async ({ data, metadata, sessionId }) => {
    const file = path.join(FRAMES, `f${String(++n).padStart(6, '0')}.jpg`);
    fs.writeFileSync(file, Buffer.from(data, 'base64'));
    frames.push({ file, ts: metadata.timestamp });
    try { await cdp.send('Page.screencastFrameAck', { sessionId }); } catch (_) {}
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 90, maxWidth: 1920, maxHeight: 1080, everyNthFrame: 1 });
  await page.waitForTimeout(300);

  console.log('Recording show…');
  const t0 = Date.now();
  await page.evaluate(() => window.runShow());
  await page.waitForTimeout(600);
  await cdp.send('Page.stopScreencast');
  console.log(`Show finished in ${((Date.now() - t0) / 1000).toFixed(1)}s, ${frames.length} frames`);

  const exported = await page.evaluate(() => window.exportAudio());
  await browser.close();
  if (frames.length < 10) throw new Error('Too few frames captured');

  // ---- video: variable-rate frames -> constant 30 fps via concat durations
  const tStart = frames[0].ts;
  const tEnd = frames[frames.length - 1].ts + 0.5;
  const list = [];
  for (let i = 0; i < frames.length; i++) {
    const dur = Math.max(0.001, (i + 1 < frames.length ? frames[i + 1].ts : tEnd) - frames[i].ts);
    list.push(`file '${frames[i].file}'`, `duration ${dur.toFixed(5)}`);
  }
  list.push(`file '${frames[frames.length - 1].file}'`);
  const listPath = path.join(OUT, 'frames.txt');
  fs.writeFileSync(listPath, list.join('\n'));
  const silent = path.join(OUT, 'video.mp4');
  execFileSync(ffmpeg, ['-y', '-f', 'concat', '-safe', '0', '-i', listPath, '-vf', 'fps=30,format=yuv420p', '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', silent], { stdio: 'inherit' });

  // ---- audio
  const wavPath = path.join(OUT, 'audio.wav');
  mixAudio({ exported, videoStartSec: tStart, videoEndSec: tEnd, outPath: wavPath });

  fs.mkdirSync(path.dirname(FINAL), { recursive: true });
  execFileSync(ffmpeg, ['-y', '-i', silent, '-i', wavPath, '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', FINAL], { stdio: 'inherit' });
  console.log('DONE ->', FINAL, `(${((tEnd - tStart)).toFixed(1)}s)`);
})().catch((e) => { console.error(e); process.exit(1); });
