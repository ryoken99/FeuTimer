/*
 * WebAudio + PT-PT Vocal Coach player matching WavSynthesizer.kt & TimerSoundPlayer.kt:
 * Preloads the 21 studio PT-PT MP3 announcements (works over both http:// and file://)
 * and layers them cleanly with WebAudio tatami horns, bells, whistles, and wooden clappers.
 */
(function () {
  'use strict';

  const root = (typeof window !== 'undefined' ? window : globalThis);
  root.FEU_TIMER = root.FEU_TIMER || {};

  const SAMPLE_RATE = 44100;
  let ctx = null;
  const buffers = new Map();
  const voiceElements = new Map();
  // Voice clips decoded to WebAudio buffers: they play on the same unlocked context as the horns, scheduled on the
  // audio clock, so remote-started matches (no click on this tab) and Safari per-element autoplay rules cannot mute them.
  const voiceBuffers = new Map();
  let activeNodes = [];
  let activeTimers = [];
  let activeHtmlAudio = null;

  const VOICE_FILES = [
    'count_01', 'count_02', 'count_03', 'count_04', 'count_05',
    'count_06', 'count_07', 'count_08', 'count_09', 'count_10',
    'warn_halfway', 'warn_2m', 'warn_1m', 'warn_50s', 'warn_30s', 'warn_10s',
    'prep_ready', 'fight_start', 'round_end', 'rest_start', 'session_end',
  ];

  function preloadVoiceClips() {
    if (typeof window === 'undefined' || voiceElements.size > 0) return;
    for (const key of VOICE_FILES) {
      try {
        const a = new window.Audio(`assets/voice/${key}.mp3`);
        a.preload = 'auto';
        voiceElements.set(key, a);
      } catch (_) {}
    }
  }

  function decode(audioCtx, arrayBuffer) {
    return new Promise((resolve, reject) => {
      const p = audioCtx.decodeAudioData(arrayBuffer, resolve, reject);
      if (p && typeof p.then === 'function') p.then(resolve, reject);
    });
  }

  function loadVoiceBuffers(audioCtx) {
    if (typeof window === 'undefined' || window.location.protocol === 'file:') return;
    for (const key of VOICE_FILES) {
      fetch(`assets/voice/${key}.mp3`)
        .then((r) => r.arrayBuffer())
        .then((ab) => decode(audioCtx, ab))
        .then((buf) => voiceBuffers.set(key, buf))
        .catch(() => {});
    }
  }

  function ensureContext() {
    if (typeof window === 'undefined') return null;
    preloadVoiceClips();
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return null;
    if (!ctx) {
      ctx = new AudioCtx();
      buildAllBuffers(ctx);
      loadVoiceBuffers(ctx);
      ctx.onstatechange = () => {
        window.dispatchEvent(new CustomEvent(ctx.state === 'running' ? 'feu-audio-unlocked' : 'feu-audio-locked'));
      };
    }
    if (ctx.state === 'suspended') {
      ctx.resume().catch(() => {});
    }
    return ctx;
  }

  function buildAllBuffers(audioCtx) {
    const pip = makeBuffer(audioCtx, 0.20, (t) => {
      const env = Math.min(1, t / 0.005) * Math.exp(-t * 9.5);
      return (0.82 * Math.sin(2 * Math.PI * 950 * t) + 0.18 * Math.sin(2 * Math.PI * 1900 * t)) * env * 0.65;
    });

    let seed = 0xfe0b11;
    const nextNoise = () => {
      seed = (seed * 1664525 + 1013904223) >>> 0;
      return (seed / 0x80000000) - 1.0;
    };

    const singleClapSamples = new Float32Array(Math.floor(SAMPLE_RATE * 0.22));
    for (let k = 0; k < singleClapSamples.length; k++) {
      const t = k / SAMPLE_RATE;
      const click = nextNoise() * Math.exp(-t * 140) * 0.55;
      const m1 = Math.sin(2 * Math.PI * 2150 * t) * Math.exp(-t * 32) * 0.48;
      const m2 = Math.sin(2 * Math.PI * 3980 * t) * Math.exp(-t * 45) * 0.36;
      const body = Math.sin(2 * Math.PI * 1120 * t) * Math.exp(-t * 24) * 0.25;
      singleClapSamples[k] = (click + m1 + m2 + body) * Math.min(1, t / 0.0015) * 0.62;
    }
    const clapSingle = fromFloatArray(audioCtx, singleClapSamples);

    const clapSamples = new Float32Array(Math.floor(SAMPLE_RATE * 1.2));
    for (const offsetSec of [0.01, 0.42, 0.83]) {
      const start = Math.floor(offsetSec * SAMPLE_RATE);
      const len = Math.floor(0.26 * SAMPLE_RATE);
      for (let k = 0; k < len && start + k < clapSamples.length; k++) {
        const t = k / SAMPLE_RATE;
        const click = nextNoise() * Math.exp(-t * 140) * 0.55;
        const m1 = Math.sin(2 * Math.PI * 2150 * t) * Math.exp(-t * 32) * 0.48;
        const m2 = Math.sin(2 * Math.PI * 3980 * t) * Math.exp(-t * 45) * 0.36;
        const body = Math.sin(2 * Math.PI * 1120 * t) * Math.exp(-t * 24) * 0.25;
        clapSamples[start + k] += (click + m1 + m2 + body) * Math.min(1, t / 0.0015) * 0.72;
      }
    }
    const clapTriple = fromFloatArray(audioCtx, clapSamples);

    const hornStart = makeBuffer(audioCtx, 0.50, (t) => {
      const env = Math.min(1, t / 0.012) * Math.max(0, Math.min(1, (0.50 - t) / 0.08));
      return (0.56 * Math.sin(2 * Math.PI * 985 * t) + 0.26 * Math.sin(2 * Math.PI * 492.5 * t) + 0.18 * Math.sin(2 * Math.PI * 1477.5 * t)) * env * 0.72;
    });

    const endSamples = new Float32Array(Math.floor(SAMPLE_RATE * 2.25));
    for (const g of [0.0, 0.76, 1.52]) {
      for (const s of [0.0, 0.18]) {
        const start = Math.floor((g + s) * SAMPLE_RATE);
        const len = Math.floor(0.14 * SAMPLE_RATE);
        for (let k = 0; k < len && start + k < endSamples.length; k++) {
          const t = k / SAMPLE_RATE;
          const env = Math.min(1, t / 0.004) * Math.max(0, Math.min(1, (0.14 - t) / 0.015));
          endSamples[start + k] += (0.68 * Math.sin(2 * Math.PI * 3200 * t) + 0.32 * Math.sin(2 * Math.PI * 1600 * t)) * env * 0.72;
        }
      }
    }
    const hornEnd = fromFloatArray(audioCtx, endSamples);

    const makeBell = (strikes) => {
      const dur = strikes === 1 ? 1.15 : 1.95;
      const arr = new Float32Array(Math.floor(SAMPLE_RATE * dur));
      const offsets = strikes === 1 ? [0] : [0, 0.34, 0.68];
      for (const off of offsets) {
        const sIdx = Math.floor(off * SAMPLE_RATE);
        for (let k = 0; k < arr.length - sIdx; k++) {
          const t = k / SAMPLE_RATE;
          const att = Math.min(1, t / 0.002);
          const p =
            0.45 * Math.sin(2 * Math.PI * 820 * t) * Math.exp(-t * 3.4) +
            0.30 * Math.sin(2 * Math.PI * 1685 * t) * Math.exp(-t * 5.6) +
            0.16 * Math.sin(2 * Math.PI * 2590 * t) * Math.exp(-t * 8.8) +
            0.09 * Math.sin(2 * Math.PI * 3710 * t) * Math.exp(-t * 13.5);
          arr[sIdx + k] += p * att * 0.72;
        }
      }
      return fromFloatArray(audioCtx, arr);
    };

    const makeWhistle = (longBlast) => {
      const dur = longBlast ? 0.48 : 0.90;
      const arr = new Float32Array(Math.floor(SAMPLE_RATE * dur));
      const blasts = longBlast ? [[0, 0.44]] : [[0, 0.22], [0.30, 0.22], [0.60, 0.28]];
      for (const [st, ln] of blasts) {
        const sIdx = Math.floor(st * SAMPLE_RATE);
        const bLen = Math.floor(ln * SAMPLE_RATE);
        for (let k = 0; k < bLen && sIdx + k < arr.length; k++) {
          const t = k / SAMPLE_RATE;
          const env = Math.min(1, t / 0.012) * Math.max(0, Math.min(1, (ln - t) / 0.025));
          const trill = 0.72 + 0.28 * Math.sin(2 * Math.PI * 38 * t);
          const c = 0.55 * Math.sin(2 * Math.PI * 2820 * t) + 0.45 * Math.sin(2 * Math.PI * 2945 * t);
          arr[sIdx + k] += c * trill * env * 0.72;
        }
      }
      return fromFloatArray(audioCtx, arr);
    };

    buffers.set('PIP', pip);
    buffers.set('CLAP_SINGLE', clapSingle);
    buffers.set('CLAP_TRIPLE', clapTriple);
    buffers.set('CLASSIC_HORN:START', hornStart);
    buffers.set('CLASSIC_HORN:END', hornEnd);
    buffers.set('BELL_GONG:START', makeBell(1));
    buffers.set('BELL_GONG:END', makeBell(3));
    buffers.set('WHISTLE:START', makeWhistle(true));
    buffers.set('WHISTLE:END', makeWhistle(false));
  }

  function makeBuffer(audioCtx, durationSec, fn) {
    const length = Math.floor(SAMPLE_RATE * durationSec);
    const arr = new Float32Array(length);
    for (let i = 0; i < length; i++) {
      arr[i] = Math.max(-0.98, Math.min(0.98, fn(i / SAMPLE_RATE)));
    }
    return fromFloatArray(audioCtx, arr);
  }

  function fromFloatArray(audioCtx, arr) {
    const buffer = audioCtx.createBuffer(1, arr.length, SAMPLE_RATE);
    buffer.getChannelData(0).set(arr);
    return buffer;
  }

  function playBuffer(audioCtx, buf, gainValue = 1.0) {
    if (!audioCtx || !buf) return;
    const src = audioCtx.createBufferSource();
    const gain = audioCtx.createGain();
    gain.gain.value = gainValue;
    src.buffer = buf;
    src.connect(gain);
    gain.connect(audioCtx.destination);
    src.start(0);
    activeNodes.push(src);
  }

  function playVoiceClip(key, delayMs = 0) {
    const buf = voiceBuffers.get(key);
    if (buf && ctx) {
      const src = ctx.createBufferSource();
      src.buffer = buf;
      src.connect(ctx.destination);
      src.start(ctx.currentTime + Math.max(0, delayMs) / 1000);
      activeNodes.push(src);
      return;
    }
    const el = voiceElements.get(key);
    if (!el) return;
    const run = () => {
      try {
        if (activeHtmlAudio && activeHtmlAudio !== el) {
          activeHtmlAudio.pause();
          activeHtmlAudio.currentTime = 0;
        }
        el.currentTime = 0;
        activeHtmlAudio = el;
        el.play().catch(() => {});
      } catch (_) {}
    };
    if (delayMs <= 0) run();
    else activeTimers.push(setTimeout(run, delayMs));
  }

  function play(cueName, soundPack = 'CLASSIC_HORN', voiceEnabled = true) {
    const audioCtx = ensureContext();
    if (!cueName) return;
    if (audioCtx && audioCtx.state !== 'running') {
      // Browser autoplay policy: tell the UI that one click anywhere on this page is needed to enable sound
      window.dispatchEvent(new CustomEvent('feu-audio-locked'));
    }
    stopAll();

    if (cueName.startsWith('COUNT_')) {
      const n = Number(cueName.split('_')[1]);
      if (voiceEnabled && n >= 1 && n <= 10) {
        playBuffer(audioCtx, buffers.get('PIP'), 0.22);
        playVoiceClip(`count_${String(n).padStart(2, '0')}`, 0);
      } else {
        playBuffer(audioCtx, buffers.get('PIP'), 0.9);
      }
      return;
    }

    const warnMap = {
      WARN_HALFWAY: 'warn_halfway',
      WARN_2M: 'warn_2m',
      WARN_1M: 'warn_1m',
      WARN_50S: 'warn_50s',
      WARN_30S: 'warn_30s',
    };
    if (warnMap[cueName]) {
      if (voiceEnabled) {
        playBuffer(audioCtx, buffers.get('CLAP_SINGLE'), 0.52);
        playVoiceClip(warnMap[cueName], 130);
      } else {
        playBuffer(audioCtx, buffers.get('CLAP_TRIPLE'), 0.95);
      }
      return;
    }

    if (cueName === 'FIGHT_HORN') {
      const startBuf = buffers.get(`${soundPack}:START`) || buffers.get('CLASSIC_HORN:START');
      playBuffer(audioCtx, startBuf, voiceEnabled ? 0.68 : 0.95);
      if (voiceEnabled) playVoiceClip('fight_start', 240);
      return;
    }

    if (cueName === 'ROUND_END_TO_REST') {
      const endBuf = buffers.get(`${soundPack}:START`) || buffers.get('CLASSIC_HORN:START');
      if (voiceEnabled) {
        playBuffer(audioCtx, endBuf, 0.65);
        playVoiceClip('round_end', 240);
        playVoiceClip('rest_start', 820);
      } else {
        playBuffer(audioCtx, buffers.get(`${soundPack}:END`) || buffers.get('CLASSIC_HORN:END'), 0.95);
      }
      return;
    }

    if (cueName === 'MATCH_END') {
      if (voiceEnabled) {
        const endBuf = buffers.get(`${soundPack}:START`) || buffers.get('CLASSIC_HORN:START');
        playBuffer(audioCtx, endBuf, 0.68);
        playVoiceClip('round_end', 240);
      } else {
        playBuffer(audioCtx, buffers.get(`${soundPack}:END`) || buffers.get('CLASSIC_HORN:END'), 0.95);
      }
      return;
    }

    if (cueName === 'ROUND_END_ALARM') {
      if (voiceEnabled) {
        const endBuf = buffers.get(`${soundPack}:START`) || buffers.get('CLASSIC_HORN:START');
        playBuffer(audioCtx, endBuf, 0.68);
        playVoiceClip('round_end', 240);
        playVoiceClip('session_end', 820);
      } else {
        playBuffer(audioCtx, buffers.get(`${soundPack}:END`) || buffers.get('CLASSIC_HORN:END'), 0.95);
      }
      return;
    }

    if (cueName === 'WARNING_CLAP') {
      playBuffer(audioCtx, buffers.get('CLAP_TRIPLE'), 0.95);
    } else {
      playBuffer(audioCtx, buffers.get('PIP'), 0.9);
    }
  }

  function stopAll() {
    for (const t of activeTimers) clearTimeout(t);
    activeTimers = [];
    for (const node of activeNodes) {
      try { node.stop(); } catch (_) {}
    }
    activeNodes = [];
    if (activeHtmlAudio) {
      try {
        activeHtmlAudio.pause();
        activeHtmlAudio.currentTime = 0;
      } catch (_) {}
      activeHtmlAudio = null;
    }
  }

  preloadVoiceClips();
  // Create the (suspended) context + decode every voice clip immediately, and unlock on the first interaction anywhere
  try { ensureContext(); } catch (_) {}
  if (typeof document !== 'undefined') {
    for (const evt of ['pointerdown', 'keydown', 'touchstart']) {
      document.addEventListener(evt, () => { try { ensureContext(); } catch (_) {} }, { passive: true });
    }
  }

  root.FEU_TIMER.Audio = {
    unlock: ensureContext,
    play,
    stopAll,
    debug: () => ({ state: ctx ? ctx.state : 'none', voiceBuffers: voiceBuffers.size, htmlClips: voiceElements.size }),
  };
})();
