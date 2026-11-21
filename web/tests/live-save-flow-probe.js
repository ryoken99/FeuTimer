(async () => {
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const vp = document.getElementById('tv-viewport');
  const press = (k) => window.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));
  const stored = () => JSON.parse(localStorage.getItem('feu.tv.simulator.v3')).presetSlots[1];
  const out = {};
  Array.from(document.querySelectorAll('.bottom-bar .tv-focusable')).find((x) => x.textContent.includes('Rondas')).click();
  await wait(450);
  const click = (sel, n = 1) => { for (let i = 0; i < n; i++) vp.querySelector(sel).click(); };
  click('[data-builder-idx="5"]', 3); // reps 2 -> 1... then +
  click('[data-builder-idx="4"]', 2);
  await wait(100);
  // focus Guardar 2 via keyboard
  document.body.classList.add('dpad-mode');
  for (let i = 0; i < 60; i++) {
    const f = vp.querySelector('.is-dpad-focused');
    if (f && f.getAttribute('data-save-slot') === '1') break;
    press(i % 5 === 4 ? 'ArrowDown' : 'ArrowRight');
    await wait(30);
  }
  out.focusedSlot = (vp.querySelector('.is-dpad-focused') || {}).getAttribute?.('data-save-slot');
  out.previewTarget = Array.from(vp.querySelectorAll('.preset-card')).map((c) => c.classList.contains('is-save-target'));
  const before = stored();
  press('Enter'); await wait(250);
  out.dialogOpen = !document.getElementById('modal-layer').hidden;
  out.savedWhileDialogOpen = stored().repeat === before.repeat;
  press('Escape'); await wait(250);
  out.closedByEscape = document.getElementById('modal-layer').hidden;
  out.focusReturnedToSlot = (vp.querySelector('.is-dpad-focused') || {}).getAttribute?.('data-save-slot');
  out.screenStill = document.querySelector('.tv-screen.is-active').id;
  out.unchangedAfterCancel = stored().repeat === before.repeat;
  press('Enter'); await wait(250);
  out.reopened = !document.getElementById('modal-layer').hidden;
  press('ArrowRight'); await wait(60); // OK -> Cancelar
  out.cancelFocused = (document.querySelector('.is-dpad-focused') || {}).id;
  press('ArrowLeft'); await wait(60);
  press('Enter'); await wait(350); // OK
  out.closedAfterOk = document.getElementById('modal-layer').hidden;
  const after = stored();
  out.saved = { repeat: after.repeat, work: after.workSeconds, rest: after.restSeconds };
  out.toast = (document.querySelector('.toast-pill') || {}).textContent;
  out.flash = document.querySelectorAll('.preset-card')[1].classList.contains('is-saved-flash');
  out.screenAfter = document.querySelector('.tv-screen.is-active').id;
  return JSON.stringify(out, null, 1);
})()
