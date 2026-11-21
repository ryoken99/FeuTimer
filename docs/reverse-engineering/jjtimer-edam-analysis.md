# Reverse-Engineering Analysis: `br.com.isul.jiujitsutimertv.edambjj` (JJTimer-Edam)

## 1. Artifact Identity & Packaging

| Artifact | SHA-256 / Size | Role | REA Evidence ID |
|---|---|---|---|
| `br.com.isul.jiujitsutimertv.edambjj.apk` | `6fe009644ed88613376e832628ad33ccb7c2eddb5d3891368b87fe50e9d0d512` | Base APK (DEX, layouts, default PT strings, raw MP3 audio) | `ev_f49479ca4e0818a4ad99145733ec478e00750429737f3b010db389d45135cb04` |
| `config.en.apk` | Split locale APK | English (`en`) string table | Decoded via `resources.arsc` |
| `config.es.apk` | Split locale APK | Spanish (`es`) string table | Decoded via `resources.arsc` |
| `config.pt.apk` | Split locale APK | Portuguese (`pt`) Material/AndroidX strings | Decoded via `resources.arsc` |
| `config.xhdpi.apk` | Split density APK | `xhdpi` raster drawables (`banner.png`, `icon.png`, `logo_principal.png`, `ic_round.png`) | Inspected via ZIP central directory |

### Manifest & Platform Requirements (`AndroidManifest.xml`)
- **Package:** `br.com.isul.jiujitsutimertv.edambjj` (`versionName="1.0.0"`, `versionCode="1"`)
- **SDK Levels:** `minSdkVersion="23"`, `targetSdkVersion="34"`, `compileSdkVersion="34"`
- **Android TV Declarations:**
  - `<uses-feature android:name="android.software.leanback" android:required="true"/>`
  - `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`
  - `MainActivity` (`screenOrientation="landscape"`, `FLAG_KEEP_SCREEN_ON`) exports both `android.intent.category.LAUNCHER` and `android.intent.category.LEANBACK_LAUNCHER`, with `<application android:banner="...">`.

---

## 2. Complete Feature Inventory & State Machine

### 2.1 Persistent TV Chrome (`activity_main.xml` + `MainActivity`)
Evidence: `ev_179a2b8677799cdd22ce07f603d149f49ff4a0765cf6e65d03e2a9dc749715b3`, `ev_782ac9a8a9b9fdb3d933636a3d517c13547ba6790a8087905d2855b43cc1bdef`

1. **Top Bar:**
   - **Top-Left (`layoutLastTime` / `tvLastTime`):** Displays the active/last timer configuration (e.g., `"05:00"` for a single timer, or `"5 x 5m x 1m"` for multi-round sparring). Shown while `TimerFragment` is active.
   - **Top-Center (`ivLogoPrincipal`):** Main academy logo.
   - **Top-Right (`TextClock`):** Always-visible real-time digital wall clock (`kk:mm:ss` in 24h / `h:mm:ss a` in 12h) so athletes on the mat always know the current time even mid-sparring.
2. **Center Content (`FrameLayout#content`):** Hosts `ClockFragment`, `ControlFragment`, `RoundFragment`, or `TimerFragment`.
3. **Bottom Bar:**
   - **Bottom-Left Action Strip:**
     - On `ClockFragment`: `ibControl` (open Quick Single Timers — initial D-pad focus), `ibRound` (open Multi-Round Sparring & Custom Builder), `ibSound10` (toggle 10s clapper), `ibSound50` (toggle 50s clapper), `ibSoundInterval` (toggle rest breathing cue).
     - On `TimerFragment`: `ibPause` / `ibPlay` (auto-focused on state change so TV remote `OK`/`SELECT` toggles pause/play effortlessly), `ibBack` (return to Wall Clock), plus the 3 sound toggles.
   - **Bottom-Center (`ivLogoSecundario`):** Secondary academy emblem.
   - **Bottom-Right (`tvVersion`):** App version (`v1.0.0`).
