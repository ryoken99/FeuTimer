(async () => {
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const vp = document.getElementById('tv-viewport');
  const press = (k) => window.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));
  const label = (e) => (e ? (e.getAttribute('data-quick-opt') || (e.hasAttribute('data-quick-idx') ? 'time' + e.getAttribute('data-quick-idx') : null) || e.getAttribute('data-bar-action') || e.id || e.textContent.trim()) : null);
  Array.from(document.querySelectorAll('.bottom-bar .tv-focusable')).find((x) => x.textContent.includes('Tempos')).click();
  await wait(400);
  document.body.classList.add('dpad-mode');
  const focus = (sel) => {
    vp.querySelectorAll('.is-dpad-focused').forEach((n) => n.classList.remove('is-dpad-focused'));
    vp.querySelector(sel).classList.add('is-dpad-focused');
  };
  const cases = [
    ['stopwatch  Right', '#btn-stopwatch', 'ArrowRight', 'rep-'],
    ['rest +     Right', '[data-quick-opt="rest+"]', 'ArrowRight', 'time0'],
    ['time0      Left ', '[data-quick-idx="0"]', 'ArrowLeft', 'rest+'],
    ['rep -      Left ', '[data-quick-opt="rep-"]', 'ArrowLeft', 'btn-stopwatch'],
    ['rep -      Right', '[data-quick-opt="rep-"]', 'ArrowRight', 'rep+'],
    ['rep +      Right', '[data-quick-opt="rep+"]', 'ArrowRight', 'rest-'],
    ['rep -      Down ', '[data-quick-opt="rep-"]', 'ArrowDown', 'time0'],
    ['time0      Up   ', '[data-quick-idx="0"]', 'ArrowUp', 'rep-'],
    ['stopwatch  Down ', '#btn-stopwatch', 'ArrowDown', null],
  ];
  const out = {};
  for (const [name, sel, key, expect] of cases) {
    focus(sel);
    press(key);
    await wait(40);
    const got = label(vp.querySelector('.is-dpad-focused'));
    out[name] = expect === null ? got : got === expect ? 'OK ' + got : 'FAIL got ' + got + ' expected ' + expect;
  }
  return JSON.stringify(out, null, 1);
})()
