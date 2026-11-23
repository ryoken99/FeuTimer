/*
 * FEU BJJ Android TV Browser Simulator — Full 6-Screen Controller
 * Matches the upgraded Kotlin Compose Android TV app 1:1:
 * - True 960×540 ResizeObserver scale lock (0px cut-off at 100% zoom or any window size)
 * - PT-PT Vocal Coach announcements + peaceful silent breaks
 * - Real-time WebSocket Wi-Fi Mobile Referee Console sync + scannable QR code on Scoreboard
 */
(function () {
  'use strict';

  const { I18n, TimeFormatter, Presets, TimerEngine, CustomBuilder, DpadGrid, Scoreboard, Qr, Audio } = window.FEU_TIMER;

  const STORAGE_KEY = 'feu.tv.simulator.v3';

  const roundSections = ['presets', 'builder'];
  let roundSection = 'presets';
  let workoutPage = 0;
  const workoutsPerPage = 8;

  function loadPersisted() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function savePersisted(data) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (_) {}
  }

  const saved = loadPersisted();

  const state = {
    locale: saved?.locale || 'pt-PT',
    screen: 'CLOCK', // CLOCK | QUICK_TIMERS | ROUNDS | SCOREBOARD | SETTINGS | ACTIVE_TIMER
    soundPreferences: {
      soundEnabled: true,
      voiceEnabled: true,
      warnHalfway: true,
      warn2m: false,
      warn1m: false,
      beep50s: true,
      warn30s: false,
      beep10s: true,
      prepSeconds: 3,
      soundPack: 'CLASSIC_HORN',
      use24HourClock: true,
      ...(saved?.soundPreferences || {}),
    },
    presetSlots: saved?.presetSlots || Presets.roundPresets.map((p) => ({ ...p })),
    customBuilder: CustomBuilder.create(saved?.customRound),
    lastSessionConfig: saved?.lastSessionConfig || { repeat: 5, workSeconds: 300, restSeconds: 60 },
    timer: TimerEngine.idle(),
    scoreboard: Scoreboard.create(saved?.scoreboard),
    matchResults: saved?.matchResults || [],
    quickRepeat: Math.min(99, Math.max(1, saved?.quickRepeat || 1)),
    quickRestSeconds: Math.min(3600, Math.max(0, saved?.quickRestSeconds ?? 60)),
    scoreboardDurationSeconds: 300,
    scoreboardArmed: true,
    lanUrl: `${window.location.hostname || '127.0.0.1'}:7777`,
    lanConnectedClients: 0,
    speedMultiplier: 1,
    focusZone: 'BAR',
    contentFocusIndex: 0,
    barFocusIndex: 0,
  };

  // Skip DOM replacement when markup is unchanged so focused controls never re-animate (no flicker)
  function setHtml(el, html) {
    if (el.__lastHtml === html) return false;
    el.__lastHtml = html;
    el.innerHTML = html;
    return true;
  }

  function persistSettings() {
    savePersisted({
      locale: state.locale,
      soundPreferences: state.soundPreferences,
      presetSlots: state.presetSlots,
      customRound: state.customBuilder,
      lastSessionConfig: state.lastSessionConfig,
      scoreboard: state.scoreboard,
      matchResults: state.matchResults,
      quickRepeat: state.quickRepeat,
      quickRestSeconds: state.quickRestSeconds,
    });
  }

  // Real-time WebSocket bridge to web/serve.cjs so mobile referee changes happen with zero delay
  let tvWs = null;
  // Only the TV page the user touched last speaks; other open pages stay in sync silently (hub-assigned)
  let isAudioLeader = true;

  function buildRefereeStateSnapshot() {
    const isRunning = state.screen === 'SCOREBOARD' && (state.timer.phase === 'RUNNING_WORK' || state.timer.phase === 'LEAD_IN');
    const leadInLabel = state.screen === 'SCOREBOARD' && state.timer.phase === 'LEAD_IN'
      ? (state.timer.leadInCount > 0 ? String(state.timer.leadInCount) : 'GO') : '';
    const remSec = state.screen === 'SCOREBOARD' ? state.timer.remainingSeconds : state.scoreboardDurationSeconds;
    return {
      screen: state.screen,
      isRunning,
      leadInLabel,
      remainingSeconds: remSec,
      formattedClock: TimeFormatter.formatMmSs(remSec),
      durationSeconds: state.scoreboardDurationSeconds,
      fighterAName: state.scoreboard.fighterAName,
      fighterBName: state.scoreboard.fighterBName,
      category: state.scoreboard.category,
      aPoints: state.scoreboard.a.points,
      aAdvantages: state.scoreboard.a.advantages,
      aPenalties: state.scoreboard.a.penalties,
      bPoints: state.scoreboard.b.points,
      bAdvantages: state.scoreboard.b.advantages,
      bPenalties: state.scoreboard.b.penalties,
      computedWinner: Scoreboard.computeWinner(state.scoreboard),
      defaultMethod: Scoreboard.defaultMethod(state.scoreboard),
      results: state.matchResults,
    };
  }

  function pushTvStateToHub() {
    if (tvWs && tvWs.readyState === WebSocket.OPEN) {
      try {
        tvWs.send(JSON.stringify({ type: 'tv_state', state: buildRefereeStateSnapshot() }));
      } catch (_) {}
    }
  }

  function applyRemoteRefereeCommand(cmdObj) {
    if (!cmdObj || typeof cmdObj !== 'object') return;
    const cmd = cmdObj.cmd || '';
    const side = cmdObj.side === 'B' ? 'B' : 'A';

    const ensureSb = () => {
      if (state.screen !== 'SCOREBOARD') openScoreboard();
    };

    if (cmd === 'open_scoreboard') {
      if (state.screen === 'CLOCK') openScoreboard();
    }
    else if (cmd === 'set_name') {
      ensureSb();
      state.scoreboard = Scoreboard.setName(state.scoreboard, side, cmdObj.name);
    } else if (cmd === 'set_category') {
      ensureSb();
      state.scoreboard = Scoreboard.setCategory(state.scoreboard, cmdObj.category);
    } else if (cmd === 'score_delta') {
      ensureSb();
      state.scoreboard = Scoreboard.adjust(state.scoreboard, side, cmdObj.field || 'points', Number(cmdObj.delta) || 0);
    } else if (cmd === 'score_set') {
      ensureSb();
      state.scoreboard = Scoreboard.setExact(state.scoreboard, side, cmdObj.field || 'points', Number(cmdObj.value) || 0);
    } else if (cmd === 'swap_sides') {
      ensureSb();
      state.scoreboard = Scoreboard.swapSides(state.scoreboard);
    } else if (cmd === 'play_pause') {
      ensureSb();
      togglePlayPause();
    } else if (cmd === 'time_delta') {
      ensureSb();
      state.timer = TimerEngine.adjustTime(state.timer, Number(cmdObj.delta) || 0);
    } else if (cmd === 'time_set') {
      ensureSb();
      state.timer = TimerEngine.setExactTime(state.timer, Number(cmdObj.seconds) || 300);
    } else if (cmd === 'set_duration') {
      selectScoreboardDuration(Number(cmdObj.seconds) || 300);
    } else if (cmd === 'reset_match') {
      ensureSb();
      state.scoreboard = Scoreboard.reset(Boolean(cmdObj.keepNames), state.scoreboard);
      selectScoreboardDuration(state.scoreboardDurationSeconds);
    } else if (cmd === 'buzzer') {
      if (isAudioLeader && state.soundPreferences.soundEnabled) Audio.play('ROUND_END_ALARM', state.soundPreferences.soundPack, false);
    } else if (cmd === 'save_result') {
      const now = new Date();
      const ts = `${String(now.getDate()).padStart(2, '0')}/${String(now.getMonth() + 1).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      const record = {
        id: Math.random().toString(36).slice(2, 10),
        timestamp: ts,
        category: state.scoreboard.category,
        fighterAName: state.scoreboard.fighterAName || 'Atleta 1 (Branco)',
        fighterAPoints: state.scoreboard.a.points,
        fighterAAdvantages: state.scoreboard.a.advantages,
        fighterAPenalties: state.scoreboard.a.penalties,
        fighterBName: state.scoreboard.fighterBName || 'Atleta 2 (Vermelho)',
        fighterBPoints: state.scoreboard.b.points,
        fighterBAdvantages: state.scoreboard.b.advantages,
        fighterBPenalties: state.scoreboard.b.penalties,
        winnerSide: cmdObj.winner || Scoreboard.computeWinner(state.scoreboard),
        method: cmdObj.method || Scoreboard.defaultMethod(state.scoreboard),
        durationSeconds: state.scoreboardDurationSeconds,
        remainingSeconds: state.timer.remainingSeconds,
      };
      state.matchResults = [record, ...state.matchResults];
    } else if (cmd === 'delete_result') {
      state.matchResults = state.matchResults.filter((r) => r.id !== cmdObj.id);
    } else if (cmd === 'clear_results') {
      state.matchResults = [];
    }

    persistSettings();
    renderAll();
    pushTvStateToHub();
  }

  // Tell the hub this page is the one being used (visible + interacted with) so it becomes the audio/state leader
  function announceActiveTv() {
    if (document.visibilityState !== 'visible') return;
    isAudioLeader = true; // optimistic: hands-on page speaks at once, the hub confirms and silences the others
    if (tvWs && tvWs.readyState === WebSocket.OPEN) {
      try { tvWs.send(JSON.stringify({ type: 'tv_active' })); } catch (_) {}
    }
  }
  document.addEventListener('visibilitychange', announceActiveTv);
  window.addEventListener('focus', announceActiveTv);
  window.addEventListener('pointerdown', announceActiveTv, true);
  window.addEventListener('keydown', announceActiveTv, true);

  function connectTvHub() {
    if (window.location.protocol === 'file:') return;
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    try {
      tvWs = new WebSocket(`${proto}//${window.location.host}/ws?role=tv`);
    } catch (_) {
      return;
    }
    tvWs.onopen = () => {
      announceActiveTv();
      pushTvStateToHub();
      fetch('/api/lan-info')
        .then((r) => r.json())
        .then((info) => {
          if (info?.lanUrl) state.lanUrl = info.lanUrl;
          state.lanConnectedClients = info?.connectedClients || 0;
          renderBottomBar();
        })
        .catch(() => {});
    };
    tvWs.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.type === 'lan_meta') {
          if (msg.lanUrl) state.lanUrl = msg.lanUrl;
          state.lanConnectedClients = msg.connectedClients || 0;
          renderBottomBar();
        } else if (msg.type === 'audio_leader') {
          isAudioLeader = Boolean(msg.leader);
          if (!isAudioLeader) Audio.stopAll();
        } else if (msg.type === 'referee_cmd') {
          applyRemoteRefereeCommand(msg.payload);
        }
      } catch (_) {}
    };
    tvWs.onclose = () => {
      setTimeout(connectTvHub, 1200);
    };
  }

  let rafId = null;
  let frameTimerId = null;

  // Browsers pause requestAnimationFrame in background tabs, which froze a phone-started count-in after "3".
  // Race rAF against a short timeout (timers keep running, audio tabs are not throttled) so ticks never stall.
  function scheduleFrame(fn) {
    cancelScheduledFrame();
    const run = (ts) => {
      cancelScheduledFrame();
      fn(typeof ts === "number" ? ts : performance.now());
    };
    rafId = requestAnimationFrame(run);
    frameTimerId = setTimeout(() => run(performance.now()), 200);
  }

  function cancelScheduledFrame() {
    if (rafId !== null) cancelAnimationFrame(rafId);
    if (frameTimerId !== null) clearTimeout(frameTimerId);
    rafId = null;
    frameTimerId = null;
  }
  let lastTickAnchorMs = 0;
  let finishReturnDeadlineMs = null;

  function playCue(cue) {
    if (!cue || !isAudioLeader || !state.soundPreferences.soundEnabled) return;
    if (cue === 'ROUND_END_ALARM' && state.screen === 'SCOREBOARD') cue = 'MATCH_END';
    Audio.play(cue, state.soundPreferences.soundPack, state.soundPreferences.voiceEnabled);
  }

  function startTicker(expectedScreen) {
    stopTicker();
    lastTickAnchorMs = performance.now();
    finishReturnDeadlineMs = null;

    const stepFrame = (nowMs) => {
      if (state.screen !== expectedScreen) {
        rafId = null;
        return;
      }

      if (state.timer.phase === 'FINISHED') {
        if (expectedScreen === 'ACTIVE_TIMER') {
          if (finishReturnDeadlineMs === null) {
            finishReturnDeadlineMs = nowMs + 3000 / state.speedMultiplier;
          } else if (nowMs >= finishReturnDeadlineMs) {
            returnToClock();
            return;
          }
          scheduleFrame(stepFrame);
        }
        return;
      }

      if (state.timer.phase === 'PAUSED') {
        lastTickAnchorMs = nowMs;
        scheduleFrame(stepFrame);
        return;
      }

      const tickIntervalMs = 1000 / state.speedMultiplier;
      while (nowMs - lastTickAnchorMs >= tickIntervalMs && state.timer.phase !== 'FINISHED') {
        lastTickAnchorMs += tickIntervalMs;
        const step = TimerEngine.tick(state.timer, state.soundPreferences);
        state.timer = step.snapshot;
        playCue(step.cue);

        if (expectedScreen === 'ACTIVE_TIMER') {
          renderActiveTimer();
          renderBottomBar();
          updateAmbientTheme();
        } else if (expectedScreen === 'SCOREBOARD') {
          renderScoreboardScreen();
          pushTvStateToHub();
        }
      }

      scheduleFrame(stepFrame);
    };
    scheduleFrame(stepFrame);
  }

  function stopTicker() {
    cancelScheduledFrame();
  }

  function openQuickTimers() {
    stopTicker();
    Audio.stopAll();
    state.screen = 'QUICK_TIMERS';
    state.focusZone = 'CONTENT';
    state.contentFocusIndex = 0;
    renderAll();
  }

  function openRounds() {
    stopTicker();
    Audio.stopAll();
    state.screen = 'ROUNDS';
    roundSection = 'presets';
    state.focusZone = 'CONTENT';
    state.contentFocusIndex = 0;
    renderAll();
  }

  function openScoreboard() {
    if (state.screen === 'SCOREBOARD') return;
    stopTicker();
    Audio.stopAll();
    state.screen = 'SCOREBOARD';
    const started = TimerEngine.start(
      { repeat: 0, workSeconds: state.scoreboardDurationSeconds, restSeconds: 0 },
      state.soundPreferences,
      false
    ).snapshot;
    state.timer = TimerEngine.pause(started);
    state.scoreboardArmed = true;
    state.focusZone = 'BAR';
    state.barFocusIndex = 0;
    renderAll();
    pushTvStateToHub();
  }

  function selectScoreboardDuration(seconds) {
    stopTicker();
    Audio.stopAll();
    state.screen = 'SCOREBOARD';
    state.scoreboardDurationSeconds = seconds;
    const started = TimerEngine.start(
      { repeat: 0, workSeconds: seconds, restSeconds: 0 },
      state.soundPreferences,
      false
    ).snapshot;
    state.timer = TimerEngine.pause(started);
    state.scoreboardArmed = true;
    renderScoreboardScreen();
    renderBottomBar();
    pushTvStateToHub();
  }

  function openSettings() {
    stopTicker();
    Audio.stopAll();
    state.screen = 'SETTINGS';
    state.focusZone = 'BAR';
    state.barFocusIndex = 0;
    renderAll();
  }

  function returnToClock() {
    stopTicker();
    Audio.stopAll();
    state.screen = 'CLOCK';
    state.timer = TimerEngine.idle();
    state.focusZone = 'BAR';
    state.barFocusIndex = 0;
    renderAll();
    pushTvStateToHub();
  }

  // Quick time -> config honouring the chosen repeat count and rest (1 = plain single countdown)
  function quickConfigFor(seconds) {
    if (state.quickRepeat <= 1) return { repeat: 0, workSeconds: seconds, restSeconds: 0 };
    return { repeat: state.quickRepeat, workSeconds: seconds, restSeconds: state.quickRestSeconds };
  }

  function adjustQuickRest(increase) {
    const r = state.quickRestSeconds;
    const step = (increase && r < 30) || (!increase && r <= 30) ? 5 : 10;
    state.quickRestSeconds = Math.min(3600, Math.max(0, r + (increase ? step : -step)));
  }

  function startSession(config) {
    Audio.unlock();
    stopTicker();
    Audio.stopAll();

    state.lastSessionConfig = { ...config };
    persistSettings();

    const initial = TimerEngine.start(config, state.soundPreferences, true);
    state.screen = 'ACTIVE_TIMER';
    state.timer = initial.snapshot;
    state.focusZone = 'BAR';
    state.barFocusIndex = 1;

    renderAll();
    playCue(initial.cue);
    startTicker('ACTIVE_TIMER');
  }

  function togglePlayPause() {
    if (state.screen !== 'ACTIVE_TIMER' && state.screen !== 'SCOREBOARD') return;
    Audio.unlock();

    if (state.screen === 'SCOREBOARD' && state.scoreboardArmed && state.timer.phase === 'PAUSED') {
      state.scoreboardArmed = false;
      const secs = Math.max(1, state.timer.remainingSeconds);
      const initial = TimerEngine.start({ repeat: 0, workSeconds: secs, restSeconds: 0 }, state.soundPreferences, true);
      state.timer = initial.snapshot;
      playCue(initial.cue);
      startTicker('SCOREBOARD');
      renderScoreboardScreen();
      pushTvStateToHub();
      return;
    }

    if (state.timer.phase === 'PAUSED') {
      state.timer = TimerEngine.resume(state.timer);
      lastTickAnchorMs = performance.now();
      startTicker(state.screen);
    } else if (['LEAD_IN', 'RUNNING_WORK', 'RUNNING_REST', 'RUNNING_STOPWATCH'].includes(state.timer.phase)) {
      Audio.stopAll();
      state.timer = TimerEngine.pause(state.timer);
    } else if (state.timer.phase === 'FINISHED' && state.screen === 'SCOREBOARD') {
      selectScoreboardDuration(state.scoreboardDurationSeconds);
      return;
    }

    if (state.screen === 'ACTIVE_TIMER') {
      renderActiveTimer();
      renderBottomBar();
      updateAmbientTheme();
    } else {
      renderScoreboardScreen();
      pushTvStateToHub();
    }
  }

  // ---- Save-to-slot preview (grow + pulse the target card, show before -> after) ----
  function metricsOf(cfg) {
    const L = state.locale;
    const w = TimeFormatter.displayValueAndUnit(cfg.workSeconds);
    const r = TimeFormatter.displayValueAndUnit(cfg.restSeconds);
    const endless = cfg.repeat === -1;
    const total = endless ? '∞' : TimeFormatter.formatCompactDuration(cfg.repeat * cfg.workSeconds + Math.max(0, cfg.repeat - 1) * cfg.restSeconds);
    return {
      rep: { val: endless ? '∞' : String(cfg.repeat), unit: '' },
      work: { val: String(w.value), unit: I18n.t(L, w.unitKey) },
      rest: { val: String(r.value), unit: I18n.t(L, r.unitKey) },
      total: { val: total, unit: '' },
    };
  }

  let hoverSaveSlot = null;

  function currentSavePreviewSlot() {
    if (state.screen !== 'ROUNDS' || isModalOpen()) return null;
    if (document.body.classList.contains('dpad-mode')) {
      const f = els.viewport.querySelector('.is-dpad-focused[data-save-slot]');
      return f ? Number(f.getAttribute('data-save-slot')) : null;
    }
    return hoverSaveSlot;
  }

  function updateSavePreview() {
    if (!els.presetsRow) return;
    const L = state.locale;
    const slot = currentSavePreviewSlot();
    const next = metricsOf(state.customBuilder);
    const popup = document.getElementById('slot-preview');
    popup.hidden = slot == null || roundSection !== 'create';
    if (!popup.hidden) {
      const preset = state.presetSlots[slot];
      const title = popup.querySelector('h3');
      const label = (slot + 1) + ' · ' + (preset.customName || I18n.t(L, preset.titleKey));
      if (title.textContent !== label) title.textContent = label;
      setHtml(popup.querySelector('.modal-body'), diffRows(L, metricsOf(preset), next));
    }
    els.presetsRow.querySelectorAll('.preset-card').forEach((card) => {
      const idx = Number(card.dataset.presetIdx);
      const target = idx === slot;
      card.classList.toggle('is-save-target', target);
      const cur = metricsOf(state.presetSlots[idx]);
      for (const k of ['rep', 'work', 'rest']) {
        const valEl = card.querySelector(`[data-m="${k}"]`);
        const oldEl = card.querySelector(`[data-old="${k}"]`);
        const sufEl = valEl && valEl.parentElement.querySelector('.metric-suffix');
        const changed = target && (cur[k].val !== next[k].val || cur[k].unit !== next[k].unit);
        const shown = target ? next[k] : cur[k];
        if (valEl && valEl.textContent !== shown.val) valEl.textContent = shown.val;
        if (sufEl && sufEl.textContent !== shown.unit) sufEl.textContent = shown.unit;
        if (valEl) valEl.classList.toggle('is-changed', Boolean(changed));
        if (oldEl) {
          const txt = changed ? `${cur[k].val} ${cur[k].unit}`.trim() : '';
          if (oldEl.textContent !== txt) oldEl.textContent = txt;
        }
      }
      const totalEl = card.querySelector('[data-m="total"]');
      if (totalEl) {
        const changedTotal = target && cur.total.val !== next.total.val;
        const txt = changedTotal
          ? `${I18n.t(L, 'total_prefix', { total: cur.total.val })} → ${next.total.val}`
          : I18n.t(L, 'total_prefix', { total: (target ? next : cur).total.val });
        if (totalEl.textContent !== txt) totalEl.textContent = txt;
      }
    });
  }

  // ---- Templates strip: side-scrolling with fading edges ----
  // ---- Confirm dialog + completion feedback ----
  function isModalOpen() {
    const m = document.getElementById('modal-layer');
    return Boolean(m && !m.hidden);
  }

  function diffRows(L, cur, next) {
    const row = (label, k) => `<div class="diff-row"><span class="diff-label">${label}</span><span class="diff-old${cur[k].val !== next[k].val || cur[k].unit !== next[k].unit ? '' : ' is-same'}">${`${cur[k].val} ${cur[k].unit}`.trim()}</span><span class="diff-arrow">→</span><span class="diff-new${cur[k].val !== next[k].val || cur[k].unit !== next[k].unit ? ' is-changed' : ''}">${`${next[k].val} ${next[k].unit}`.trim()}</span></div>`;
    return row(I18n.t(L, 'role_rounds'), 'rep') + row(I18n.t(L, 'role_work'), 'work') +
      row(I18n.t(L, 'role_rest'), 'rest') + row('Total', 'total');
  }

  function openSaveDialog(slotIdx) {
    const L = state.locale;
    const existing = state.presetSlots[slotIdx];
    const cur = metricsOf(existing || state.customBuilder);
    const next = metricsOf(state.customBuilder);
    state.pendingSaveSlot = slotIdx;
    document.getElementById('modal-title').textContent = existing ? I18n.t(L, 'save_dialog_title', { slot: slotIdx + 1 }) : I18n.t(L, 'create_workout');
    document.getElementById('modal-body').innerHTML = diffRows(L, cur, next) +
      (existing ? `<div class="modal-hint">${I18n.t(L, 'save_dialog_hint')}</div>` : '');
    document.getElementById('slot-name-label').textContent = I18n.t(L, 'save_name');
    document.getElementById('slot-name').value = existing?.customName || '';
    document.getElementById('modal-ok').textContent = I18n.t(L, 'save_ok');
    document.getElementById('modal-cancel').textContent = I18n.t(L, 'save_cancel');
    document.getElementById('modal-layer').hidden = false;
    updateSavePreview();
    applyDomFocus(document.getElementById('modal-ok'));
  }

  function closeModal() {
    const m = document.getElementById('modal-layer');
    if (m) m.hidden = true;
    state.pendingSaveSlot = null;
    leaveSaveFlow();
  }

  function leaveSaveFlow() {
    hoverSaveSlot = null;
    document.getElementById('slot-name').blur();
    document.body.classList.add('dpad-mode');
    applyDomFocus(els.builderRow.querySelector('[data-builder-idx="10"]'));
  }

  function showToast(text, kind) {
    const layer = document.getElementById('toast-layer');
    const el = document.createElement('div');
    el.className = 'toast-pill' + (kind === 'warn' ? ' is-warn' : '');
    el.textContent = text;
    layer.appendChild(el);
    requestAnimationFrame(() => el.classList.add('is-shown'));
    setTimeout(() => el.classList.remove('is-shown'), 2000);
    setTimeout(() => el.remove(), 2400);
  }

  function confirmSaveToSlot() {
    const idx = state.pendingSaveSlot;
    if (idx == null) return closeModal();
    const isNew = idx === state.presetSlots.length;
    state.presetSlots[idx] = {
      titleKey: 'new_workout',
      ...state.presetSlots[idx],
      customName: document.getElementById('slot-name').value.trim().slice(0, 14),
      repeat: state.customBuilder.repeat,
      workSeconds: state.customBuilder.workSeconds,
      restSeconds: state.customBuilder.restSeconds,
    };
    persistSettings();
    closeModal();
    if (isNew) {
      roundSection = 'presets';
      workoutPage = Math.floor(idx / workoutsPerPage);
    }
    renderRoundsScreen();
    const card = els.presetsRow.querySelector(`[data-preset-idx="${idx}"]`);
    if (card) {
      if (isNew) applyDomFocus(card);
      card.classList.add('is-saved-flash');
      setTimeout(() => card.classList.remove('is-saved-flash'), 1400);
    }
    showToast(I18n.t(state.locale, 'save_done', { slot: idx + 1 }));
  }

  function onBackAction() {
    if (isModalOpen()) {
      closeModal();
      return;
    }
    if (!document.getElementById('slot-preview').hidden) {
      leaveSaveFlow();
      return;
    }
    if (state.screen === 'ROUNDS' && roundSection === 'create') {
      roundSection = 'presets';
      renderRoundsScreen();
      applyDomFocus(els.viewport.querySelector('[data-create-workout]'));
      return;
    }
    if (state.screen !== 'CLOCK') {
      returnToClock();
    }
  }

  // DOM Elements
  const els = {};

  function cacheElements() {
    els.bezel = document.querySelector('.tv-bezel');
    els.viewport = document.getElementById('tv-viewport');
    els.chromeBadge = document.getElementById('chrome-badge');
    els.chromeBadgeText = document.getElementById('chrome-badge-text');
    els.brandSub = document.getElementById('brand-sub');
    els.miniClock = document.getElementById('mini-clock');
    els.miniClockText = document.getElementById('mini-clock-text');

    els.screenClock = document.getElementById('screen-clock');
    els.screenQuick = document.getElementById('screen-quick');
    els.screenRounds = document.getElementById('screen-rounds');
    els.screenActive = document.getElementById('screen-active');
    els.screenScoreboard = document.getElementById('screen-scoreboard');
    els.screenSettings = document.getElementById('screen-settings');

    els.wallEyebrow = document.getElementById('wall-eyebrow');
    els.wallDate = document.getElementById('wall-date');
    els.wallDigits = document.getElementById('wall-digits');

    els.quickTitle = document.getElementById('quick-title');
    els.stopwatchBtn = document.getElementById('btn-stopwatch');
    els.quickGrid = document.getElementById('quick-grid');
    els.quickOptions = document.getElementById('quick-options');

    els.presetsRow = document.getElementById('presets-row');
    els.builderRow = document.getElementById('builder-row');

    els.phaseHeadline = document.getElementById('phase-headline');
    els.timerDigits = document.getElementById('timer-digits');

    els.scoreboardWrap = document.getElementById('scoreboard-wrap');
    els.settingsPanel = document.getElementById('settings-panel');

    els.bottomLeft = document.getElementById('bottom-left');
    els.bottomRight = document.getElementById('bottom-right');

    els.remoteTitle = document.getElementById('remote-title');
    els.speedBtn = document.getElementById('btn-speed');
    els.fullscreenBtn = document.getElementById('btn-fullscreen');
    els.testSoundBtn = document.getElementById('btn-test-sound');
  }

  // Exact 960×540 uniform scale lock — guarantees zero UI cut-off at any zoom or window size
  function setupViewportScaleLock() {
    const updateScale = () => {
      if (!els.bezel) return;
      const rect = els.bezel.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      const scale = Math.min(rect.width / 960, rect.height / 540);
      document.documentElement.style.setProperty('--tv-scale', scale.toFixed(5));
    };
    updateScale();
    if (typeof ResizeObserver !== 'undefined' && els.bezel) {
      new ResizeObserver(updateScale).observe(els.bezel);
    }
    window.addEventListener('resize', updateScale);
  }

  function formatWallClockString() {
    const now = new Date();
    if (state.soundPreferences.use24HourClock) {
      return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
    }
    const rawH = now.getHours();
    const ampm = rawH >= 12 ? 'PM' : 'AM';
    const h12 = rawH % 12 || 12;
    return `${h12}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')} ${ampm}`;
  }

  function updateWallClocks() {
    const timeStr = formatWallClockString();
    if (els.wallDigits) {
      els.wallDigits.textContent = timeStr;
      els.wallDigits.classList.toggle('is-12h', !state.soundPreferences.use24HourClock);
    }
    if (els.miniClockText) els.miniClockText.textContent = timeStr;

    if (els.wallDate) {
      const loc = state.locale === 'en' ? 'en-GB' : state.locale === 'es' ? 'es-ES' : 'pt-PT';
      els.wallDate.textContent = new Date()
        .toLocaleDateString(loc, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
        .toUpperCase();
    }
  }

  function updateAmbientTheme() {
    const rootStyle = document.documentElement.style;
    let aura = 'rgba(217, 14, 20, 0.12)';
    let accent = '#f8f5f0';
    let pillBorder = 'rgba(232, 20, 27, 0.85)';

    if (state.screen === 'ACTIVE_TIMER') {
      const p = state.timer.phase;
      if (p === 'LEAD_IN' && state.timer.leadInCount > 0) {
        aura = 'rgba(217, 178, 52, 0.14)';
        accent = '#d9b234';
        pillBorder = 'rgba(217, 178, 52, 0.85)';
      } else if (p === 'RUNNING_WORK' || (p === 'LEAD_IN' && state.timer.leadInCount === 0)) {
        aura = 'rgba(232, 20, 27, 0.15)';
        accent = p === 'LEAD_IN' ? '#e8141b' : '#f8f5f0';
        pillBorder = 'rgba(232, 20, 27, 0.85)';
      } else if (p === 'RUNNING_REST') {
        aura = 'rgba(56, 168, 121, 0.14)';
        accent = '#38a879';
        pillBorder = 'rgba(56, 168, 121, 0.85)';
      } else if (p === 'RUNNING_STOPWATCH' || p === 'PAUSED' || p === 'FINISHED') {
        aura = 'rgba(217, 178, 52, 0.14)';
        accent = '#d9b234';
        pillBorder = 'rgba(217, 178, 52, 0.85)';
      }
    }
    rootStyle.setProperty('--phase-aura', aura);
    rootStyle.setProperty('--phase-accent', accent);
    rootStyle.setProperty('--phase-pill-border', pillBorder);
  }

  function renderTopChrome() {
    const isHero = state.screen === 'CLOCK' || state.screen === 'ACTIVE_TIMER';
    if (els.viewport) els.viewport.classList.toggle('is-hero-chrome', isHero);

    els.brandSub.textContent = I18n.t(state.locale, 'brand_subtitle');
    const activeCfg = state.timer.config || state.lastSessionConfig;
    const badge = TimeFormatter.summaryBadge(activeCfg);
    if (badge && state.screen === 'ACTIVE_TIMER') {
      els.chromeBadgeText.textContent = badge;
      els.chromeBadge.classList.add('is-visible');
    } else {
      els.chromeBadge.classList.remove('is-visible');
    }
    els.miniClock.classList.toggle('is-visible', state.screen !== 'CLOCK');
  }

  function renderScreensVisibility() {
    els.screenClock.classList.toggle('is-active', state.screen === 'CLOCK');
    els.screenQuick.classList.toggle('is-active', state.screen === 'QUICK_TIMERS');
    els.screenRounds.classList.toggle('is-active', state.screen === 'ROUNDS');
    els.screenActive.classList.toggle('is-active', state.screen === 'ACTIVE_TIMER');
    els.screenScoreboard.classList.toggle('is-active', state.screen === 'SCOREBOARD');
    els.screenSettings.classList.toggle('is-active', state.screen === 'SETTINGS');
  }

  function renderClockScreen() {
    els.wallEyebrow.textContent = I18n.t(state.locale, 'brand_tatami_mode');
    updateWallClocks();
  }

  function renderQuickScreen() {
    const L = state.locale;
    els.quickTitle.textContent = I18n.t(L, 'quick_timers_title');
    els.stopwatchBtn.textContent = `⏱ ${I18n.t(L, 'quick_stopwatch_btn')}`;
    const restLabel = state.quickRestSeconds === 0 ? I18n.t(L, 'quick_rest_none') : TimeFormatter.formatMmSs(state.quickRestSeconds);
    // Structure is static; only the two value labels + dim state change, updated in place (no rebuild => no flicker)
    setHtml(
      els.quickOptions,
      `<div class="quick-opt" data-opt-group="rep">
        <span class="quick-opt__label">${I18n.t(L, 'quick_repeat_label')}</span>
        <button type="button" class="tv-focusable quick-opt__btn" data-quick-opt="rep-" aria-label="Repeat -">−</button>
        <span class="quick-opt__val" data-opt-val="rep"></span>
        <button type="button" class="tv-focusable quick-opt__btn" data-quick-opt="rep+" aria-label="Repeat +">+</button>
      </div>
      <div class="quick-opt" data-opt-group="rest">
        <span class="quick-opt__label">${I18n.t(L, 'quick_rest_label')}</span>
        <button type="button" class="tv-focusable quick-opt__btn" data-quick-opt="rest-" aria-label="Rest -">−</button>
        <span class="quick-opt__val" data-opt-val="rest"></span>
        <button type="button" class="tv-focusable quick-opt__btn" data-quick-opt="rest+" aria-label="Rest +">+</button>
      </div>`
    );
    const repVal = els.quickOptions.querySelector('[data-opt-val="rep"]');
    const restVal = els.quickOptions.querySelector('[data-opt-val="rest"]');
    if (repVal) repVal.textContent = `×${state.quickRepeat}`;
    if (restVal) restVal.textContent = restLabel;
    const restGroup = els.quickOptions.querySelector('[data-opt-group="rest"]');
    if (restGroup) restGroup.classList.toggle('is-dim', state.quickRepeat <= 1);
    setHtml(els.quickGrid, Presets.quickTimers
      .map((item, idx) => {
        const focused = false;
        return `
          <button type="button"
                  class="tv-focusable quick-btn${focused ? ' is-dpad-focused' : ''}"
                  data-quick-idx="${idx}">
            ${item.label}
          </button>
        `;
      })
      .join(''));
  }

  function renderRoundsScreen() {
    const L = state.locale;
    {
      const wrap = els.presetsRow.parentElement;
      wrap.dataset.section = roundSection;
      setHtml(document.getElementById('round-sections'), roundSections.map((section) =>
        `<button type="button" class="tv-focusable round-section" data-round-section="${section}" aria-pressed="${(section === roundSection || (section === 'presets' && roundSection === 'create'))}">${I18n.t(L, 'round_section_' + section)}</button>`
      ).join(''));
    }
    document.querySelector('[data-create-workout]').textContent = I18n.t(L, 'create_workout');
    const pages = Math.ceil(state.presetSlots.length / workoutsPerPage);
    workoutPage = Math.max(0, Math.min(workoutPage, pages - 1));
    setHtml(document.getElementById('workout-pages'), pages <= 1 ? '' :
      `<button class="tv-focusable opt-pill" data-page-step="-1" aria-label="${I18n.t(L,'prev_page')}">◀</button>
       <span>${workoutPage + 1} / ${pages}</span>
       <button class="tv-focusable opt-pill" data-page-step="1" aria-label="${I18n.t(L,'next_page')}">▶</button>`);
    const start = workoutPage * workoutsPerPage;
    setHtml(els.presetsRow, state.presetSlots.slice(start, start + workoutsPerPage)
      .map((p, position) => {
        const idx = start + position;
        const focused = false;
        const repStr = p.repeat === -1 ? '∞' : p.repeat;
        const w = TimeFormatter.displayValueAndUnit(p.workSeconds);
        const r = TimeFormatter.displayValueAndUnit(p.restSeconds);
        const total = p.repeat === -1 ? '∞' : TimeFormatter.formatCompactDuration(p.repeat * p.workSeconds + Math.max(0, p.repeat - 1) * p.restSeconds);

        return `
          <button type="button" class="tv-focusable preset-card${focused ? ' is-dpad-focused' : ''}" data-preset-idx="${idx}">
            <div class="preset-card__title">${idx + 1} · ${escapeHtml(p.customName || I18n.t(L, p.titleKey))}</div>
            <div class="preset-card__metrics">
              <div class="metric-cell">
                <span class="metric-old" data-old="rep"></span>
                <span class="metric-top"><span class="metric-val" data-m="rep">${repStr}</span></span>
                <span class="metric-role">${I18n.t(L, 'role_rounds')}</span>
              </div>
              <span class="preset-card__div"></span>
              <div class="metric-cell">
                <span class="metric-old" data-old="work"></span>
                <span class="metric-top"><span class="metric-val" data-m="work">${w.value}</span><em class="metric-suffix">${I18n.t(L, w.unitKey)}</em></span>
                <span class="metric-role metric-role--work">${I18n.t(L, 'phase_single_time')}</span>
              </div>
              <span class="preset-card__div"></span>
              <div class="metric-cell">
                <span class="metric-old" data-old="rest"></span>
                <span class="metric-top"><span class="metric-val" data-m="rest">${r.value}</span><em class="metric-suffix">${I18n.t(L, r.unitKey)}</em></span>
                <span class="metric-role metric-role--rest">${I18n.t(L, 'role_rest')}</span>
              </div>
            </div>
            <div class="preset-card__total" data-m="total">${I18n.t(L, 'total_prefix', { total })}</div>
          </button>
        `;
      })
      .join(''));
    updateSavePreview();

    const b = state.customBuilder;
    const isFoc = () => false;
    const isEndless = b.repeat === -1;
    const totalStr = isEndless ? '∞' : TimeFormatter.formatCompactDuration(CustomBuilder.totalSeconds(b));
    const unitForSec = (s) => I18n.t(L, s >= 3600 ? 'unit_hour' : s >= 60 ? 'unit_min' : 'unit_sec');

    els.builderRow.innerHTML = `
      <div class="builder-col">
        <div class="builder-col__top">
          <span class="builder-col__label">${I18n.t(L, 'custom_col_repeats')}</span>
          <button type="button" class="tv-focusable mini-tag${isEndless ? ' is-active' : ''}" data-builder-cmd="toggle-endless">∞</button>
        </div>
        <div class="builder-col__mid">
          <span class="builder-col__unit">${I18n.t(L, isEndless ? 'unit_endless' : 'unit_rep')}</span>
          <span class="builder-col__num">${isEndless ? '∞' : b.repeat}</span>
        </div>
        <div class="stepper-pair">
          <button type="button" class="tv-focusable step-btn${isFoc(4) ? ' is-dpad-focused' : ''}" data-builder-idx="4" aria-label="Increase rounds">+</button>
          <span class="stepper-div"></span>
          <button type="button" class="tv-focusable step-btn${isFoc(5) ? ' is-dpad-focused' : ''}" data-builder-idx="5" aria-label="Decrease rounds">−</button>
        </div>
      </div>

      <div class="builder-col">
        <div class="builder-col__top">
          <span class="builder-col__label">${I18n.t(L, 'custom_col_work')}</span>
          <span class="mini-tags">
            <button type="button" class="tv-focusable mini-tag" data-builder-cmd="work+60">+1m</button>
            <button type="button" class="tv-focusable mini-tag" data-builder-cmd="work-60">−1m</button>
          </span>
        </div>
        <div class="builder-col__mid">
          <span class="builder-col__unit">${unitForSec(b.workSeconds)}</span>
          <span class="builder-col__num">${TimeFormatter.formatMmSs(b.workSeconds)}</span>
        </div>
        <div class="stepper-pair">
          <button type="button" class="tv-focusable step-btn${isFoc(6) ? ' is-dpad-focused' : ''}" data-builder-idx="6" aria-label="Increase fight time">+</button>
          <span class="stepper-div"></span>
          <button type="button" class="tv-focusable step-btn${isFoc(7) ? ' is-dpad-focused' : ''}" data-builder-idx="7" aria-label="Decrease fight time">−</button>
        </div>
      </div>

      <div class="builder-col">
        <div class="builder-col__top">
          <span class="builder-col__label">${I18n.t(L, 'custom_col_rest')}</span>
          <span class="mini-tags">
            <button type="button" class="tv-focusable mini-tag" data-builder-cmd="rest+60">+1m</button>
            <button type="button" class="tv-focusable mini-tag" data-builder-cmd="rest-60">−1m</button>
          </span>
        </div>
        <div class="builder-col__mid">
          <span class="builder-col__unit">${unitForSec(b.restSeconds)}</span>
          <span class="builder-col__num">${TimeFormatter.formatMmSs(b.restSeconds)}</span>
        </div>
        <div class="stepper-pair">
          <button type="button" class="tv-focusable step-btn${isFoc(8) ? ' is-dpad-focused' : ''}" data-builder-idx="8" aria-label="Increase rest time">+</button>
          <span class="stepper-div"></span>
          <button type="button" class="tv-focusable step-btn${isFoc(9) ? ' is-dpad-focused' : ''}" data-builder-idx="9" aria-label="Decrease rest time">−</button>
        </div>
      </div>

      <div class="builder-action-col">
        <div class="total-frame-box">${totalStr}</div>
        <button type="button" class="tv-focusable circle-play-btn${isFoc(10) ? ' is-dpad-focused' : ''}" data-builder-idx="10" aria-label="Start Custom Session">▶</button>
        ${roundSection === 'create' ? `<button type="button" class="tv-focusable opt-pill" data-new-workout>${I18n.t(L, 'save_ok')}</button>
        <div class="save-slots-row">
          <span>${I18n.t(L, 'custom_save_slot_prefix')}</span>
          ${state.presetSlots.map((_, i) => `<button type="button" class="tv-focusable mini-tag" data-save-slot="${i}">${i + 1}</button>`).join('')}
        </div>` : `<button type="button" class="tv-focusable opt-pill" data-custom-stopwatch>${I18n.t(L, 'phase_stopwatch')}</button>`}
      </div>
    `;
  }

  function renderActiveTimer() {
    const L = state.locale;
    const t = state.timer;
    const isEndless = t.config?.repeat === -1;
    let banner = '';

    if (t.phase === 'LEAD_IN') {
      banner = t.leadInCount > 0 ? I18n.t(L, 'phase_prep', { count: t.leadInCount }) : I18n.t(L, 'phase_fight_go');
    } else if (t.phase === 'RUNNING_WORK') {
      if (isEndless) banner = I18n.t(L, 'phase_round_endless', { round: t.currentRound });
      else if (t.totalRounds > 0) banner = I18n.t(L, 'phase_round_progress', { round: t.currentRound, total: t.totalRounds });
      else banner = I18n.t(L, 'phase_single_time');
    } else if (t.phase === 'RUNNING_STOPWATCH') {
      banner = I18n.t(L, 'phase_stopwatch');
    } else if (t.phase === 'RUNNING_REST') {
      if (isEndless) banner = I18n.t(L, 'phase_rest_next_endless', { next: t.currentRound + 1 });
      else if (t.totalRounds > 0) banner = I18n.t(L, 'phase_rest_next', { next: t.currentRound + 1, total: t.totalRounds });
      else banner = I18n.t(L, 'phase_rest');
    } else if (t.phase === 'PAUSED') {
      banner = I18n.t(L, 'phase_paused');
    } else if (t.phase === 'FINISHED') {
      banner = I18n.t(L, 'phase_finished');
    }

    els.phaseHeadline.textContent = banner;
    els.phaseHeadline.classList.toggle('is-long', banner.length > 16);
    els.phaseHeadline.classList.toggle('is-empty', !banner);
    els.timerDigits.textContent = TimeFormatter.formatMmSs(t.remainingSeconds);
    els.timerDigits.classList.toggle('is-blinking', Boolean(t.isBlinking));
    els.timerDigits.classList.toggle('is-rest', t.phase === 'RUNNING_REST');
    els.timerDigits.classList.toggle('is-paused', t.phase === 'PAUSED');
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function renderScoreboardScreen() {
    const L = state.locale;
    const sb = state.scoreboard;
    const paused = state.timer.phase === 'PAUSED' || state.timer.phase === 'FINISHED';

    const durPills = Presets.ibjjfBelts
      .map((b) => {
        const sel = b.seconds === state.scoreboardDurationSeconds;
        return `<button type="button" class="tv-focusable sb-dur-pill${sel ? ' is-selected' : ''}" data-sb-dur="${b.seconds}">${b.label}</button>`;
      })
      .join('');

    const inLead = state.timer.phase === 'LEAD_IN';
    const prepText = inLead
      ? (state.timer.leadInCount > 0 ? I18n.t(L, 'phase_prep', { count: state.timer.leadInCount }) : I18n.t(L, 'phase_fight_go'))
      : '';
    // Persistent overlay node: updated in place so the duration pills are never rebuilt during count-in
    if (!els.sbPrep) {
      els.sbPrep = document.createElement('div');
      els.sbPrep.className = 'sb-prep-tag';
      els.screenScoreboard.appendChild(els.sbPrep);
    }
    els.sbPrep.textContent = prepText;
    els.sbPrep.style.display = prepText ? 'block' : 'none';
    const nameA = sb.fighterAName.trim() || I18n.t(L, 'fighter_a_name');
    const nameB = sb.fighterBName.trim() || I18n.t(L, 'fighter_b_name');

    const fighterCard = (side, name, data, cls) => `
      <div class="fighter-card ${cls}">
        <div class="fighter-card__top">
          <span class="fighter-card__name">${escapeHtml(name)}</span>
          <span class="fighter-card__subscores">
            <span style="color:#d9b234">${I18n.t(L, 'score_advantages')}: ${data.advantages}</span>
            <span style="color:#e8141b">${I18n.t(L, 'score_penalties')}: ${data.penalties}</span>
          </span>
        </div>
        <div class="fighter-card__main">
          <div class="fighter-points">${data.points}</div>
          <div class="fighter-btns">
            <div class="fighter-btns__row">
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:points:2">+2</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:points:3">+3</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:points:4">+4</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:points:-1">−1</button>
            </div>
            <div class="fighter-btns__row">
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:advantages:1">+V</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:advantages:-1">−V</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:penalties:1">+P</button>
              <button type="button" class="tv-focusable sb-btn" data-sb-act="${side}:penalties:-1">−P</button>
            </div>
          </div>
        </div>
      </div>
    `;

    const clockText = TimeFormatter.formatMmSs(state.timer.remainingSeconds);
    const sbStructure = `
      <div class="sb-header">
        <div class="sb-durations">${durPills}</div>
        <div class="sb-clock-group">
          <button type="button" class="tv-focusable sb-step-btn" data-sb-cmd="minus10">−</button><span class="sb-clock-digits">§CLOCK§</span><button type="button" class="tv-focusable sb-step-btn" data-sb-cmd="plus10">+</button>
          <button type="button" class="tv-focusable circle-play-btn" style="width:48px;height:48px" data-sb-cmd="playpause">§PLAY§</button>
          <button type="button" class="tv-focusable opt-pill" data-sb-cmd="reset">↺ ${I18n.t(L, 'score_reset')}</button>
        </div>
      </div>
      <div class="sb-fighters">
        ${fighterCard('A', nameA, sb.a, 'fighter-card--a')}
        ${fighterCard('B', nameB, sb.b, 'fighter-card--b')}
      </div>
    `;
    if (els.scoreboardWrap.__structure !== sbStructure) {
      els.scoreboardWrap.__structure = sbStructure;
      els.scoreboardWrap.innerHTML = sbStructure.replace('§CLOCK§', clockText).replace('§PLAY§', paused ? '▶' : '⏸');
    }
    const playBtn = els.scoreboardWrap.querySelector('[data-sb-cmd="playpause"]');
    if (playBtn) {
      const icon = paused ? '▶' : '⏸';
      if (playBtn.textContent !== icon) playBtn.textContent = icon;
    }
    const digits = els.scoreboardWrap.querySelector('.sb-clock-digits');
    if (digits) {
      if (digits.textContent !== clockText) digits.textContent = clockText;
      digits.classList.toggle('is-blinking', Boolean(state.timer.isBlinking) && !paused);
      digits.style.color = paused ? '#d9b234' : '#f8f5f0';
    }
  }

  function renderSettingsScreen() {
    const L = state.locale;
    const sp = state.soundPreferences;

    const prepBtns = [0, 3, 5, 10]
      .map((s) => `<button type="button" class="tv-focusable opt-pill${sp.prepSeconds === s ? ' is-selected' : ''}" data-set-prep="${s}">${s === 0 ? I18n.t(L, 'set_prep_off') : `${s}s`}</button>`)
      .join('');

    const warnBtns = [
      ['warnHalfway', I18n.t(L, 'warn_halfway_short')],
      ['warn2m', '2m'],
      ['warn1m', '1m'],
      ['beep50s', '50s'],
      ['warn30s', '30s'],
    ]
      .map(([prop, lbl]) => `<button type="button" class="tv-focusable opt-pill${sp[prop] ? ' is-selected' : ''}" data-set-warn-prop="${prop}">${lbl}</button>`)
      .join('');

    const packAndVoiceBtns =
      `<button type="button" class="tv-focusable opt-pill${sp.beep10s ? ' is-selected' : ''}" data-set-cmd="toggle-10s">10s: ${sp.beep10s ? 'ON' : 'OFF'}</button>` +
      `<button type="button" class="tv-focusable opt-pill${sp.voiceEnabled ? ' is-selected' : ''}" data-set-cmd="toggle-voice">${I18n.t(L, 'nav_voice_short')}: ${sp.voiceEnabled ? 'ON' : 'OFF'}</button>` +
      [
        ['CLASSIC_HORN', I18n.t(L, 'pack_horn')],
        ['BELL_GONG', I18n.t(L, 'pack_bell')],
        ['WHISTLE', I18n.t(L, 'pack_whistle')],
      ]
        .map(([id, label]) => `<button type="button" class="tv-focusable opt-pill${sp.soundPack === id ? ' is-selected' : ''}" data-set-pack="${id}">${label}</button>`)
        .join('') +
      `<button type="button" class="tv-focusable opt-pill" data-set-cmd="test-sound">🔊 ${I18n.t(L, 'set_test_sound')}</button>`;

    const langAndClockBtns =
      `<button type="button" class="tv-focusable opt-pill is-selected" data-set-cmd="toggle-24h">${sp.use24HourClock ? '24h' : '12h AM/PM'}</button>` +
      [
        ['pt-PT', 'PT'],
        ['en', 'EN'],
        ['es', 'ES'],
      ]
        .map(([code, lbl]) => `<button type="button" class="tv-focusable opt-pill${state.locale === code ? ' is-selected' : ''}" data-set-lang="${code}">${lbl}</button>`)
        .join('') +
      `<button type="button" class="tv-focusable opt-pill" data-set-cmd="reset-presets">${I18n.t(L, 'set_reset_presets')}</button>`;

    els.settingsPanel.innerHTML = `
      <h2 class="settings-title">${I18n.t(L, 'settings_title')}</h2>
      <div class="set-line"><span class="set-line__label">${I18n.t(L, 'set_prep_label')}</span><div class="set-line__opts">${prepBtns}</div></div>
      <div class="set-line"><span class="set-line__label">${I18n.t(L, 'set_warn_label')}</span><div class="set-line__opts">${warnBtns}</div></div>
      <div class="set-line"><span class="set-line__label">${I18n.t(L, 'set_pack_label')}</span><div class="set-line__opts">${packAndVoiceBtns}</div></div>
      <div class="set-line"><span class="set-line__label">${I18n.t(L, 'set_lang_label')}</span><div class="set-line__opts">${langAndClockBtns}</div></div>
    `;
  }

  function getBottomBarModel() {
    const L = state.locale;
    const left = [];
    const right = [];

    if (state.screen === 'CLOCK') {
      left.push({ id: 'open_rounds', label: I18n.t(L, 'nav_rounds'), kind: 'pill-primary' });
      left.push({ id: 'open_scoreboard', label: I18n.t(L, 'nav_scoreboard'), kind: 'pill-primary' });
      right.push({ id: 'open_settings', label: '⚙ ' + I18n.t(L, 'nav_settings'), kind: 'option' });
    } else if (state.screen === 'ACTIVE_TIMER') {
      left.push({ id: 'back_clock', label: '◀', kind: 'circle' });
      if (state.timer.phase !== 'FINISHED') {
        const paused = state.timer.phase === 'PAUSED';
        left.push({ id: 'play_pause', label: paused ? '▶' : '⏸', kind: 'circle-prominent', paused });
        left.push({ id: 'restart_phase', label: '↺', kind: 'circle' });
        left.push({ id: 'minus_10s', label: '−10s', kind: 'delta' });
        left.push({ id: 'plus_10s', label: '+10s', kind: 'delta' });
        const isMulti = state.timer.config && (state.timer.config.repeat > 0 || state.timer.config.repeat === -1);
        if (isMulti) {
          left.push({ id: 'skip_phase', label: '⏭', kind: 'circle' });
        }
      }
    } else {
      left.push({ id: 'back_clock', label: '◀', kind: 'circle' });
      if (state.screen === 'SETTINGS') {
        right.push({ id: 'download_update', label: I18n.t(L, 'nav_update'), kind: 'option' });
      }
    }

    if (state.screen === 'ROUNDS' || state.screen === 'SCOREBOARD') {
      const on = state.soundPreferences.soundEnabled;
      right.push({ id: 'toggle_sound', kind: 'sound', on, label: I18n.t(L, on ? 'sound_on' : 'sound_off') });
    }

    return { left, right, totalCount: left.length + right.length };
  }

  function renderBarItem(item, idx) {
    const focused = false;
    const fCls = focused ? ' is-dpad-focused' : '';
    if (item.kind === 'option') {
      return `<button type="button" class="tv-focusable opt-pill" data-bar-idx="${idx}" data-bar-action="${item.id}">${item.label}</button>`;
    }
    if (item.kind === 'circle') {
      return `<button type="button" class="tv-focusable media-circle${fCls}" data-bar-idx="${idx}" data-bar-action="${item.id}">${item.label}</button>`;
    }
    if (item.kind === 'circle-prominent') {
      return `<button type="button" class="tv-focusable media-circle media-circle--prominent${item.paused ? ' is-paused' : ''}${fCls}" data-bar-idx="${idx}" data-bar-action="${item.id}">${item.label}</button>`;
    }
    if (item.kind === 'delta') {
      return `<button type="button" class="tv-focusable delta-pill${fCls}" data-bar-idx="${idx}" data-bar-action="${item.id}">${item.label}</button>`;
    }
    if (item.kind === 'sound') {
      return `
        <button type="button" aria-pressed="${item.on}" class="tv-focusable sound-chip${item.on ? ' is-on' : ''}${fCls}" data-bar-idx="${idx}" data-bar-action="${item.id}">
          <span class="sound-chip__dot"></span><span>${item.label}</span>
        </button>
      `;
    }
    const mod = item.kind === 'pill-primary' ? ' bar-pill--primary' : item.kind === 'pill-gold' ? ' bar-pill--gold' : '';
    return `<button type="button" class="tv-focusable bar-pill${mod}${fCls}" data-bar-idx="${idx}" data-bar-action="${item.id}">${item.label}</button>`;
  }

  function renderBottomBar() {
    const L = state.locale;
    const model = getBottomBarModel();
    if (state.barFocusIndex >= model.totalCount) {
      state.barFocusIndex = Math.max(0, model.totalCount - 1);
    }

    let extraLeft = '';
    if (state.screen === 'SCOREBOARD') {
      const fullHref = `http://${state.lanUrl}`;
      const qrSvg = Qr ? Qr.toSvg(fullHref, 36) : '';
      const liveHtml = state.lanConnectedClients > 0
        ? `<span class="lan-banner__live">${I18n.t(L, 'lan_connected_badge', { count: state.lanConnectedClients })}</span>`
        : '';
      const savedHtml = state.matchResults.length > 0
        ? `<span class="lan-banner__saved">${I18n.t(L, 'lan_saved_count', { count: state.matchResults.length })}</span>`
        : '';
      extraLeft = `
        <a class="lan-banner" href="${fullHref}" target="_blank" rel="noopener" title="Abrir Controlo Mobile">
          ${qrSvg}
          <span class="lan-banner__lbl">📱 ${I18n.t(L, 'lan_remote_label')}</span>
          <span class="lan-banner__url">${escapeHtml(state.lanUrl)}</span>
          ${liveHtml}
          ${savedHtml}
        </a>
      `;
    }

    setHtml(els.bottomLeft, model.left.map((item, i) => renderBarItem(item, i)).join('') + extraLeft);
    setHtml(
      els.bottomRight,
      model.right.map((item, rIdx) => renderBarItem(item, model.left.length + rIdx)).join('') +
        `<span class="version-tag"><strong>FEU BJJ</strong> · v1.3.8</span>`
    );
  }

  function renderRemoteCompanion() {
    const L = state.locale;
    els.remoteTitle.textContent = I18n.t(L, 'sim_remote_title');
    els.speedBtn.textContent = state.speedMultiplier > 1 ? I18n.t(L, 'sim_speed_fast') : I18n.t(L, 'sim_speed_normal');
    els.speedBtn.classList.toggle('is-active', state.speedMultiplier > 1);
    els.fullscreenBtn.textContent = document.body.classList.contains('is-tv-only')
      ? I18n.t(L, 'sim_exit_fullscreen')
      : I18n.t(L, 'sim_fullscreen');
    els.testSoundBtn.textContent = `🔊 ${I18n.t(L, 'sim_test_sound')}`;

    document.querySelectorAll('[data-locale]').forEach((btn) => {
      btn.classList.toggle('is-on', btn.getAttribute('data-locale') === state.locale);
    });
  }

  function renderAll() {
    updateAmbientTheme();
    renderTopChrome();
    renderScreensVisibility();
    renderClockScreen();
    renderQuickScreen();
    renderRoundsScreen();
    renderActiveTimer();
    renderScoreboardScreen();
    renderSettingsScreen();
    renderBottomBar();
    renderRemoteCompanion();
  }

  function triggerBarAction(actionId) {
    if (actionId === 'open_quick') openQuickTimers();
    else if (actionId === 'open_rounds') openRounds();
    else if (actionId === 'open_scoreboard') openScoreboard();
    else if (actionId === 'open_settings') openSettings();
    else if (actionId === 'download_update') {
      const localServer = tvWs?.readyState === WebSocket.OPEN && ['localhost', '127.0.0.1'].includes(location.hostname);
      location.assign(localServer ? '/update/web' : 'https://github.com/ryoken99/FeuTimer/releases/latest/download/FeuTimer-web.zip');
    }
    else if (actionId === 'repeat_last') startSession(state.lastSessionConfig);
    else if (actionId === 'back_clock') onBackAction();
    else if (actionId === 'play_pause') togglePlayPause();
    else if (actionId === 'restart_phase') {
      Audio.stopAll();
      state.timer = TimerEngine.restartPhase(state.timer);
      renderActiveTimer();
    } else if (actionId === 'minus_10s') {
      state.timer = TimerEngine.adjustTime(state.timer, -10);
      renderActiveTimer();
    } else if (actionId === 'plus_10s') {
      state.timer = TimerEngine.adjustTime(state.timer, +10);
      renderActiveTimer();
    } else if (actionId === 'skip_phase') {
      Audio.stopAll();
      const step = TimerEngine.skipPhase(state.timer, state.soundPreferences);
      state.timer = step.snapshot;
      playCue(step.cue);
      if (state.timer.phase === 'FINISHED') returnToClock();
      else {
        renderActiveTimer();
        updateAmbientTheme();
      }
    } else if (actionId === 'toggle_sound') {
      state.soundPreferences.soundEnabled = !state.soundPreferences.soundEnabled;
      if (!state.soundPreferences.soundEnabled) Audio.stopAll();
      persistSettings();
      renderBottomBar();
      applyDomFocus(els.bottomRight.querySelector('[data-bar-action="toggle_sound"]'));
    }
  }

  function triggerRoundsBuilderAction(idx) {
    if (idx >= 0 && idx <= 3) {
      startSession(state.presetSlots[idx]);
      return;
    }
    if (idx === 4) state.customBuilder = CustomBuilder.adjustRepeat(state.customBuilder, true);
    else if (idx === 5) state.customBuilder = CustomBuilder.adjustRepeat(state.customBuilder, false);
    else if (idx === 6) state.customBuilder = CustomBuilder.adjustWorkSmart(state.customBuilder, true);
    else if (idx === 7) state.customBuilder = CustomBuilder.adjustWorkSmart(state.customBuilder, false);
    else if (idx === 8) state.customBuilder = CustomBuilder.adjustRestSmart(state.customBuilder, true);
    else if (idx === 9) state.customBuilder = CustomBuilder.adjustRestSmart(state.customBuilder, false);
    else if (idx === 10) {
      persistSettings();
      startSession(state.customBuilder);
      return;
    }
    persistSettings();
    renderRoundsScreen();
  }

  // ---- DOM-based spatial focus: every visible control is reachable, no stuck states ----
  let focusSig = null;
  let focusScreen = null;

  function listFocusables() {
    if (isModalOpen()) {
      return Array.from(document.querySelectorAll('#modal-layer .tv-focusable'));
    }
    const activeScreen = els.viewport.querySelector('.tv-screen.is-active');
    const nodes = [];
    if (activeScreen) nodes.push(...activeScreen.querySelectorAll('.tv-focusable'));
    nodes.push(...els.viewport.querySelectorAll('.bottom-bar .tv-focusable'));
    return nodes.filter((n) => {
      const r = n.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    });
  }

  const GROUP_SELECTOR = '.quick-options, .builder-row, .presets-row, .templates-strip, .quick-grid, .set-line, .sb-header, .fighter-card, .quick-topbar, .bar-group';

  function groupIdsFor(nodes) {
    const seen = new Map();
    return nodes.map((n) => {
      const g = n.closest(GROUP_SELECTOR);
      if (!g) return null;
      if (!seen.has(g)) seen.set(g, seen.size);
      return seen.get(g);
    });
  }

  function signatureOf(el) {
    const attrs = Array.from(el.attributes)
      .filter((a) => a.name.startsWith('data-'))
      .map((a) => `${a.name}=${a.value}`)
      .join('|');
    return `${attrs}#${(el.textContent || '').trim()}`;
  }

  function defaultFocusTarget(list) {
    if (state.screen === 'ACTIVE_TIMER') {
      const pp = list.find((n) => n.getAttribute('data-bar-action') === 'play_pause');
      if (pp) return pp;
    }
    if (state.screen === 'CLOCK') {
      const first = list.find((n) => n.hasAttribute('data-bar-action'));
      if (first) return first;
    }
    return list[0] || null;
  }

  function applyDomFocus(target) {
    const current = Array.from(els.viewport.querySelectorAll('.is-dpad-focused'));
    if (target && current.length === 1 && current[0] === target) {
      focusSig = signatureOf(target);
      focusScreen = state.screen;
      return;
    }
    current.forEach((n) => n.classList.remove('is-dpad-focused'));
    if (!target) {
      updateSavePreview();
      return;
    }
    target.classList.add('is-dpad-focused');
    updateSavePreview();
    focusSig = signatureOf(target);
    focusScreen = state.screen;
  }

  function restoreDomFocus() {
    const list = listFocusables();
    if (list.length === 0) return;
    let target = null;
    if (focusScreen === state.screen && focusSig) {
      target = list.find((n) => signatureOf(n) === focusSig) || null;
    }
    if (!target) target = defaultFocusTarget(list);
    applyDomFocus(target);
  }

  function handleDpadDirection(dir) {
    Audio.unlock();
    document.body.classList.add('dpad-mode');
    const list = listFocusables();
    if (list.length === 0) return;
    let curIdx = list.findIndex((n) => n.classList.contains('is-dpad-focused'));
    if (curIdx < 0) {
      restoreDomFocus();
      curIdx = list.findIndex((n) => n.classList.contains('is-dpad-focused'));
      if (curIdx < 0) curIdx = 0;
    }
    const rects = list.map((n) => {
      const r = n.getBoundingClientRect();
      return { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
    });
    const nextIdx = DpadGrid.findNext(rects, curIdx, dir, groupIdsFor(list));
    applyDomFocus(list[nextIdx >= 0 ? nextIdx : curIdx]);
  }

  function handleDpadSelect() {
    Audio.unlock();
    document.body.classList.add('dpad-mode');
    let el = els.viewport.querySelector('.is-dpad-focused');
    if (!el || !el.isConnected) {
      restoreDomFocus();
      el = els.viewport.querySelector('.is-dpad-focused');
    }
    if (el?.id === 'slot-name') el.focus();
    else if (el) el.click();
  }

  window.FEU_TIMER.__focusProbe = () => {
    const nodes = listFocusables();
    return {
      nodes,
      rects: nodes.map((n) => { const r = n.getBoundingClientRect(); return { left: r.left, top: r.top, right: r.right, bottom: r.bottom }; }),
      groups: groupIdsFor(nodes),
    };
  };

  function installFocusObserver() {
    let pending = false;
    const schedule = () => {
      if (pending) return;
      pending = true;
      queueMicrotask(() => {
        pending = false;
        restoreDomFocus();
      });
    };
    new MutationObserver(schedule).observe(els.viewport, { childList: true, subtree: true });
    window.addEventListener('resize', schedule);
  }

  function toggleTvOnlyMode() {
    document.body.classList.toggle('is-tv-only');
    renderRemoteCompanion();
  }

  function wireEvents() {
    els.stopwatchBtn.addEventListener('click', () => {
      Audio.unlock();
      startSession({ repeat: 0, workSeconds: 0, restSeconds: 0, isStopwatch: true });
    });

    els.viewport.addEventListener('pointerdown', () => {
      document.body.classList.remove('dpad-mode');
      updateSavePreview();
    });
    els.viewport.addEventListener('mouseover', (ev) => {
      const b = ev.target.closest('[data-save-slot]');
      const next = b ? Number(b.getAttribute('data-save-slot')) : null;
      if (next !== hoverSaveSlot) {
        hoverSaveSlot = next;
        updateSavePreview();
      }
    });
    els.viewport.addEventListener('click', (ev) => {
      Audio.unlock();
      const modalAct = ev.target.closest('[data-modal-act]');
      if (modalAct) {
        if (modalAct.getAttribute('data-modal-act') === 'ok') confirmSaveToSlot();
        else closeModal();
        return;
      }
      if (isModalOpen()) {
        if (ev.target.classList.contains('modal-layer')) closeModal();
        return;
      }
      const clickedFocusable = ev.target.closest('.tv-focusable');
      if (clickedFocusable) applyDomFocus(clickedFocusable);
      if (ev.target.closest('.lan-banner')) return;

      const pageButton = ev.target.closest('[data-page-step]');
      if (pageButton) {
        const pages = Math.ceil(state.presetSlots.length / workoutsPerPage);
        workoutPage = (workoutPage + Number(pageButton.dataset.pageStep) + pages) % pages;
        renderRoundsScreen();
        applyDomFocus(els.presetsRow.querySelector('.preset-card'));
        return;
      }
      if (ev.target.closest('[data-create-workout]')) {
        roundSection = 'create';
        renderRoundsScreen();
        applyDomFocus(els.builderRow.querySelector('[data-builder-idx="10"]'));
        return;
      }
      if (ev.target.closest('[data-new-workout]')) {
        openSaveDialog(state.presetSlots.length);
        return;
      }
      if (ev.target.closest('[data-custom-stopwatch]')) {
        els.stopwatchBtn.click();
        return;
      }
      const sectionButton = ev.target.closest('[data-round-section]');
      if (sectionButton) {
        roundSection = sectionButton.dataset.roundSection;
        renderRoundsScreen();
        const first = els.viewport.querySelector('.rounds-wrap [data-' + ({ presets: 'preset-idx', builder: 'builder-cmd' }[roundSection]) + ']');
        applyDomFocus(first);
        return;
      }

      const quickOpt = ev.target.closest('[data-quick-opt]');
      if (quickOpt) {
        const k = quickOpt.getAttribute('data-quick-opt');
        if (k === 'rep-') state.quickRepeat = Math.max(1, state.quickRepeat - 1);
        else if (k === 'rep+') state.quickRepeat = Math.min(99, state.quickRepeat + 1);
        else adjustQuickRest(k === 'rest+');
        persistSettings();
        renderQuickScreen();
        return;
      }

      const quickBtn = ev.target.closest('[data-quick-idx]');
      if (quickBtn) {
        const idx = Number(quickBtn.getAttribute('data-quick-idx'));
        state.focusZone = 'CONTENT';
        state.contentFocusIndex = idx;
        const opt = Presets.quickTimers[idx];
        if (opt) startSession(quickConfigFor(opt.seconds));
        return;
      }

      const presetBtn = ev.target.closest('[data-preset-idx]');
      if (presetBtn) {
        const idx = Number(presetBtn.getAttribute('data-preset-idx'));
        state.focusZone = 'CONTENT';
        state.contentFocusIndex = idx;
        startSession(state.presetSlots[idx]);
        return;
      }

      const builderCmd = ev.target.closest('[data-builder-cmd]');
      if (builderCmd) {
        const cmd = builderCmd.getAttribute('data-builder-cmd');
        if (cmd === 'toggle-endless') state.customBuilder = CustomBuilder.toggleEndless(state.customBuilder);
        else if (cmd === 'work+60') state.customBuilder = CustomBuilder.adjustWork(state.customBuilder, true, 60);
        else if (cmd === 'work-60') state.customBuilder = CustomBuilder.adjustWork(state.customBuilder, false, 60);
        else if (cmd === 'rest+60') state.customBuilder = CustomBuilder.adjustRest(state.customBuilder, true, 60);
        else if (cmd === 'rest-60') state.customBuilder = CustomBuilder.adjustRest(state.customBuilder, false, 60);
        persistSettings();
        renderRoundsScreen();
        return;
      }

      const saveSlotBtn = ev.target.closest('[data-save-slot]');
      if (saveSlotBtn) {
        openSaveDialog(Number(saveSlotBtn.getAttribute('data-save-slot')));
        return;
      }

      const builderBtn = ev.target.closest('[data-builder-idx]');
      if (builderBtn) {
        const idx = Number(builderBtn.getAttribute('data-builder-idx'));
        state.focusZone = 'CONTENT';
        state.contentFocusIndex = idx;
        triggerRoundsBuilderAction(idx);
        return;
      }

      // Scoreboard events
      const sbDur = ev.target.closest('[data-sb-dur]');
      if (sbDur) {
        selectScoreboardDuration(Number(sbDur.getAttribute('data-sb-dur')));
        return;
      }
      const sbCmd = ev.target.closest('[data-sb-cmd]');
      if (sbCmd) {
        const cmd = sbCmd.getAttribute('data-sb-cmd');
        if (cmd === 'playpause') togglePlayPause();
        else if (cmd === 'minus10' || cmd === 'plus10') {
          state.timer = TimerEngine.adjustTime(state.timer, cmd === 'plus10' ? 10 : -10);
          renderScoreboardScreen();
          pushTvStateToHub();
        } else if (cmd === 'reset') {
          state.scoreboard = Scoreboard.reset();
          persistSettings();
          selectScoreboardDuration(state.scoreboardDurationSeconds);
        }
        return;
      }
      const sbAct = ev.target.closest('[data-sb-act]');
      if (sbAct) {
        const [side, field, deltaStr] = sbAct.getAttribute('data-sb-act').split(':');
        state.scoreboard = Scoreboard.adjust(state.scoreboard, side, field, Number(deltaStr));
        persistSettings();
        renderScoreboardScreen();
        pushTvStateToHub();
        return;
      }

      // Settings events
      const setPrep = ev.target.closest('[data-set-prep]');
      if (setPrep) {
        state.soundPreferences.prepSeconds = Number(setPrep.getAttribute('data-set-prep'));
        persistSettings();
        renderSettingsScreen();
        return;
      }
      const setWarnProp = ev.target.closest('[data-set-warn-prop]');
      if (setWarnProp) {
        const prop = setWarnProp.getAttribute('data-set-warn-prop');
        state.soundPreferences[prop] = !state.soundPreferences[prop];
        persistSettings();
        renderSettingsScreen();
        return;
      }
      const setPack = ev.target.closest('[data-set-pack]');
      if (setPack) {
        state.soundPreferences.soundPack = setPack.getAttribute('data-set-pack');
        persistSettings();
        playCue('FIGHT_HORN');
        renderSettingsScreen();
        return;
      }
      const setLang = ev.target.closest('[data-set-lang]');
      if (setLang) {
        state.locale = setLang.getAttribute('data-set-lang');
        persistSettings();
        renderAll();
        return;
      }
      const setCmd = ev.target.closest('[data-set-cmd]');
      if (setCmd) {
        const cmd = setCmd.getAttribute('data-set-cmd');
        if (cmd === 'test-sound') playCue('FIGHT_HORN');
        else if (cmd === 'toggle-10s') state.soundPreferences.beep10s = !state.soundPreferences.beep10s;
        else if (cmd === 'toggle-voice') state.soundPreferences.voiceEnabled = !state.soundPreferences.voiceEnabled;
        else if (cmd === 'toggle-24h') state.soundPreferences.use24HourClock = !state.soundPreferences.use24HourClock;
        else if (cmd === 'reset-presets') state.presetSlots = Presets.roundPresets.map((p) => ({ ...p }));
        persistSettings();
        renderAll();
        return;
      }

      const barBtn = ev.target.closest('[data-bar-action]');
      if (barBtn) {
        const idx = Number(barBtn.getAttribute('data-bar-idx'));
        state.focusZone = 'BAR';
        state.barFocusIndex = idx;
        triggerBarAction(barBtn.getAttribute('data-bar-action'));
      }
    });

    document.querySelectorAll('[data-dpad]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const cmd = btn.getAttribute('data-dpad');
        if (cmd === 'ok') handleDpadSelect();
        else if (cmd === 'back') onBackAction();
        else if (cmd === 'playpause') togglePlayPause();
        else handleDpadDirection(cmd);
      });
    });

    document.querySelectorAll('[data-locale]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.locale = btn.getAttribute('data-locale');
        persistSettings();
        renderAll();
      });
    });

    els.speedBtn.addEventListener('click', () => {
      state.speedMultiplier = state.speedMultiplier === 1 ? 10 : 1;
      lastTickAnchorMs = performance.now();
      renderRemoteCompanion();
    });

    els.fullscreenBtn.addEventListener('click', toggleTvOnlyMode);
    document.getElementById('btn-restore-dock').addEventListener('click', toggleTvOnlyMode);

    els.testSoundBtn.addEventListener('click', () => {
      Audio.unlock();
      playCue('FIGHT_HORN');
    });

    window.addEventListener('keydown', (ev) => {
      if (ev.metaKey || ev.ctrlKey || ev.altKey || ev.isComposing) return;
      if (ev.target.id === 'slot-name') {
        if (ev.key === 'Enter' || ev.key === 'ArrowDown') {
          ev.preventDefault();
          ev.target.blur();
          applyDomFocus(document.getElementById('modal-ok'));
        } else if (ev.key === 'Escape') { ev.preventDefault(); closeModal(); }
        return;
      }
      if (ev.key === 'ArrowUp') { ev.preventDefault(); handleDpadDirection('up'); }
      else if (ev.key === 'ArrowDown') { ev.preventDefault(); handleDpadDirection('down'); }
      else if (ev.key === 'ArrowLeft') { ev.preventDefault(); handleDpadDirection('left'); }
      else if (ev.key === 'ArrowRight') { ev.preventDefault(); handleDpadDirection('right'); }
      else if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); handleDpadSelect(); }
      else if (ev.key === 'Escape' || ev.key === 'Backspace') { ev.preventDefault(); onBackAction(); }
      else if (ev.key === 'p' || ev.key === 'P' || ev.key === 'MediaPlayPause') { ev.preventDefault(); togglePlayPause(); }
      else if (ev.key === 'f' || ev.key === 'F') { ev.preventDefault(); toggleTvOnlyMode(); }
    });
  }

  // Sound is blocked until someone interacts with THIS page (e.g. a match started from the phone first): say so
  let lastLockedHintMs = 0;
  window.addEventListener('feu-audio-locked', () => {
    const now = performance.now();
    if (now - lastLockedHintMs < 6000) return;
    lastLockedHintMs = now;
    showToast(I18n.t(state.locale, 'audio_locked'), 'warn');
  });

  function init() {
    cacheElements();
    setupViewportScaleLock();
    if (new URLSearchParams(window.location.search).get('tv') === '1') {
      document.body.classList.add('is-tv-only');
    }
    document.body.classList.add('rounds-preview');
    if (new URLSearchParams(location.search).get('abrir') === 'definicoes') state.screen = 'SETTINGS';
    wireEvents();
    renderAll();
    installFocusObserver();
    restoreDomFocus();
    connectTvHub();
    setInterval(updateWallClocks, 250);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
