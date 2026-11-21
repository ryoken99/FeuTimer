const test = require('node:test');
const assert = require('node:assert/strict');

require('../js/i18n.js');
require('../js/presets.js');
require('../js/engine.js');
require('../js/dpad.js');
require('../js/qr.js');

const { I18n, TimeFormatter, Presets, TimerEngine, CustomBuilder, DpadGrid, Scoreboard, Qr } = globalThis.FEU_TIMER;

test('Restart restores the rest duration, including while paused', () => {
  const prefs = { prepSeconds: 0 };
  const work = TimerEngine.start({ repeat: 2, workSeconds: 30, restSeconds: 10 }, prefs).snapshot;
  const rest = TimerEngine.tick(TimerEngine.skipPhase(work, prefs).snapshot, prefs).snapshot;
  for (const current of [rest, TimerEngine.pause(rest)]) {
    assert.deepEqual(TimerEngine.restartPhase(current), { ...current, remainingSeconds: 10 });
  }
});

test('TimeFormatter formats MM:SS and compact BJJ durations identically to Kotlin engine', () => {
  assert.equal(TimeFormatter.formatMmSs(30), '00:30');
  assert.equal(TimeFormatter.formatMmSs(300), '05:00');
  assert.equal(TimeFormatter.parseMmSs('12:00'), 720);
  assert.equal(TimeFormatter.formatCompactDuration(1740), '29m');
  assert.equal(TimeFormatter.formatCompactDuration(2310), '38m 30s');
});

test('Presets exposes all 15 quick timers and 4 core BJJ sparring presets', () => {
  assert.equal(Presets.quickTimers.length, 15);
  assert.equal(Presets.roundPresets.length, 4);
});

test('CustomBuilder enforces step increments, endless toggle, and bounds', () => {
  assert.equal(CustomBuilder.adjustWork(CustomBuilder.create({ workSeconds: 5 }), false, 60).workSeconds, 5);
  let b = CustomBuilder.create();
  assert.equal(b.repeat, 2);
  b = CustomBuilder.adjustRepeat(b, true);
  assert.equal(b.repeat, 3);
  b = CustomBuilder.toggleEndless(b);
  assert.equal(b.repeat, -1);
  b = CustomBuilder.toggleEndless(b);
  assert.equal(b.repeat, 3);
});

test('TimerEngine runs vocal 3-2-1-HORN lead-in, halfway/50s warnings, 10..1 vocal countdown, and silent rest breaks', () => {
  const prefs = { prepSeconds: 3, warnHalfway: true, beep50s: true, beep10s: true };
  let step = TimerEngine.start({ repeat: 2, workSeconds: 60, restSeconds: 6 }, prefs, true);

  assert.equal(step.snapshot.phase, 'LEAD_IN');
  assert.equal(step.cue, 'COUNT_3');
  step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.cue, 'COUNT_2');
  step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.cue, 'COUNT_1');
  step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.cue, 'FIGHT_HORN');
  step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.snapshot.phase, 'RUNNING_WORK');
  assert.equal(step.snapshot.remainingSeconds, 59);

  // 59 -> 50: WARN_50S
  for (let i = 0; i < 9; i++) step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.snapshot.remainingSeconds, 50);
  assert.equal(step.cue, 'WARN_50S');

  // 50 -> 30: WARN_HALFWAY
  for (let i = 0; i < 20; i++) step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.snapshot.remainingSeconds, 30);
  assert.equal(step.cue, 'WARN_HALFWAY');

  // 30 -> 11: silent
  for (let i = 0; i < 19; i++) step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.snapshot.remainingSeconds, 11);
  assert.equal(step.cue, null);

  // 11 -> 1: COUNT_10 .. COUNT_1
  for (let exp = 10; exp >= 1; exp--) {
    step = TimerEngine.tick(step.snapshot, prefs);
    assert.equal(step.cue, `COUNT_${exp}`);
    assert.equal(step.snapshot.isBlinking, true);
  }

  // 1 -> 0: enters RUNNING_REST with ROUND_END_TO_REST, and all intermediate rest ticks are silent
  step = TimerEngine.tick(step.snapshot, prefs);
  assert.equal(step.snapshot.phase, 'RUNNING_REST');
  assert.equal(step.cue, 'ROUND_END_TO_REST');
  for (let i = 0; i < 5; i++) {
    step = TimerEngine.tick(step.snapshot, prefs);
    assert.equal(step.cue, null);
  }
});

test('Scoreboard supports live athlete names, direct point edits, side swap, and QR matrix generation', () => {
  let sb = Scoreboard.create();
  sb = Scoreboard.setName(sb, 'A', 'Carlos Prates');
  sb = Scoreboard.setName(sb, 'B', 'Pedro Dias');
  sb = Scoreboard.adjust(sb, 'A', 'points', 4);
  sb = Scoreboard.setExact(sb, 'B', 'points', 12);
  assert.equal(Scoreboard.computeWinner(sb), 'FIGHTER_B');

  sb = Scoreboard.swapSides(sb);
  assert.equal(sb.fighterAName, 'Pedro Dias');
  assert.equal(sb.a.points, 12);

  const qr = Qr.encodeMatrix('http://192.168.1.45:7777');
  assert.equal(qr.length, 25);
  assert.equal(qr[0][0], true);
});

test('DpadGrid wraps focus left/right across the 3x5 quick-timer matrix and steps into bottom bar', () => {
  assert.equal(DpadGrid.moveQuickGrid(0, 'left'), 14);
  assert.equal(DpadGrid.moveQuickGrid(4, 'right'), 5);
  assert.equal(DpadGrid.moveQuickGrid(12, 'down'), 'BAR');
});

