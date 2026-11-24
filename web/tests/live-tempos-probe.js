(async () => {
  const press = async (key, duration = 50) => {
    const send = type => window.dispatchEvent(new KeyboardEvent(type, { key, bubbles: true, cancelable: true }));
    send('keydown');
    await new Promise(resolve => setTimeout(resolve, duration));
    send('keyup');
    await new Promise(resolve => setTimeout(resolve, 70));
  };
  const active = selector => document.querySelector(selector)?.classList.contains('is-active');
  const check = (condition, label) => { if (!condition) throw new Error(label); };
  check(active('#screen-clock'), 'Begin on the clock');
  await press('ArrowUp');
  check(active('#screen-quick'), 'Open timers');
  const face = document.querySelector('[data-time-start]');
  const duration = () => face.querySelector('b').textContent;
  const before = duration();
  await press('ArrowDown');
  check(document.querySelector('[data-time-add="30"]').classList.contains('is-dpad-focused'), 'Focus +30 s');
  await press('Enter');
  check(face.classList.contains('is-dpad-focused'), 'Return to start after adding time');
  check(before === '60:00' || duration() !== before, 'Displayed duration updates');
  await press('ArrowDown', 650);
  check(document.querySelector('[data-quick-opt="rep+"]').classList.contains('is-dpad-focused'), 'Hold down for rounds');
  await press('ArrowUp');
  await press('Enter');
  check(active('#screen-active'), 'Start timer');
  await press('Escape');
  check(active('#screen-quick'), 'Back returns to timers');
  return 'OK';
})()
