(async () => {
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const vp = document.getElementById('tv-viewport');
  const press = (k) => window.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));
  const label = (e) => {
    if (!e) return null;
    return e.getAttribute('data-builder-cmd') || (e.hasAttribute('data-builder-idx') ? 'idx' + e.getAttribute('data-builder-idx') : null) ||
      (e.hasAttribute('data-save-slot') ? 'save' + e.getAttribute('data-save-slot') : null) ||
      (e.hasAttribute('data-tpl-idx') ? 'tpl' + e.getAttribute('data-tpl-idx') : null) ||
      (e.hasAttribute('data-preset-idx') ? 'preset' + e.getAttribute('data-preset-idx') : null) || e.textContent.trim();
  };
  Array.from(document.querySelectorAll('.bottom-bar .tv-focusable')).find((x) => x.textContent.includes('Rondas')).click();
  await wait(400);
  const setFocus = (sel) => {
    vp.querySelectorAll('.is-dpad-focused').forEach((n) => n.classList.remove('is-dpad-focused'));
    vp.querySelector(sel).classList.add('is-dpad-focused');
  };
  const cases = [
    ['rest -1m  Down ', '[data-builder-cmd="rest-60"]', 'ArrowDown', 'idx9'],
    ['rest -1m  Right', '[data-builder-cmd="rest-60"]', 'ArrowRight', 'idx10'],
    ['rest  -   Right', '[data-builder-idx="9"]', 'ArrowRight', 'idx10'],
    ['rest +1m  Down ', '[data-builder-cmd="rest+60"]', 'ArrowDown', 'idx9'],
    ['work -1m  Down ', '[data-builder-cmd="work-60"]', 'ArrowDown', 'idx7'],
    ['work  -   Right', '[data-builder-idx="7"]', 'ArrowRight', 'idx8'],
    ['reps  inf Down ', '[data-builder-cmd="toggle-endless"]', 'ArrowDown', 'idx5'],
    ['reps  -   Right', '[data-builder-idx="5"]', 'ArrowRight', 'idx6'],
    ['play      Down ', '[data-builder-idx="10"]', 'ArrowDown', null],
  ];
  const out = {};
  document.body.classList.add('dpad-mode');
  for (const [name, sel, key, expect] of cases) {
    setFocus(sel);
    press(key);
    await wait(40);
    const got = label(vp.querySelector('.is-dpad-focused'));
    out[name] = expect === null ? got : got === expect ? 'OK ' + got : 'FAIL got ' + got + ' expected ' + expect;
  }
  return JSON.stringify(out, null, 1);
})()
