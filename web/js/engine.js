/*
 * Pure deterministic state machine matching pt.feubjj.timer.domain.engine.TimerEngine + MatchScoreState:
 * - Vocal countdown numbers COUNT_10..COUNT_1 for all pre-round pips AND final 10s round countdown
 * - Mid-round vocal warnings (WARN_HALFWAY, WARN_2M, WARN_1M, WARN_50S, WARN_30S)
 * - Peaceful, silent rest breaks (zero breathing pips)
 * - Live athlete names, direct score editing, side swap, and IBJJF winner computation
 */
(function () {
  'use strict';

  const root = (typeof window !== 'undefined' ? window : globalThis);
  root.FEU_TIMER = root.FEU_TIMER || {};

  const MIN_REPEAT = 1;
  const MAX_REPEAT = 99;
  const MIN_SMALL_WORK = 5;
  const MIN_WORK = 10;
  const MIN_REST = 0;
  const MAX_SECONDS = 3600;

  function clamp(val, min, max) {
    return Math.max(min, Math.min(max, val));
  }

  function cueForCountdownSecond(sec) {
    return sec >= 1 && sec <= 10 ? `COUNT_${sec}` : 'COUNTDOWN_PIP';
  }

  const CustomBuilder = {
    create(initial) {
      const isEndless = initial?.repeat === -1;
      const rep = isEndless ? -1 : clamp(initial?.repeat ?? 2, MIN_REPEAT, MAX_REPEAT);
      return {
        repeat: rep,
        lastFiniteRepeat: isEndless ? clamp(initial?.lastFiniteRepeat ?? 5, MIN_REPEAT, MAX_REPEAT) : rep,
        workSeconds: clamp(initial?.workSeconds ?? 60, MIN_SMALL_WORK, MAX_SECONDS),
        restSeconds: clamp(initial?.restSeconds ?? 30, MIN_REST, MAX_SECONDS),
      };
    },
    toggleEndless(state) {
      if (state.repeat === -1) {
        return { ...state, repeat: clamp(state.lastFiniteRepeat || 5, MIN_REPEAT, MAX_REPEAT) };
      }
      return {
        ...state,
        repeat: -1,
        lastFiniteRepeat: clamp(state.repeat, MIN_REPEAT, MAX_REPEAT),
      };
    },
    adjustRepeat(state, increase, step = 1) {
      if (state.repeat === -1) {
        return increase ? { ...state, repeat: clamp(state.lastFiniteRepeat || 5, MIN_REPEAT, MAX_REPEAT) } : state;
      }
      const delta = increase ? step : -step;
      const next = clamp(state.repeat + delta, MIN_REPEAT, MAX_REPEAT);
      return { ...state, repeat: next, lastFiniteRepeat: next };
    },
    adjustWork(state, increase, step = 10) {
      const delta = increase ? step : -step;
      return { ...state, workSeconds: clamp(state.workSeconds + delta, MIN_WORK, MAX_SECONDS) };
    },
    adjustWorkSmart(state, increase) {
      const step = (increase && state.workSeconds < 30) || (!increase && state.workSeconds <= 30) ? 5 : 10;
      const delta = increase ? step : -step;
      return { ...state, workSeconds: clamp(state.workSeconds + delta, MIN_SMALL_WORK, MAX_SECONDS) };
    },
    adjustRest(state, increase, step = 10) {
      const delta = increase ? step : -step;
      return { ...state, restSeconds: clamp(state.restSeconds + delta, MIN_REST, MAX_SECONDS) };
    },
    adjustRestSmart(state, increase) {
      const step = (increase && state.restSeconds < 30) || (!increase && state.restSeconds <= 30) ? 5 : 10;
      const delta = increase ? step : -step;
      return { ...state, restSeconds: clamp(state.restSeconds + delta, MIN_REST, MAX_SECONDS) };
    },
    totalSeconds(state) {
      if (state.repeat === -1) return 0;
      return state.repeat * state.workSeconds + Math.max(0, state.repeat - 1) * state.restSeconds;
    },
  };

  function advanceToNextWork(current, config, preferences) {
    const nextRound = current.currentRound + 1;
    const prep = Math.max(0, preferences?.prepSeconds ?? 3);
    if (prep > 0) {
      return {
        snapshot: {
          ...current,
          phase: 'LEAD_IN',
          pausedFromPhase: null,
          currentRound: nextRound,
          remainingSeconds: config.workSeconds,
          phaseTotalSeconds: config.workSeconds,
          leadInCount: prep,
          isBlinking: true,
        },
        cue: cueForCountdownSecond(prep),
      };
    }
    return {
      snapshot: {
        ...current,
        phase: 'RUNNING_WORK',
        pausedFromPhase: null,
        currentRound: nextRound,
        remainingSeconds: config.workSeconds,
        phaseTotalSeconds: config.workSeconds,
        leadInCount: 0,
        isBlinking: config.workSeconds <= 10,
      },
      cue: 'FIGHT_HORN',
    };
  }

  function resolveWorkTickCue(nextSeconds, totalWorkSeconds, prefs) {
    if (nextSeconds >= 1 && nextSeconds <= 10) {
      return prefs?.beep10s !== false ? cueForCountdownSecond(nextSeconds) : null;
    }
    if (nextSeconds >= totalWorkSeconds) return null;

    if (nextSeconds === 120 && prefs?.warn2m) return 'WARN_2M';
    if (nextSeconds === 60 && prefs?.warn1m) return 'WARN_1M';
    if (nextSeconds === 50 && prefs?.beep50s !== false) return 'WARN_50S';
    if (nextSeconds === 30 && prefs?.warn30s) return 'WARN_30S';

    if (prefs?.warnHalfway !== false && totalWorkSeconds >= 40) {
      const half = Math.floor(totalWorkSeconds / 2);
      if (nextSeconds === half && half > 12) {
        const activeMarks = [];
        if (prefs?.warn2m && totalWorkSeconds > 120) activeMarks.push(120);
        if (prefs?.warn1m && totalWorkSeconds > 60) activeMarks.push(60);
        if (prefs?.beep50s !== false && totalWorkSeconds > 50) activeMarks.push(50);
        if (prefs?.warn30s && totalWorkSeconds > 30) activeMarks.push(30);
        if (!activeMarks.some((m) => Math.abs(half - m) < 3)) {
          return 'WARN_HALFWAY';
        }
      }
    }
    return null;
  }

  const TimerEngine = {
    idle() {
      return {
        phase: 'IDLE',
        config: null,
        currentRound: 1,
        totalRounds: 0,
        remainingSeconds: 0,
        phaseTotalSeconds: 0,
        leadInCount: 0,
        isBlinking: false,
        pausedFromPhase: null,
      };
    },

    start(config, preferences, includeLeadIn = true) {
      const prep = Math.max(0, preferences?.prepSeconds ?? 3);
      if (config.isStopwatch) {
        if (includeLeadIn && prep > 0) {
          return {
            snapshot: {
              phase: 'LEAD_IN',
              config,
              currentRound: 1,
              totalRounds: 0,
              remainingSeconds: 0,
              phaseTotalSeconds: 0,
              leadInCount: prep,
              isBlinking: true,
              pausedFromPhase: null,
            },
            cue: cueForCountdownSecond(prep),
          };
        }
        return {
          snapshot: {
            phase: 'RUNNING_STOPWATCH',
            config,
            currentRound: 1,
            totalRounds: 0,
            remainingSeconds: 0,
            phaseTotalSeconds: 0,
            leadInCount: 0,
            isBlinking: false,
            pausedFromPhase: null,
          },
          cue: includeLeadIn ? 'FIGHT_HORN' : null,
        };
      }

      const totalRounds = config.repeat === -1 ? 0 : Math.max(0, config.repeat || 0);
      if (includeLeadIn && prep > 0) {
        return {
          snapshot: {
            phase: 'LEAD_IN',
            config,
            currentRound: 1,
            totalRounds,
            remainingSeconds: config.workSeconds,
            phaseTotalSeconds: config.workSeconds,
            leadInCount: prep,
            isBlinking: true,
            pausedFromPhase: null,
          },
          cue: cueForCountdownSecond(prep),
        };
      }
      return {
        snapshot: {
          phase: 'RUNNING_WORK',
          config,
          currentRound: 1,
          totalRounds,
          remainingSeconds: config.workSeconds,
          phaseTotalSeconds: config.workSeconds,
          leadInCount: 0,
          isBlinking: config.workSeconds <= 10,
          pausedFromPhase: null,
        },
        cue: includeLeadIn ? 'FIGHT_HORN' : null,
      };
    },

    pause(snapshot) {
      if (!['LEAD_IN', 'RUNNING_WORK', 'RUNNING_REST', 'RUNNING_STOPWATCH'].includes(snapshot.phase)) {
        return snapshot;
      }
      return {
        ...snapshot,
        phase: 'PAUSED',
        isBlinking: true,
        pausedFromPhase: snapshot.phase,
      };
    },

    resume(snapshot) {
      if (snapshot.phase !== 'PAUSED') return snapshot;
      const restored = snapshot.pausedFromPhase || 'RUNNING_WORK';
      const isBlinking =
        restored === 'LEAD_IN' ||
        (restored === 'RUNNING_WORK' && snapshot.remainingSeconds >= 1 && snapshot.remainingSeconds <= 10);
      return {
        ...snapshot,
        phase: restored,
        isBlinking,
        pausedFromPhase: null,
      };
    },

    adjustTime(current, deltaSeconds) {
      const eff = current.phase === 'PAUSED' ? current.pausedFromPhase : current.phase;
      if (eff === 'RUNNING_STOPWATCH') {
        return { ...current, remainingSeconds: Math.max(0, current.remainingSeconds + deltaSeconds) };
      }
      if (!['LEAD_IN', 'RUNNING_WORK', 'RUNNING_REST'].includes(eff)) return current;
      const adjusted = clamp(current.remainingSeconds + deltaSeconds, 1, 7200);
      const newTotal = Math.max(current.phaseTotalSeconds, adjusted);
      const isBlinking =
        current.phase === 'PAUSED' || current.phase === 'LEAD_IN' || (current.phase === 'RUNNING_WORK' && adjusted <= 10);
      return { ...current, remainingSeconds: adjusted, phaseTotalSeconds: newTotal, isBlinking };
    },

    setExactTime(current, exactSeconds) {
      const clamped = clamp(exactSeconds, 1, 7200);
      const newTotal = Math.max(current.phaseTotalSeconds, clamped);
      const isBlinking =
        current.phase === 'PAUSED' || current.phase === 'LEAD_IN' || (current.phase === 'RUNNING_WORK' && clamped <= 10);
      return { ...current, remainingSeconds: clamped, phaseTotalSeconds: newTotal, isBlinking };
    },

    restartPhase(current) {
      const cfg = current.config;
      if (!cfg) return current;
      if (cfg.isStopwatch) {
        return { ...current, remainingSeconds: 0, isBlinking: current.phase === 'PAUSED' };
      }
      return {
        ...current,
        phase: current.phase === 'PAUSED' ? 'PAUSED' : 'RUNNING_WORK',
        pausedFromPhase: current.phase === 'PAUSED' ? 'RUNNING_WORK' : null,
        remainingSeconds: cfg.workSeconds,
        phaseTotalSeconds: cfg.workSeconds,
        leadInCount: 0,
        isBlinking: current.phase === 'PAUSED' ? true : cfg.workSeconds <= 10,
      };
    },

    skipPhase(current, preferences) {
      const cfg = current.config;
      if (!cfg || cfg.isStopwatch) return { snapshot: current, cue: null };
      const eff = current.phase === 'PAUSED' ? (current.pausedFromPhase || 'RUNNING_WORK') : current.phase;
      if (eff === 'LEAD_IN') {
        return {
          snapshot: {
            ...current,
            phase: 'RUNNING_WORK',
            pausedFromPhase: null,
            leadInCount: 0,
            remainingSeconds: cfg.workSeconds,
            phaseTotalSeconds: cfg.workSeconds,
            isBlinking: cfg.workSeconds <= 10,
          },
          cue: 'FIGHT_HORN',
        };
      }
      if (eff === 'RUNNING_WORK') {
        const isEndless = cfg.repeat === -1;
        const hasMore = isEndless || (cfg.repeat > 0 && current.currentRound < current.totalRounds);
        if (!hasMore) {
          return {
            snapshot: { ...current, phase: 'FINISHED', pausedFromPhase: null, remainingSeconds: 0, isBlinking: false },
            cue: 'ROUND_END_ALARM',
          };
        }
        if (cfg.restSeconds > 0) {
          return {
            snapshot: {
              ...current,
              phase: 'RUNNING_REST',
              pausedFromPhase: null,
              remainingSeconds: cfg.restSeconds,
              phaseTotalSeconds: cfg.restSeconds,
              isBlinking: false,
            },
            cue: 'ROUND_END_TO_REST',
          };
        }
        return advanceToNextWork(current, cfg, preferences);
      }
      if (eff === 'RUNNING_REST') {
        return {
          snapshot: {
            ...current,
            phase: 'RUNNING_WORK',
            pausedFromPhase: null,
            currentRound: current.currentRound + 1,
            remainingSeconds: cfg.workSeconds,
            phaseTotalSeconds: cfg.workSeconds,
            leadInCount: 0,
            isBlinking: cfg.workSeconds <= 10,
          },
          cue: 'FIGHT_HORN',
        };
      }
      return { snapshot: current, cue: null };
    },

    previousRound(current) {
      const cfg = current.config;
      if (!cfg) return current;
      if (cfg.isStopwatch) return this.restartPhase(current);
      return {
        ...current,
        phase: 'RUNNING_WORK',
        pausedFromPhase: null,
        currentRound: Math.max(1, current.currentRound - 1),
        remainingSeconds: cfg.workSeconds,
        phaseTotalSeconds: cfg.workSeconds,
        leadInCount: 0,
        isBlinking: cfg.workSeconds <= 10,
      };
    },

    tick(current, preferences) {
      if (!current.config || ['IDLE', 'PAUSED', 'FINISHED'].includes(current.phase)) {
        return { snapshot: current, cue: null };
      }

      const config = current.config;
      if (current.phase === 'RUNNING_STOPWATCH') {
        return {
          snapshot: { ...current, remainingSeconds: current.remainingSeconds + 1, isBlinking: false },
          cue: null,
        };
      }

      if (current.phase === 'LEAD_IN') {
        if (current.leadInCount > 1) {
          const nextCount = current.leadInCount - 1;
          return {
            snapshot: { ...current, leadInCount: nextCount, isBlinking: true },
            cue: cueForCountdownSecond(nextCount),
          };
        }
        if (current.leadInCount === 1) {
          return {
            snapshot: { ...current, leadInCount: 0, isBlinking: true },
            cue: 'FIGHT_HORN',
          };
        }
        const nextPhase = config.isStopwatch ? 'RUNNING_STOPWATCH' : 'RUNNING_WORK';
        return {
          snapshot: {
            ...current,
            phase: nextPhase,
            leadInCount: 0,
            isBlinking: nextPhase === 'RUNNING_WORK' && current.remainingSeconds >= 1 && current.remainingSeconds <= 10,
          },
          cue: null,
        };
      }

      if (current.phase === 'RUNNING_WORK') {
        const nextSeconds = Math.max(0, current.remainingSeconds - 1);
        if (nextSeconds > 0) {
          const cue = resolveWorkTickCue(nextSeconds, config.workSeconds, preferences);
          return {
            snapshot: {
              ...current,
              remainingSeconds: nextSeconds,
              isBlinking: current.isBlinking || nextSeconds <= 10,
            },
            cue,
          };
        }

        const isEndless = config.repeat === -1;
        const hasMoreRounds = isEndless || (config.repeat > 0 && current.currentRound < current.totalRounds);
        if (!hasMoreRounds) {
          return {
            snapshot: {
              ...current,
              phase: 'FINISHED',
              remainingSeconds: 0,
              isBlinking: false,
            },
            cue: 'ROUND_END_ALARM',
          };
        }

        if (config.restSeconds > 0) {
          return {
            snapshot: {
              ...current,
              phase: 'RUNNING_REST',
              remainingSeconds: config.restSeconds,
              phaseTotalSeconds: config.restSeconds,
              isBlinking: false,
            },
            cue: 'ROUND_END_TO_REST',
          };
        }

        return advanceToNextWork(current, config, preferences);
      }

      if (current.phase === 'RUNNING_REST') {
        const nextSeconds = Math.max(0, current.remainingSeconds - 1);
        if (nextSeconds > 0) {
          // Peaceful silent break countdown on screen
          return {
            snapshot: {
              ...current,
              remainingSeconds: nextSeconds,
              isBlinking: false,
            },
            cue: null,
          };
        }
        return advanceToNextWork(current, config, preferences);
      }

      return { snapshot: current, cue: null };
    },
  };

  const Scoreboard = {
    create(initial) {
      return {
        fighterAName: initial?.fighterAName || '',
        fighterBName: initial?.fighterBName || '',
        category: initial?.category || '',
        a: {
          points: initial?.a?.points || 0,
          advantages: initial?.a?.advantages || 0,
          penalties: initial?.a?.penalties || 0,
        },
        b: {
          points: initial?.b?.points || 0,
          advantages: initial?.b?.advantages || 0,
          penalties: initial?.b?.penalties || 0,
        },
      };
    },
    setName(state, side, name) {
      const trimmed = String(name ?? '').slice(0, 40);
      return side === 'B' ? { ...state, fighterBName: trimmed } : { ...state, fighterAName: trimmed };
    },
    setCategory(state, category) {
      return { ...state, category: String(category ?? '').slice(0, 48) };
    },
    adjust(state, side, field, delta) {
      const key = side === 'B' ? 'b' : 'a';
      const maxVal = field === 'points' ? 999 : 99;
      const nextVal = clamp((state[key][field] || 0) + delta, 0, maxVal);
      return {
        ...state,
        [key]: { ...state[key], [field]: nextVal },
      };
    },
    setExact(state, side, field, value) {
      const key = side === 'B' ? 'b' : 'a';
      const maxVal = field === 'points' ? 999 : 99;
      return {
        ...state,
        [key]: { ...state[key], [field]: clamp(Number(value) || 0, 0, maxVal) },
      };
    },
    swapSides(state) {
      return {
        ...state,
        fighterAName: state.fighterBName,
        fighterBName: state.fighterAName,
        a: { ...state.b },
        b: { ...state.a },
      };
    },
    computeWinner(state) {
      if (state.a.points !== state.b.points) return state.a.points > state.b.points ? 'FIGHTER_A' : 'FIGHTER_B';
      if (state.a.advantages !== state.b.advantages) return state.a.advantages > state.b.advantages ? 'FIGHTER_A' : 'FIGHTER_B';
      if (state.a.penalties !== state.b.penalties) return state.a.penalties < state.b.penalties ? 'FIGHTER_A' : 'FIGHTER_B';
      return 'DRAW';
    },
    defaultMethod(state) {
      if (state.a.points !== state.b.points) return 'Pontos';
      if (state.a.advantages !== state.b.advantages) return 'Vantagens';
      if (state.a.penalties !== state.b.penalties) return 'Punições';
      return 'Empate';
    },
    reset(keepNames = false, prev = null) {
      if (keepNames && prev) {
        return {
          ...this.create(),
          fighterAName: prev.fighterAName,
          fighterBName: prev.fighterBName,
          category: prev.category,
        };
      }
      return this.create();
    },
  };

  root.FEU_TIMER.CustomBuilder = CustomBuilder;
  root.FEU_TIMER.TimerEngine = TimerEngine;
  root.FEU_TIMER.Scoreboard = Scoreboard;
})();
