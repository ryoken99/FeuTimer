# Functions & Modules Reference

Concise index of all production functions, classes, file paths, and responsibilities in `FeuTimer` (`pt.feubjj.timer`) and its interactive macOS browser Android TV + Mobile Referee simulator (`web/`).

## 1. Domain, Vocal Timer Engine & JSON (`app/src/main/java/pt/feubjj/timer/domain/`)

| Symbol | File Path | Purpose & Usage |
|---|---|---|
| `TimeFormatter` | `domain/engine/TimeFormatter.kt` | Formats `MM:SS`, compact durations (`"29m"`, `"38m 30s"`), and `(value, TimeUnitKind)` columns. |
| `RoundConfig` / `CustomRoundBuilderState` | `domain/model/RoundConfig.kt` | Models single, finite multi-round, endless open-mat (`repeat = -1`), and count-up stopwatch sessions; enforces smart `5s`/`10s` steps and `±1m` jumps. |
| `MatchScoreState` / `MatchWinnerSide` | `domain/model/MatchScoreState.kt` | Live IBJJF scoreboard model supporting live custom athlete names (`fighterAName`, `fighterBName`), division (`categoryLabel`), additive & direct score editing (`0..999` pts, `0..99` adv/pen), `swapSides()`, and official IBJJF tie-break winner calculation. |
| `MatchResultRecord` | `domain/model/MatchResultRecord.kt` | Immutable saved fight record (`timestamp`, `category`, athletes, scores, `winnerSide`, `method`) serialized via `SimpleJson`. |
| `SimpleJson` | `domain/util/SimpleJson.kt` | Zero-dependency RFC-8259 JSON parser/encoder working identically in Android TV and pure JVM unit tests. |
| `SoundPreferences` | `domain/model/SoundPreferences.kt` | Persisted settings for `voiceEnabled`, independent mid-round warnings (`warnHalfway`, `warn2m`, `warn1m`, `beep50s`, `warn30s`), final 10s vocal countdown (`beep10s`), `prepSeconds` (`0/3/5/10s`), `soundPack`, and `use24HourClock`. |
| `TimerEngine` | `domain/engine/TimerEngine.kt` | Deterministic state reducer emitting `COUNT_10..COUNT_1` on all pre-round pips and final 10s round countdowns, `WARN_HALFWAY` / `WARN_2M` / `WARN_1M` / `WARN_50S` / `WARN_30S`, `FIGHT_HORN`, `ROUND_END_TO_REST`, `ROUND_END_ALARM`, and peaceful silent rest breaks. |

## 2. PT-PT Vocal + Tatami Audio Synthesis (`app/src/main/java/pt/feubjj/timer/audio/`)

| Symbol | File Path | Purpose & Usage |
|---|---|---|
| `SoundCue` / `SoundPack` | `audio/SoundCue.kt`, `audio/SoundPack.kt` | Vocabulary of 20 vocal + SFX cues across 3 acoustic profiles (`CLASSIC_HORN`, `BELL_GONG`, `WHISTLE`). |
| `WavSynthesizer` | `audio/WavSynthesizer.kt` | Decodes `res/raw/voice_*.wav` (`44.1 kHz 16-bit mono`) and composites them with tatami horns/bells/whistles/clappers using soft-knee `tanh` limiting (`generateVocalCompositeWav`), plus pure non-verbal fallbacks (`generateCueWav`). |
| `TimerSoundPlayer` | `audio/TimerSoundPlayer.kt` | Preloads all vocal composites and non-verbal SFX into Android `SoundPool` for zero-latency TV playback. |

## 3. Embedded Wi-Fi Mobile Referee Server & QR Encoder (`app/src/main/java/pt/feubjj/timer/network/`)

| Symbol | File Path | Purpose & Usage |
|---|---|---|
| `ScoreboardLanServer` | `network/ScoreboardLanServer.kt` | Embedded `TCP_NODELAY` HTTP/1.1 + RFC 6455 WebSocket server on `:7777` serving `assets/referee/index.html` and streaming live bidirectional scoreboard/timer/results updates (`< 5 ms`). |
| `LanAddressResolver` | `network/LanAddressResolver.kt` | Detects the active local Wi-Fi (`wlan0`) or Ethernet (`eth0`) IPv4 address for display on the TV Scoreboard. |
| `QrMatrixEncoder` | `network/QrMatrixEncoder.kt` | Pure Kotlin ISO/IEC 18004 Byte-mode QR Code matrix encoder (Versions 1–4, Reed-Solomon ECC) rendered directly on Compose `Canvas`. |
| `assets/referee/index.html` | `app/src/main/assets/referee/index.html` | Single-screen (`100dvh` zero-scroll) Portrait Mobile Referee Console for live athlete names, `+2/+3/+4/+1/−1/−2` & direct score/clock editing, match saving, native sharing/CSV export, and individual or bulk deletion. |

## 4. Android TV Compose UI & Viewport Scaler (`app/src/main/java/pt/feubjj/timer/ui/`)

