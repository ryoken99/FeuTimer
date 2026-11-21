# FeuTimer (Android TV & Universal Android) — Architecture & Design Specification

**Date:** 2026-11-13  
**Target Platform:** Universal Android TV + Standard Android OS (`minSdk = 23`, `targetSdk = 35`, `LEANBACK_LAUNCHER` + `LAUNCHER`, D-pad, HDMI-CEC, Air-Mouse, Touch & Gamepad compatible)  
**Brand Edition:** **FEU BJJ Portugal · Pinhal Novo** (Prof. Carlos Prates)

---

## 1. Design Goals & Brand Identity

`FeuTimer` combines every capability of `JJTimer-Edam` and `BJJ OUT` with full tatami flexibility and a modern **FEU BJJ Black-Belt Dark Mode** 10-foot interface:

### 1.1 Visual Hierarchy & `BJJ OUT` Legibility Principles
- **Colossal Distraction-Free Active Timer:** Zero progress bars or clutter in the center of the screen — 100% of central vertical real estate is dedicated to the phase headline (`RONDA 1 / 5`, `RONDA 3 · LIVRE`, `DESCANSO · PRÓXIMA 2/5`, `CRONÓMETRO PROGRESSIVO`) and giant tabular `MM:SS` numerals (`204sp` on TV).
- **Inverted High-Contrast Focus (`highContrastFocus`):** Focused cells in the `3×5` Quick Timer grid and primary launch buttons invert cleanly to solid white (`#F6F2EB`) with bold dark text (`#0B0A0A`) and a crisp crimson halo so coaches know what is selected from across the mat.
- **Compact Circular Media Controls (`BottomControlBar`):** Kept tucked in the bottom-left corner (`◀` Back, prominent circular white/red `⏸/▶` Play/Pause, `↺` Restart Round, `−10s`, `+10s`, `⏭` Skip Phase) away from the main timer numerals.
- **Localization (`pt-PT` Default + `en` + `es`):** Hand-written **European Portuguese (`pt-PT`)** ("tu" form: `Escolhe o Tempo`, `Prepara-te`, `Rondas`, `Guardar`, `Definições do Tatami`, `Placar IBJJF`), switchable live in-app between `PT`, `EN`, and `ES`.

---

## 2. Complete Feature Matrix (`JJTimer-Edam` + `BJJ OUT` + FEU Tatami Pro)

| Subsystem | `JJTimer-Edam` & `BJJ OUT` | `FeuTimer` (FEU BJJ Universal TV Edition) |
|---|---|---|
| **Idle Wall Clock** | Giant `HH:MM:SS` clock (`24h` or `12h AM/PM`) | Giant tabular `HH:MM:SS` wall clock supporting **both `24h` and `12h AM/PM`**, full date, 1-click **"Repetir: ..."** chip, quick alert pills, and `⚙` Settings |
| **Quick Single Timers + Stopwatch** | 15 buttons (`00:30`..`30:00`) in `3×5` grid with solid-white focus | Identical 15 durations in `3×5` wraparound grid with solid-white inverted focus **plus** 1-click **Progressive Stopwatch (`00:00 ↑`)** |
| **Saveable Academy Presets + Templates** | 4 fixed presets (`5×5m/1m`.. or `4×10m/2m`, `10×5m/90s`, `10×3m/40s`, `4×1m/1m`) | **4 Editable & Saveable Preset Slots** (`Guardar: [1][2][3][4]`) with color-coded `RONDAS / LUTA / DESCANSO` labels + **6-template Quick Strip** (`Rola Livre ∞`, `Resistência 10×5m/90s`, `Faixa Preta 4×10m/2m`, `Ritmo Alto 10×3m/40s`, `Shark Tank 4×1m/1m`, `Tabata 8×20s/10s`) |
| **Custom Round Builder + Endless Mode** | `+` on Left / `−` on Right, framed total box, white circular `▶` Play | Exact **`+` Left / `−` Right** layout, **`∞` Open-Mat Endless Rounds toggle**, smart `5s`/`10s` steps + fast `+1m / −1m` tags, framed total box, and white circular `▶` Play button |
| **Live Mid-Round Controls** | `◀` Back + circular `⏸` Pause | `◀` Back, prominent circular `⏸/▶` Play/Pause, **`↺` Restart Round**, **`−10s` / `+10s` live time adjustment**, and **`⏭` Skip Phase** (also mapped to TV remote media/channel keys) |
| **IBJJF Competition Scoreboard** | Not included | Dedicated **Placar IBJJF** screen: Athlete 1 (White) vs Athlete 2 (Red), `+2 / +3 / +4 / −1` points, Advantages (`VANT`), Penalties (`PUN`), and IBJJF belt timers (`5m / 6m / 7m / 8m / 10m`) |
| **Tatami Settings & Sound Profiles** | Fixed 4s lead-in, fixed 50s warning, single horn | Configurable **Prep Countdown (`0s / 3s / 5s / 10s`)**, **Warning Clapper (`Off / 60s / 50s / 30s`)**, **3 Synthesized Sound Profiles (`Buzina / Gongo / Apito`)** + Test button, `12h/24h` clock, `PT/EN/ES` switcher, and Preset Reset |
| **Privacy & Reliability** | `INTERNET`, Firebase Crashlytics, QR Paywall | **Zero network permissions**, zero telemetry, zero paywall, monotonic clock engine, 100% offline |