4. **TV Remote `BACK` Behavior (`MainActivity.onBackPressed`, `ev_e61be9f769a723e7193dbe59996f47d88b10545819b12cea0f998c462dceb9e8`):**
   - If on `ControlFragment`, `RoundFragment`, or `TimerFragment` (`ibControl.visibility == GONE`), pressing `BACK` returns to `ClockFragment` instead of exiting the app. Pressing `BACK` on `ClockFragment` exits normally.

---

### 2.2 Screen 1: Idle Academy Wall Clock (`ClockFragment`)
Evidence: `ev_e09eef4d947320f875f82ed9fd47e546190cdd5ad66eefbe74e65b89eab8cda8`, `ev_43f0ccf93fef25311fdb21235a992085872f76a66ba41de5b59fc453a66ba467`
- Full-bleed centered digital `TextClock` (`kk:mm:ss` in 24h) turning the gym TV into a high-contrast academy wall clock between classes.
- Loads persisted `Preference` (`beep10s`, `beep50s`, `beepInterval`) from `SharedPreferences` (`<pkg>.prefs`) and syncs toggle button states.

---

### 2.3 Screen 2: Quick Single-Timer Grid (`ControlFragment`)
Evidence: `ev_12e236e4b6f165ef4ad3768a2b5af64e459163e3b7ab78f24503174abca3efb1`, `ev_8ffbd2958678e8690e81f0c10d1dfc5e9318b23f0f329a877efcaba797ba48cd`
- 3×5 grid of **15 one-click single countdown timers** with wraparound D-pad focus chaining:
  - Row 1: `00:30`, `01:00`, `02:00`, `03:00`, `04:00`
  - Row 2: `05:00`, `06:00`, `07:00`, `08:00`, `09:00`
  - Row 3: `10:00`, `12:00`, `15:00`, `20:00`, `30:00`
- Selecting any duration creates `new Round(seconds)` (`repeat = 0`) and immediately starts `TimerFragment`.

---

### 2.4 Screen 3: Multi-Round Presets & Custom Round Builder (`RoundFragment` + `RoundViewModel`)
Evidence: `ev_e6e085b681904c8c06f73314fba55d0259a79d5c4c9b866982801da55bd829c8`, `ev_efd135e0c85b3a6ed86f38ce316934ab051adfa769bcda2a451562078da7526f`, `ev_94faba881eae88e43afdd59204519411fd4f4b7feb1e16629c279e05b36242e6`

- **Top Row — 4 BJJ Sparring Presets** (`R.array.presets`):
  1. **5 × 5 min / 1 min rest** (`repeat: 5, time: 300, interval: 60` — Total: `29m`)
  2. **6 × 6 min / 30 sec rest** (`repeat: 6, time: 360, interval: 30` — Total: `38m 30s`)
  3. **10 × 2 min / 3 min rest** (`repeat: 10, time: 120, interval: 180` — Total: `47m`)
  4. **3 × 3 min / 30 sec rest** (`repeat: 3, time: 180, interval: 30` — Total: `10m`)
- **Bottom Row — Custom Round Builder** (persisted automatically on start):
  - **Repetitions (`customRep`):** `1` to `99` (`±1` step, initial default `2`).
  - **Round Duration (`customTimeSeconds`):** `00:10` to `60:00` (`±10s` step, initial default `01:00`).
  - **Rest Duration (`customIntervalSeconds`):** `00:00` to `60:00` (`±10s` step, initial default `00:30`).
  - **Total Session Duration (`getTotalTimeSeconds`):** Computed live via `repeat * timeSeconds + (repeat - 1) * intervalSeconds`, paired with a Start (`Play`) button.

---

### 2.5 Screen 4: Active Timer Engine & Acoustic Cue System (`TimerFragment`)
Evidence: `ev_39dd740e6efcf621c2992f4e8a1d5a9e3e6fb1390e5c056fec8a8dd366245748`, `ev_343485f367d13f8fbc225c7e74a5661d9e79cc93261f50908ccb4ad044139433`, `ev_c07924cfb53e96e011540382e2b9f5773a5b0c6d68b5cef8143282b8e9af1e7a`

