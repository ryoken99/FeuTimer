/*
 * Builds the showcase soundtrack: the real app cues captured from the TV page (voice clips, horn/bell/whistle, pips),
 * synthesized UI sounds (whoosh / tap / key / d-pad / hit) and a generated music bed that ducks under every cue.
 */
const fs = require('fs');

const SR = 48000;

function decodeBuffers(list) {
  const map = new Map();
  for (const b of list) {
    const raw = Buffer.from(b.b64, 'base64');
    const i16 = new Int16Array(raw.buffer, raw.byteOffset, Math.floor(raw.length / 2));
    const ratio = b.rate / SR;
    const outLen = Math.floor(i16.length / ratio);
    const out = new Float32Array(outLen);
    for (let i = 0; i < outLen; i++) {
      const pos = i * ratio;
      const i0 = Math.floor(pos);
      const f = pos - i0;
      const a = i16[i0] / 32767;
      const c = (i16[Math.min(i16.length - 1, i0 + 1)] || 0) / 32767;
      out[i] = a + (c - a) * f;
    }
    map.set(b.id, out);
  }
  return map;
}

function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 0x100000000) * 2 - 1;
}

// ------------------------------------------------------------------ UI sound synths (mono Float32 at SR)
function synthWhoosh() {
  const n = Math.floor(SR * 0.85);
  const out = new Float32Array(n);
  const r = rng(11);
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const t = i / n;
    const env = Math.sin(Math.PI * Math.min(1, t * 1.15)) ** 2 * (1 - t * 0.3);
    const cutoff = 0.02 + 0.5 * t * t;
    lp += (r() - lp) * cutoff;
    out[i] = lp * env * 0.55;
  }
  return out;
}
function synthTap() {
  const n = Math.floor(SR * 0.16);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const f = 520 * Math.exp(-t * 14) + 180;
    out[i] = Math.sin(2 * Math.PI * f * t) * Math.exp(-t * 26) * 0.55;
  }
  return out;
}
function synthKey(seed) {
  const n = Math.floor(SR * 0.035);
  const out = new Float32Array(n);
  const r = rng(seed);
  let prev = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const x = r();
    prev = prev * 0.45 + x * 0.55;
    out[i] = (x - prev) * Math.exp(-t * 130) * 0.5;
  }
  return out;
}
function synthDpad() {
  const n = Math.floor(SR * 0.07);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    out[i] = Math.sin(2 * Math.PI * 740 * t) * Math.exp(-t * 55) * 0.4;
  }
  return out;
}
function synthHit() {
  const n = Math.floor(SR * 2.6);
  const out = new Float32Array(n);
  const r = rng(77);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const boom = Math.sin(2 * Math.PI * (52 + 70 * Math.exp(-t * 6)) * t) * Math.exp(-t * 1.5);
    const air = r() * Math.exp(-t * 9) * 0.35;
    const shim = (Math.sin(2 * Math.PI * 440 * t) + 0.5 * Math.sin(2 * Math.PI * 660 * t)) * Math.exp(-t * 2.2) * 0.07 * Math.min(1, t / 0.05);
    out[i] = boom * 0.9 + air + shim;
  }
  return out;
}

// ------------------------------------------------------------------ music bed (Am - F - C - G pad, bass, soft pulse)
function renderMusic(len, pulseFromSec) {
  const L = new Float32Array(len);
  const R = new Float32Array(len);
  const bpm = 84;
  const beat = 60 / bpm;
  const bar = beat * 4;
  const chords = [
    { root: 55.0, notes: [110.0, 130.81, 164.81] }, // Am
    { root: 43.65, notes: [87.31, 130.81, 174.61] }, // F
    { root: 65.41, notes: [130.81, 164.81, 196.0] }, // C
    { root: 49.0, notes: [98.0, 146.83, 196.0] }, // G
  ];
  const chordLen = bar * 2;
  const total = len / SR;
  for (let c = 0; c * chordLen < total + chordLen; c++) {
    const ch = chords[c % chords.length];
    const s0 = Math.floor(c * chordLen * SR);
    const dur = Math.floor((chordLen + 1.6) * SR);
    for (let i = 0; i < dur && s0 + i < len; i++) {
      const t = i / SR;
      const env = Math.min(1, t / 1.4) * Math.min(1, (dur / SR - t) / 1.6);
      let padL = 0, padR = 0;
      ch.notes.forEach((f, k) => {
        const det = 1 + (k - 1) * 0.0022;
        const v = Math.sin(2 * Math.PI * f * det * t) + 0.32 * Math.sin(2 * Math.PI * 2 * f * det * t + k) + 0.12 * Math.sin(2 * Math.PI * 3 * f * t);
        const pan = (k - 1) * 0.35;
        padL += v * (1 - pan) * 0.5;
        padR += v * (1 + pan) * 0.5;
      });
      const bass = Math.sin(2 * Math.PI * ch.root * t) * 0.9;
      const trem = 0.82 + 0.18 * Math.sin(2 * Math.PI * 0.17 * (s0 + i) / SR);
      L[s0 + i] += (padL * 0.045 + bass * 0.085) * env * trem;
      R[s0 + i] += (padR * 0.045 + bass * 0.085) * env * trem;
    }
  }
  // soft kick on every beat once the combat section begins + off-beat tick
  if (pulseFromSec != null) {
    const r = rng(5);
    for (let b = Math.ceil(pulseFromSec / beat); b * beat < total; b++) {
      const s0 = Math.floor(b * beat * SR);
      const kd = Math.floor(SR * 0.28);
      for (let i = 0; i < kd && s0 + i < len; i++) {
        const t = i / SR;
        const k = Math.sin(2 * Math.PI * (46 + 90 * Math.exp(-t * 28)) * t) * Math.exp(-t * 11) * 0.13;
        L[s0 + i] += k;
        R[s0 + i] += k;
      }
      const s1 = s0 + Math.floor(beat * 0.5 * SR);
      for (let i = 0; i < Math.floor(SR * 0.03) && s1 + i < len; i++) {
        const h = r() * Math.exp(-(i / SR) * 160) * 0.018;
        L[s1 + i] += h;
        R[s1 + i] += h;
      }
    }
  }
  return { L, R };
}

