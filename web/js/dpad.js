/*
 * Strongly-connected spatial D-Pad navigation for Android TV style interfaces.
 * Every control on every screen is reachable, and no control (e.g. the lone `◀` Back button) can trap focus.
 */
(function () {
  'use strict';

  const root = (typeof window !== 'undefined' ? window : globalThis);
  root.FEU_TIMER = root.FEU_TIMER || {};

  /*
   * True spatial (beam) focus search, same metric as Android's FocusFinder:
   * 1. Candidates must lie in the pressed direction.
   * 2. Candidates overlapping the current control on the cross axis ("in beam") win over the rest.
   * 3. Best candidate minimises 13 * majorDistance^2 + minorOffset^2.
   * If nothing lies in that direction the search wraps to the opposite edge, so focus can never be trapped.
   */
  function describe(r, index) {
    return {
      index,
      left: r.left,
      right: r.right,
      top: r.top,
      bottom: r.bottom,
      cx: (r.left + r.right) / 2,
      cy: (r.top + r.bottom) / 2,
    };
  }

  function overlap(aMin, aMax, bMin, bMax) {
    return Math.min(aMax, bMax) - Math.max(aMin, bMin);
  }

  function pickBest(cur, items, dir, requireBeam) {
    const horizontal = dir === 'left' || dir === 'right';
    const scored = [];
    for (const c of items) {
      if (c.index === cur.index) continue;
      let inDirection;
      let major;
      // Candidate must lie (almost) entirely beyond the current control in the pressed direction
      const SLACK = 8;
      if (dir === 'right') { inDirection = c.left >= cur.right - SLACK && c.cx > cur.cx; major = Math.max(0, c.left - cur.right); }
      else if (dir === 'left') { inDirection = c.right <= cur.left + SLACK && c.cx < cur.cx; major = Math.max(0, cur.left - c.right); }
      else if (dir === 'down') { inDirection = c.top >= cur.bottom - SLACK && c.cy > cur.cy; major = Math.max(0, c.top - cur.bottom); }
      else { inDirection = c.bottom <= cur.top + SLACK && c.cy < cur.cy; major = Math.max(0, cur.top - c.bottom); }
      if (!inDirection) continue;

      const crossOverlap = horizontal
        ? overlap(cur.top, cur.bottom, c.top, c.bottom)
        : overlap(cur.left, cur.right, c.left, c.right);
      const inBeam = crossOverlap > 0;
      if (requireBeam && !inBeam) continue;
      const minor = horizontal ? Math.abs(c.cy - cur.cy) : Math.abs(c.cx - cur.cx);
      const edgeGap = inBeam ? 0 : Math.abs(crossOverlap);
      scored.push({ index: c.index, inBeam, score: 13 * major * major + (inBeam ? minor : edgeGap + minor * 0.25) ** 2 });
    }
    if (scored.length === 0) return -1;
    const beam = scored.filter((x) => x.inBeam);
    const pool = beam.length ? beam : scored;
    pool.sort((x, y) => x.score - y.score);
    return pool[0].index;
  }

  // Reading order (rows top-to-bottom, left-to-right): Right at the end of a row moves to the first
  // control of the next row, Left at the start moves to the last control of the previous row.
  function readingOrder(items) {
    const sorted = [...items].sort((a, b) => a.cy - b.cy);
    const rows = [];
    for (const it of sorted) {
      const row = rows[rows.length - 1];
      const h = Math.min(it.bottom - it.top, row ? row.minH : Infinity);
      if (row && Math.abs(it.cy - row.cy) <= Math.max(12, h * 0.5)) {
        row.items.push(it);
        row.cy = row.items.reduce((s, x) => s + x.cy, 0) / row.items.length;
        row.minH = Math.min(row.minH, it.bottom - it.top);
      } else {
        rows.push({ cy: it.cy, minH: it.bottom - it.top, items: [it] });
      }
    }
    rows.forEach((r) => r.items.sort((a, b) => a.cx - b.cx));
    return rows.flatMap((r) => r.items);
  }

  function wrapTarget(cur, items, dir) {
    const others = items.filter((c) => c.index !== cur.index);
    if (others.length === 0) return cur.index;
    const horizontal = dir === 'left' || dir === 'right';
    if (horizontal) {
      const order = readingOrder(items);
      const at = order.findIndex((c) => c.index === cur.index);
      const step = dir === 'right' ? 1 : -1;
      return order[(at + step + order.length) % order.length].index;
    }
    // Pick the opposite edge, then the control closest on the cross axis
    const edgeValue = (c) => (dir === 'right' ? c.cx : dir === 'left' ? -c.cx : dir === 'down' ? c.cy : -c.cy);
    const minEdge = Math.min(...others.map(edgeValue));
    const band = others.filter((c) => edgeValue(c) - minEdge <= 24);
    band.sort((x, y) => (horizontal ? Math.abs(x.cy - cur.cy) - Math.abs(y.cy - cur.cy) : Math.abs(x.cx - cur.cx) - Math.abs(y.cx - cur.cx)));
    return band[0].index;
  }

  /*
   * groups (optional): one id per control, e.g. the card/row it belongs to. Controls in the SAME group
   * are preferred in every direction, so arrows move inside a card/row first and only then leave it.
   */
  function findNext(rects, currentIndex, dir, groups) {
    if (!Array.isArray(rects) || rects.length === 0) return -1;
    if (rects.length === 1) return 0;
    if (currentIndex < 0 || currentIndex >= rects.length) return 0;
    const withIndex = rects.map((r, i) => describe(r, i));
    const cur = withIndex[currentIndex];
    if (Array.isArray(groups) && groups[currentIndex] != null) {
      const sameGroup = withIndex.filter((c) => groups[c.index] === groups[currentIndex]);
      const inGroup = pickBest(cur, sameGroup, dir, false);
      if (inGroup >= 0) return inGroup;
    }
    const horizontalMove = dir === 'left' || dir === 'right';
    // Leaving the card/row sideways must hit something actually beside us (same band); otherwise read on
    const best = pickBest(cur, withIndex, dir, horizontalMove);
    if (best >= 0) return best;
    return wrapTarget(cur, withIndex, dir);
  }

  // Legacy 3x5 helper retained for backwards compatibility with existing unit tests
  function moveQuickGrid(index, dir) {
    const QUICK_COUNT = 15;
    const QUICK_COLS = 5;
    const row = Math.floor(index / QUICK_COLS);
    const col = index % QUICK_COLS;
    if (dir === 'left') return (index - 1 + QUICK_COUNT) % QUICK_COUNT;
    if (dir === 'right') return (index + 1) % QUICK_COUNT;
    if (dir === 'up') return row > 0 ? (row - 1) * QUICK_COLS + col : index;
    if (dir === 'down') return row < 2 ? (row + 1) * QUICK_COLS + col : 'BAR';
    return index;
  }

  root.FEU_TIMER.DpadGrid = {
    findNext,
    moveQuickGrid,
  };
})();
