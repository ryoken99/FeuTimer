(async () => {
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const press = (key) => window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
  const vp = document.getElementById('tv-viewport');
  const { DpadGrid } = window.FEU_TIMER;
  const clickBar = (t) => Array.from(document.querySelectorAll('.bottom-bar .tv-focusable')).find((x) => x.textContent.includes(t)).click();

  // Exhaustive BFS over the REAL live DOM geometry using the same engine the keys use
  const reachability = () => {
    const { nodes, rects, groups } = window.FEU_TIMER.__focusProbe();
    let worstStart = 0;
    let stronglyConnected = true;
    for (let s = 0; s < rects.length; s++) {
      const seen = new Set([s]);
      const q = [s];
      while (q.length) {
        const c = q.shift();
        for (const d of ['up', 'down', 'left', 'right']) {
          const n = DpadGrid.findNext(rects, c, d, groups);
          if (!seen.has(n)) { seen.add(n); q.push(n); }
        }
      }
      if (seen.size !== rects.length) { stronglyConnected = false; worstStart = s; break; }
    }
    return { controls: rects.length, everyControlReachesEveryOther: stronglyConnected, failingStart: stronglyConnected ? null : nodes[worstStart].textContent.trim() };
  };

  const out = [];
  const screens = [['SETTINGS', '⚙'], ['SCOREBOARD', 'Placar'], ['ROUNDS', 'Rondas'], ['QUICK_TIMERS', 'Tempos']];
  for (const [name, label] of screens) {
    clickBar(label);
    await wait(350);
    out.push({ name, ...reachability() });
    press('Escape');
    await wait(350);
  }
  return JSON.stringify(out);
})()
