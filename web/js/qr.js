/*
 * Zero-dependency ISO/IEC 18004 Byte-mode QR Code matrix encoder matching QrMatrixEncoder.kt
 * Renders scannable SVG QR badges on the TV Scoreboard screen.
 */
(function () {
  'use strict';

  const root = (typeof window !== 'undefined' ? window : globalThis);
  root.FEU_TIMER = root.FEU_TIMER || {};

  const SPECS = [
    { version: 1, size: 21, dataCw: 19, ecCw: 7, align: [] },
    { version: 2, size: 25, dataCw: 34, ecCw: 10, align: [6, 18] },
    { version: 3, size: 29, dataCw: 55, ecCw: 15, align: [6, 22] },
    { version: 4, size: 33, dataCw: 80, ecCw: 20, align: [6, 26] },
  ];

  function gfMul(a, b) {
    let res = 0;
    for (let i = 0; i < 8; i++) {
      if (b & 1) res ^= a;
      const hi = a & 0x80;
      a = (a << 1) & 0xff;
      if (hi) a ^= 0x1d;
      b >>>= 1;
    }
    return res;
  }

  function computeRsEc(data, ecLen) {
    let gen = [1];
    let r = 1;
    for (let k = 0; k < ecLen; k++) {
      const next = new Array(gen.length + 1).fill(0);
      for (let i = 0; i < gen.length; i++) {
        next[i] ^= gen[i];
        next[i + 1] ^= gfMul(gen[i], r);
      }
      gen = next;
      r = gfMul(r, 2);
    }
    const rem = new Array(ecLen).fill(0);
    for (const b of data) {
      const factor = b ^ rem[0];
      rem.shift();
      rem.push(0);
      for (let i = 0; i < ecLen; i++) rem[i] ^= gfMul(gen[i + 1], factor);
    }
    return rem;
  }

  function encodeMatrix(text) {
    const bytes = Array.from(String(text), (c) => c.charCodeAt(0) & 0xff);
    const spec = SPECS.find((s) => bytes.length + 2 <= s.dataCw) || SPECS[SPECS.length - 1];
    const payload = bytes.slice(0, spec.dataCw - 2);

    const bits = [];
    const push = (val, len) => {
      for (let i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1);
    };
    push(0b0100, 4);
    push(payload.length, 8);
    for (const b of payload) push(b, 8);
    const maxBits = spec.dataCw * 8;
    for (let i = 0; i < 4 && bits.length < maxBits; i++) bits.push(0);
    while (bits.length % 8 !== 0) bits.push(0);
    const pads = [0xec, 0x11];
    let p = 0;
    while (bits.length < maxBits) push(pads[p++ % 2], 8);

    const dataCw = [];
    for (let i = 0; i < spec.dataCw; i++) {
      let v = 0;
      for (let b = 0; b < 8; b++) v = (v << 1) | bits[i * 8 + b];
      dataCw.push(v);
    }
    const allCw = dataCw.concat(computeRsEc(dataCw, spec.ecCw));

    const n = spec.size;
    const m = Array.from({ length: n }, () => new Array(n).fill(false));
    const fn = Array.from({ length: n }, () => new Array(n).fill(false));

    const placeFinder = (top, left) => {
      for (let dr = -1; dr <= 7; dr++) {
        for (let dc = -1; dc <= 7; dc++) {
          const rr = top + dr;
          const cc = left + dc;
          if (rr < 0 || rr >= n || cc < 0 || cc >= n) continue;
          const inOuter = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
          m[rr][cc] = inOuter && (dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
          fn[rr][cc] = true;
        }
      }
    };
    placeFinder(0, 0);
    placeFinder(0, n - 7);
    placeFinder(n - 7, 0);

    for (let i = 8; i < n - 8; i++) {
      if (!fn[6][i]) { m[6][i] = i % 2 === 0; fn[6][i] = true; }
      if (!fn[i][6]) { m[i][6] = i % 2 === 0; fn[i][6] = true; }
    }

    for (const cy of spec.align) {
      for (const cx of spec.align) {
        if ((cy === 6 && cx === 6) || (cy === 6 && cx === n - 7) || (cy === n - 7 && cx === 6)) continue;
        for (let dr = -2; dr <= 2; dr++) {
          for (let dc = -2; dc <= 2; dc++) {
            m[cy + dr][cx + dc] = Math.max(Math.abs(dr), Math.abs(dc)) !== 1;
            fn[cy + dr][cx + dc] = true;
          }
        }
      }
    }

    for (let i = 0; i <= 8; i++) {
      if (i !== 6) { fn[8][i] = true; fn[i][8] = true; }
    }
    for (let i = 0; i <= 7; i++) fn[8][n - 1 - i] = true;
    for (let i = 0; i <= 6; i++) fn[n - 1 - i][8] = true;
    m[4 * spec.version + 9][8] = true;
    fn[4 * spec.version + 9][8] = true;

    let bitIdx = 0;
    let right = n - 1;
    let up = true;
    while (right >= 1) {
      if (right === 6) right = 5;
      for (let step = 0; step < n; step++) {
        const rr = up ? n - 1 - step : step;
        for (let off = 0; off < 2; off++) {
          const cc = right - off;
          if (!fn[rr][cc]) {
            const dark = bitIdx < allCw.length * 8
              ? ((allCw[bitIdx >> 3] >>> (7 - (bitIdx & 7))) & 1) !== 0
              : false;
            m[rr][cc] = (rr + cc) % 2 === 0 ? !dark : dark; // Mask 0
            bitIdx++;
          }
        }
      }
      up = !up;
      right -= 2;
    }

    // Format bits for ECC L (01), Mask 0 (000)
    const data = 0b01000;
    let rem = data << 10;
    for (let i = 14; i >= 10; i--) {
      if (rem & (1 << i)) rem ^= 0x537 << (i - 10);
    }
    const fmt = ((data << 10) | rem) ^ 0x5412;
    const fbit = (i) => ((fmt >>> i) & 1) !== 0;

    for (let i = 0; i <= 5; i++) m[8][i] = fbit(14 - i);
    m[8][7] = fbit(8);
    m[8][8] = fbit(7);
    m[7][8] = fbit(6);
    for (let i = 9; i <= 14; i++) m[14 - i][8] = fbit(14 - i);
    for (let i = 0; i <= 6; i++) m[n - 1 - i][8] = fbit(14 - i);
    for (let i = 7; i <= 14; i++) m[8][n - 15 + i] = fbit(14 - i);

    return m;
  }

  function toSvg(url, sizePx = 38) {
    const matrix = encodeMatrix(url);
    const n = matrix.length;
    let rects = '';
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        if (matrix[r][c]) rects += `<rect x="${c}" y="${r}" width="1.05" height="1.05" fill="#000"/>`;
      }
    }
    return `<svg width="${sizePx}" height="${sizePx}" viewBox="-1.5 -1.5 ${n + 3} ${n + 3}" xmlns="http://www.w3.org/2000/svg" style="background:#fff;border-radius:4px;display:block;flex:none">${rects}</svg>`;
  }

  root.FEU_TIMER.Qr = { encodeMatrix, toSvg };
})();