test('I18n provides complete hand-written pt-PT, en, and es dictionaries', () => {
  assert.equal(I18n.t('pt-PT', 'phase_prep', { count: 3 }), 'PREPARA-TE · 3');
  assert.equal(I18n.t('en', 'phase_fight_go'), 'FIGHT!');
  assert.equal(I18n.t('es', 'nav_rounds'), 'Rondas');
});

test("Spatial D-pad never traps focus: every control reachable and a lone Back button escapes on all 4 arrows", () => {
  // Layout mimicking Settings: 4 rows of pills + lone Back button at bottom-left
  const mk = (l, t, w = 80, h = 40) => ({ left: l, top: t, right: l + w, bottom: t + h });
  const rects = [
    mk(560, 190), mk(680, 190), mk(760, 190), mk(840, 190),
    mk(520, 250), mk(620, 250), mk(700, 250), mk(780, 250), mk(860, 250),
    mk(330, 310), mk(430, 310), mk(530, 310), mk(600, 310), mk(660, 310), mk(740, 310),
    mk(440, 370), mk(520, 370), mk(580, 370), mk(640, 370), mk(700, 370),
    mk(40, 470, 46, 46),
  ];
  const backIdx = rects.length - 1;
  for (const dir of ["up", "down", "left", "right"]) {
    const next = DpadGrid.findNext(rects, backIdx, dir);
    assert.notEqual(next, backIdx, "Back button must be escapable via " + dir);
  }
  // Graph is strongly connected: BFS from Back reaches every control
  const seen = new Set([backIdx]);
  const queue = [backIdx];
  while (queue.length) {
    const cur = queue.shift();
    for (const dir of ["up", "down", "left", "right"]) {
      const n = DpadGrid.findNext(rects, cur, dir);
      if (!seen.has(n)) { seen.add(n); queue.push(n); }
    }
  }
  assert.equal(seen.size, rects.length);
  // And every control can get back to the Back button
  for (let start = 0; start < rects.length; start++) {
    const vis = new Set([start]);
    const q = [start];
    while (q.length) {
      const cur = q.shift();
      for (const dir of ["up", "down", "left", "right"]) {
        const n = DpadGrid.findNext(rects, cur, dir);
        if (!vis.has(n)) { vis.add(n); q.push(n); }
      }
    }
    assert.equal(vis.size, rects.length, "start " + start + " must reach all controls");
  }
});

test("Rondas card: Down from +1m/-1m goes to the stepper (-), Right from - or -1m goes to the Play button", () => {
  const mk = (l, t, r, b) => ({ left: l, top: t, right: r, bottom: b });
  // Geometry taken from the Descanso card + action column screenshot
  const plus1m = mk(280, 42, 370, 96);
  const minus1m = mk(380, 42, 472, 96);
  const stepPlus = mk(54, 238, 248, 300);
  const stepMinus = mk(278, 238, 472, 300);
  const play = mk(698, 136, 816, 254);
  const save1 = mk(706, 286, 744, 322);
  const save2 = mk(766, 286, 804, 322);
  const rects = [plus1m, minus1m, stepPlus, stepMinus, play, save1, save2];
  const IDX = { plus1m: 0, minus1m: 1, stepPlus: 2, stepMinus: 3, play: 4 };
  assert.equal(DpadGrid.findNext(rects, IDX.minus1m, "down"), IDX.stepMinus);
  assert.equal(DpadGrid.findNext(rects, IDX.plus1m, "down"), IDX.stepMinus);
  assert.equal(DpadGrid.findNext(rects, IDX.stepMinus, "right"), IDX.play);
  assert.equal(DpadGrid.findNext(rects, IDX.minus1m, "right"), IDX.play);
  assert.ok([IDX.plus1m, IDX.minus1m].includes(DpadGrid.findNext(rects, IDX.stepMinus, "up")));
  assert.equal(DpadGrid.findNext(rects, IDX.stepPlus, "right"), IDX.stepMinus);
});

test("Tempos screen: Right from the stopwatch goes to Repetir, Right from Descanso + goes to the time grid, never to Back", () => {
  const mk = (l, t, r, b) => ({ left: l, top: t, right: r, bottom: b });
  const stopwatch = mk(630, 140, 984, 188);            // 0
  const repMinus = mk(178, 200, 222, 238);              // 1
  const repPlus = mk(258, 200, 302, 238);               // 2
  const restMinus = mk(366, 200, 410, 238);             // 3
  const restPlus = mk(446, 200, 490, 238);              // 4
  const grid = [0, 1, 2, 3, 4].map((c) => mk(42 + c * 190, 250, 42 + c * 190 + 178, 316)); // 5..9
  const back = mk(42, 520, 88, 566);                    // 10
  const rects = [stopwatch, repMinus, repPlus, restMinus, restPlus, ...grid, back];
  const groups = [0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3];
  assert.equal(DpadGrid.findNext(rects, 0, "right", groups), 1);   // stopwatch -> Repetir -
  assert.equal(DpadGrid.findNext(rects, 4, "right", groups), 5);   // Descanso + -> first time
  assert.equal(DpadGrid.findNext(rects, 5, "left", groups), 4);    // first time <- Descanso +
  assert.notEqual(DpadGrid.findNext(rects, 0, "right", groups), 10);
  assert.notEqual(DpadGrid.findNext(rects, 4, "right", groups), 10);
});