| Symbol | File Path | Purpose & Usage |
|---|---|---|
| `TvViewportScaler` | `ui/theme/TvViewportScaler.kt` | Normalizes any physical window resolution (`720p`, `1080p @ 160dpi/320dpi`, `4K`, ultrawide) onto the canonical `960 × 540` 10-foot reference grid so UI never shrinks or clips across OEM TVs/boxes. |
| `FeuTimerSessionController` / `FeuTimerViewModel` | `ui/viewmodel/*.kt` | Reactive state controller & lifecycle host driving all 6 TV screens, vocal audio cues, match results persistence, and `ScoreboardLanServer`. |
| `TopChromeBar`, `TatamiBackdrop`, `BottomControlBar`, `TvFocusableSurface` | `ui/components/*.kt` | Heroic `92.dp` FEU BJJ crest + display wordmark + stitched Black-Belt & Red-Ponteira rule, deep `#070606` off-axis-safe backdrop, and overscan-safe bottom dock with QR + Wi-Fi Referee URL banner. |
| `ClockScreen`, `QuickTimersScreen`, `RoundsScreen`, `ActiveTimerScreen`, `ScoreboardScreen`, `SettingsScreen` | `ui/screens/*.kt` | All 6 high-contrast, across-the-mat legible TV screens (`44.sp` framed Round pill, `224.sp` timer numerals, live custom athlete names on Scoreboard, 4-row Vocal & Alert Settings). |

## 5. Interactive macOS Browser Simulator & Wi-Fi Referee Hub (`web/`)

| Symbol / File | File Path | Purpose & Usage |
|---|---|---|
| `web/serve.cjs` | `web/serve.cjs` | Local HTTP + RFC 6455 WebSocket server hosting the `960×540` scale-locked TV Simulator (`:8788`) and the live Mobile Portrait Referee Console (`:7777` and `:8788/r`). |
| `web/js/engine.js`, `audio.js`, `qr.js`, `i18n.js`, `presets.js`, `dpad.js`, `app.js` | `web/js/*.js` | 1:1 browser twin of the Android TV app with `ResizeObserver` `960×540` scale lock (0px cut-off at any zoom), PT-PT vocal playback, and live WebSocket referee sync. |

## 6. Focus Navigation Guarantees (never-trapped D-pad)

| Symbol / File | File Path | Purpose & Usage |
|---|---|---|
| `DpadGrid.findNext` | `web/js/dpad.js` | Spatial row-clustering engine; every control reachable from every other, lone buttons (e.g. `◀` Back) escape on all 4 arrows. Covered by BFS reachability test in `web/tests/timer-web.test.cjs`; live probe: `web/tests/live-dpad-probe.js`. |
| Root `onKeyEvent` focus fallback | `app/.../ui/FeuTimerAppRoot.kt` | If Compose finds no geometric candidate for an arrow key, falls back to sequential `FocusDirection.Next/Previous` so Android TV focus can never get stuck. |
| Scoreboard count-in + `MATCH_END` | `FeuTimerSessionController.kt`, `SoundCue.kt` | First Play on `Placar` runs the vocal prep countdown, final 10s vocal countdown runs, match ends with `"Tempo!"`; durations `2:00..10:00`, live `±10s`. |

## 7. Save-to-Preset Flow & Side-Scrolling Templates

| Symbol / File | File Path | Purpose & Usage |
|---|---|---|
| Slot preview (pulse + before→after) | `RoundsScreen.kt` (`PresetRoundCard`, `previewSlot`), `web/js/app.js` (`updateSavePreview`) | Focusing/hovering a `Guardar` number grows and pulses the target preset card and shows each changed value struck-through above its new value. |
| `SaveSlotDialog` / `openSaveDialog` | `RoundsScreen.kt`, `web/js/app.js` | OK/Cancel confirmation listing old → new values; Back/Escape cancels; OK saves, then a green toast + card flash confirm completion. |
| `TemplateStrip` / `#templates-strip` | `RoundsScreen.kt`, `web/js/app.js` + CSS | Templates sit at the bottom as a side-scrolling strip (full text, no truncation), edges fade where more options hide, focused pill is centred. |
| `TvFocusableSurface(pulse, onFocusChange)` | `ui/components/TvFocusableSurface.kt` | Shape-following halo, optional attention pulse, and focus callback used by the preview and strip. |
| Live probes | `web/tests/live-*.js` | Browser-console probes for reachability, Rondas/Tempos navigation and the save flow (run via `fetch(...).then(eval)`). |

## Web audio hardening (v1.3.2)
- `web/js/audio.js`: voice clips are decoded into WebAudio buffers (`loadVoiceBuffers`) and scheduled on the audio clock, so rapid cues (3, 2, 1, Combate!) never get dropped by per-element autoplay rules. `Audio.debug()` reports `{state, voiceBuffers, htmlClips}` (expect 21). When the context is locked, a `feu-audio-locked` event shows a gold toast asking for one click on the page.
- Verified: scoreboard count-in plays COUNT_3, COUNT_2, COUNT_1, FIGHT_HORN; then WARN_HALFWAY, WARN_50S (WARN_30S is opt-in), COUNT_10..1, MATCH_END. Android test asserts the same count-in order.

## Duplicate / layered count-in "3" (fix)
- Cause: `serve.cjs` broadcast phone commands to every open TV page. A stale/background tab received `play_pause`, said "3" immediately, then its `requestAnimationFrame` ticker froze (browsers pause rAF in hidden tabs), so only the "3" layered over the visible tab's full 3-2-1-Combate.
- Fix: `dispatchRefereeCommand` targets only the newest TV socket; `web/js/app.js` `scheduleFrame` races rAF against a 200 ms timeout so a background tab can never stall mid count-in. The Android app has a single TV instance and was verified to play each cue once.

## Phone ↔ TV consistency (v1.3.4)
- Hub (`web/serve.cjs`): every TV page receives every command (state never diverges). One page is the *leader* (last touched via `tv_active`, ties newest): only it speaks (`audio_leader`) and only its `tv_state` is forwarded to phones, so a stale tab cannot overwrite names/points.
- Phone (`app/src/main/assets/referee/index.html`): names/category are kept "dirty" (`typeText`) until the TV echoes the same value; a 1.2 s heartbeat and tab-visibility reconnect re-send anything unconfirmed, so a silently dropped WebSocket can't leave the phone showing text the TV never got.