function addMono(L, R, sample, atSec, gain, pan = 0) {
  const s0 = Math.floor(atSec * SR);
  if (s0 >= L.length) return;
  const gl = gain * (1 - Math.max(0, pan));
  const gr = gain * (1 + Math.min(0, pan));
  for (let i = 0; i < sample.length; i++) {
    const idx = s0 + i;
    if (idx < 0) continue;
    if (idx >= L.length) break;
    L[idx] += sample[i] * gl;
    R[idx] += sample[i] * gr;
  }
}

function writeWav(path, L, R) {
  const n = L.length;
  const buf = Buffer.alloc(44 + n * 4);
  buf.write('RIFF', 0); buf.writeUInt32LE(36 + n * 4, 4); buf.write('WAVE', 8);
  buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
  buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
  buf.write('data', 36); buf.writeUInt32LE(n * 4, 40);
  for (let i = 0; i < n; i++) {
    buf.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(L[i] * 32767))), 44 + i * 4);
    buf.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(R[i] * 32767))), 46 + i * 4);
  }
  fs.writeFileSync(path, buf);
}

function mixAudio({ exported, videoStartSec, videoEndSec, outPath }) {
  const len = Math.ceil((videoEndSec - videoStartSec + 0.5) * SR);
  const cuesL = new Float32Array(len), cuesR = new Float32Array(len);
  const uiL = new Float32Array(len), uiR = new Float32Array(len);
  const rel = (ms) => ms / 1000 - videoStartSec;

  // 1) real app cues, honouring stopAll() truncation
  const bufs = decodeBuffers(exported.buffers);
  const duck = new Float32Array(len).fill(1);
  for (const ev of exported.audio) {
    const src = bufs.get(ev.id);
    if (!src) continue;
    const at = rel(ev.t);
    let take = src.length;
    if (ev.stop != null) take = Math.max(0, Math.min(src.length, Math.floor((rel(ev.stop) - at) * SR)));
    if (take <= 0) continue;
    const seg = Float32Array.from(src.subarray(0, take));
    const fade = Math.min(seg.length, Math.floor(SR * 0.006));
    for (let i = 0; i < fade; i++) seg[seg.length - 1 - i] *= i / fade;
    addMono(cuesL, cuesR, seg, at, (ev.gain || 1) * 1.0);
    const a = Math.max(0, Math.floor((at - 0.04) * SR));
    const b = Math.min(len, Math.floor((at + take / SR + 0.18) * SR));
    if (take / SR > 0.12) for (let i = a; i < b; i++) duck[i] = Math.min(duck[i], 0.32);
  }
  // smooth the ducking curve (~60 ms boxcar, two passes)
  const win = Math.floor(SR * 0.06);
  for (let pass = 0; pass < 2; pass++) {
    let acc = 0;
    const tmp = new Float32Array(len);
    for (let i = 0; i < len; i++) {
      acc += duck[i];
      if (i >= win) acc -= duck[i - win];
      tmp[i] = acc / Math.min(win, i + 1);
    }
    duck.set(tmp);
  }

  // 2) UI sounds
  const whoosh = synthWhoosh(), tap = synthTap(), dpad = synthDpad(), hit = synthHit();
  const keys = [synthKey(3), synthKey(9), synthKey(21), synthKey(33)];
  let keyN = 0;
  for (const e of exported.sfx) {
    const at = rel(e.t);
    if (at < 0) continue;
    if (e.type === 'whoosh') addMono(uiL, uiR, whoosh, at - 0.1, 0.34, -0.2);
    else if (e.type === 'tap') addMono(uiL, uiR, tap, at, 0.2, 0.15);
    else if (e.type === 'key') addMono(uiL, uiR, keys[keyN++ % keys.length], at, 0.55, 0.1);
    else if (e.type === 'dpad') addMono(uiL, uiR, dpad, at, 0.28, -0.1);
    else if (e.type === 'hit') addMono(uiL, uiR, hit, at, 0.5);
  }

  // 3) music bed
  const duoMark = exported.sfx.find((e) => e.type === 'mark' && e.name === 'duo');
  const music = renderMusic(len, duoMark ? rel(duoMark.t) : null);
  const total = len / SR;
  for (let i = 0; i < len; i++) {
    const t = i / SR;
    const fade = Math.min(1, t / 2.2) * Math.min(1, (total - t) / 3.2);
    const g = fade * duck[i] * 1.5;
    music.L[i] *= g;
    music.R[i] *= g;
  }

  // 4) sum + limiter/normalize
  const L = new Float32Array(len), R = new Float32Array(len);
  let peak = 0;
  for (let i = 0; i < len; i++) {
    L[i] = cuesL[i] + uiL[i] + music.L[i];
    R[i] = cuesR[i] + uiR[i] + music.R[i];
    peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  }
  const norm = peak > 0.9 ? 0.9 / peak : 1;
  for (let i = 0; i < len; i++) {
    L[i] = Math.tanh(L[i] * norm * 1.05) * 0.95;
    R[i] = Math.tanh(R[i] * norm * 1.05) * 0.95;
  }
  writeWav(outPath, L, R);
  console.log(`Audio mixed: ${exported.audio.length} app cues, ${exported.sfx.length} ui events, peak ${peak.toFixed(2)}`);
}

module.exports = { mixAudio };