---

## 3. Modular Code Architecture (`pt.feubjj.timer`)

```text
app/src/main/java/pt/feubjj/timer/
├── FeuTimerApp.kt                    # Application entry point
├── MainActivity.kt                   # Universal TV/Android host, KEEP_SCREEN_ON, Locale wrapper, remote key routing
├── domain/
│   ├── model/
│   │   ├── RoundConfig.kt            # Single, multi-round, endless (∞), stopwatch, and CustomRoundBuilderState
│   │   ├── MatchScoreState.kt        # Official IBJJF competition scoreboard state (Points, Advantages, Penalties)
│   │   ├── SoundPreferences.kt       # Configurable prep, warning clapper, sound pack, 12h/24h clock, language
│   │   ├── TimerPhase.kt             # IDLE, LEAD_IN, RUNNING_WORK, RUNNING_REST, RUNNING_STOPWATCH, PAUSED, FINISHED
│   │   └── TimerSnapshot.kt          # Deterministic UI & audio state emitted by engine
│   ├── presets/
│   │   └── PresetCatalog.kt          # 15 quick timers + 4 default presets + 6 quick BJJ templates
│   └── engine/
│       ├── TimerEngine.kt            # Pure Kotlin state reducer (tick, pause, resume, ±10s, skip, restart)
│       └── TimeFormatter.kt          # MM:SS, compact duration, and unit formatters
├── data/
│   └── TimerStorage.kt               # SharedPreferences persistence for preferences, 4 saveable slots, custom & last session
├── audio/
│   ├── SoundCue.kt                   # 5 martial arts cues
│   ├── SoundPack.kt                  # HORN, BELL (Gongo), WHISTLE (Apito)
│   ├── WavSynthesizer.kt             # Deterministic 44.1 kHz PCM WAV generator for all (SoundPack × SoundCue) pairs
│   └── TimerSoundPlayer.kt           # Preloaded zero-latency Android SoundPool controller
└── ui/
    ├── input/
    │   └── RemoteKeyClassifier.kt    # Universal TV remote, HDMI-CEC, Air-Mouse, media & gamepad key classifier
    ├── components/
    │   ├── TatamiBackdrop.kt         # FEU BJJ ambient glow + subtle watermark
    │   ├── TopChromeBar.kt           # Session summary badge, FEU mark, live 24h / 12h AM-PM wall clock
    │   ├── BottomControlBar.kt       # Home navigation bar + compact circular active-timer media controls
    │   └── TvFocusableSurface.kt     # Single-node D-pad + Air-Mouse tap surface with optional inverted white focus
    ├── screens/
    │   ├── ClockScreen.kt            # Giant academy wall clock
    │   ├── QuickTimersScreen.kt      # 3×5 high-contrast grid + Progressive Stopwatch launcher
    │   ├── RoundsScreen.kt           # 4 saveable slots + 6-template strip + Custom Builder (+ left / − right, ∞, ▶)
    │   ├── ActiveTimerScreen.kt      # Colossal 204sp distraction-free countdown / stopwatch
    │   ├── ScoreboardScreen.kt       # Official IBJJF competition scoreboard + match timer
    │   └── SettingsScreen.kt         # Prep time, warning clapper, sound pack, 12h/24h, PT/EN/ES, preset reset
    └── viewmodel/
        ├── FeuTimerSessionController.kt
        └── FeuTimerViewModel.kt
```
