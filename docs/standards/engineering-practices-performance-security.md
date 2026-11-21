# Engineering Practices, Performance & Security Notes

## 1. Best Practices & Code Comments
- **Purpose-Focused KDoc:** Comments explain *why* each domain rule exists (e.g., why `LEAD_IN` runs 4 seconds for the `3-2-1-HORN` sequence, or why `tnum` font features prevent digit layout shift), never restating obvious syntax.
- **DRY & Modularity:** All duration parsing/formatting lives in `TimeFormatter`; all focus styling and D-pad Enter/Center key handling lives in `TvFocusableSurface`; all stitched black-belt rendering lives in `DigitalBlackBelt`.
- **European Portuguese (`pt-PT`):** Default `res/values/strings.xml` is hand-written in authentic `pt-PT` ("tu" form: *Escolhe*, *Prepara-te*, *Cronómetro*, *comando*), with `tools:locale="pt-rPT"` declared for Android Lint.

## 2. Performance Optimizations for Android TV
- **Preloaded `SoundPool` WAV Cues:** Unlike `JJTimer-Edam` (which reloaded raw MP3 streams per `playSound` call via `setOnLoadCompleteListener`), `TimerSoundPlayer` synthesizes and loads all 5 PCM WAV cues once at startup, eliminating HDMI/speaker trigger lag.
- **Zero-Layout-Shift GPU Focus & Tabular Digits:**
  - `TvFocusableSurface` applies focus scale (`1.035f`) inside `Modifier.graphicsLayer`, avoiding re-measurement of sibling TV grid cards during D-pad navigation.
  - All clocks and timers enable OpenType `tnum` (`tabular numerals`) so `01:11` and `08:48` occupy identical pixel widths.

## 3. Security & Privacy Best Practices
- **Zero Network Surface:** `AndroidManifest.xml` requests **no `INTERNET` or `ACCESS_NETWORK_STATE` permissions**.
- **No Unexported Dynamic Receivers or Cleartext Endpoints:** Eliminates telemetry SDKs and external activation endpoints (`timerapp.tv.br`).
- **Scoped App-Private Storage:** Preferences use `Context.MODE_PRIVATE` `SharedPreferences`, and synthesized WAV cache files reside inside `context.cacheDir`.

## 4. Code Example — Pure Deterministic Tick Step
```kotlin
val prefs = SoundPreferences(beep50s = true, beep10s = true, beepInterval = true)
val initial = TimerEngine.start(RoundConfig(repeat = 5, workSeconds = 300, restSeconds = 60), prefs)
val next = TimerEngine.tickOneSecond(initial.snapshot, prefs)
// next.snapshot holds immutable UI state; next.cue holds exact SoundCue? to play
```