1. **4-Second Pre-Round Lead-In (`beep_pega.mp3` — 4.05s):**
   - Waveform analysis reveals three 200ms ~950 Hz countdown tones at `t=0.0s`, `t=1.0s`, `t=2.0s` (`3... 2... 1...`), followed at `t=3.0s..3.5s` by a loud sustained ~990 Hz fight-start horn!
   - During these 4 seconds, the timer blinks (`500ms` alpha pulse) at full round time before `loopTimer()` begins decrementing.
2. **Active 1-Second Countdown Loop:**
   - Displays `MM:SS` (`%02d:%02d`) and uppercase phase header (`TEMPO` for single timer, `TEMPO X/Y` for multi-round fight, `INTERVALO` for rest).
   - **At `00:50` remaining (`50,000 ms`):** Plays `wood_clap.mp3` (three crisp wooden clapper strikes at `0.10s`, `0.60s`, `1.10s`) if `beep50s` is enabled.
   - **At `00:10` remaining (`10,000 ms`):** Starts blinking animation for the final 10 seconds AND plays `wood_clap.mp3` if `beep10s` is enabled.
   - **During Rest Interval (`isInterval == true`):** Every 3 seconds (`currentTime % 3 == 0`), plays `beep_respira.mp3` (soft 200ms ~880 Hz tone) if `beepInterval` is enabled, guiding recovery breathing.
3. **Phase Transitions (`nextRound` / `finishTimer`):**
   - Fight -> Rest (when `currentRound < repeat` and `intervalSeconds > 0`): plays `beep_fim.mp3` (four ~4000 Hz double-chirp alarm bursts over 4s), switches to Rest Interval.
   - Rest -> Next Fight: increments `currentRound`, plays `beep_pega.mp3` (3-2-1-HORN), starts next round.
   - Final Round Complete: stops blinking, plays `beep_fim.mp3`, and returns automatically to `ClockFragment`.
4. **Vendor Commercial Gate (`ActivationFragment` — Excluded by Design in `FeuTimer`):**
   - Original APK locked multi-round mode and single timers `>= 02:00` behind an HMAC-based 6-digit QR activation code (`https://timerapp.tv.br`). `FeuTimer` removes all paywall/telemetry (`FirebaseCrashlytics`, `Google Play Measurement`, `pairip` license checks, `INTERNET` permissions) so it runs 100% offline and unrestricted on FEU BJJ's TVs.

---

## 3. Defects & Limitations Found in Original APK (Fixed in `FeuTimer`)

1. **Zero-Interval Edge Case Bug in Original `TimerFragment`:** In `nextRound()`, when `intervalSeconds == 0` on a custom multi-round workout (`repeat > 1`, `interval = 0s`), the original app still flipped `isInterval = true` with `currentTime = 0`, burning an empty 1-second tick + 4-second `beep_fim` before starting the next round. `FeuTimer` handles `intervalSeconds == 0` cleanly while preserving standard rest intervals.
2. **Timer Drift (`postDelayed(1000L)`):** Original app subtracted `1000ms` per `Handler.postDelayed(1000L)` callback, accumulating UI thread scheduling drift over 30-minute sparring sessions. `FeuTimer` uses monotonic clock anchoring (`elapsedRealtime`) with clean deterministic `ClockSource` injection for zero drift.
3. **Per-Play `SoundPool` Reload Latency:** Original `playSound()` instantiated/loaded sounds on-demand via `setOnLoadCompleteListener` on every cue. `FeuTimer` preloads synthesized studio-quality WAV cues at startup for zero-latency playback.
4. **Sound Toggles Defaulted to `false` on First Install:** `new Preference()` left `beep10s`, `beep50s`, and `beepInterval` `false` until manually toggled. `FeuTimer` defaults `beep10s` and `beep50s` to `true` (and `beepInterval` configurable right from the TV bar) with clear visual pills so instructors immediately see active alerts.
