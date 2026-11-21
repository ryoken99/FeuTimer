# Reconstruction Verification & Residual Unknowns Audit

## 1. REA Evidence & Verification Summary

- **Source Target APK:** `/Users/lx/Downloads/JJTimer-Edam/br.com.isul.jiujitsutimertv.edambjj.apk`
  - SHA-256: `6fe009644ed88613376e832628ad33ccb7c2eddb5d3891368b87fe50e9d0d512`
  - Canonical exported REA bundle: `docs/reverse-engineering/rea-evidence-bundle.json` (129 Evidence records, 3 unknown revisions).
- **Reconstructed Artifact:** `/Users/lx/Documents/FeuTimer/dist/FeuTimer-AndroidTV-v1.0.0.apk`
  - SHA-256: `becab0948c580a0b2dd3968af18995880e39ec0cc595960a06fe960709108ca7`
  - REA Package Inspection Evidence: `ev_84a0139dfd4bb789cd79cefc136cb7ab773f4062cbd3987061f845d9340b2cbe`
  - REA Class Inventory Evidence: `ev_1c16f863bfcf1473736aa8205f5a1c7a28580178c4125cb7190f7f202a7d0c57`

## 2. Feature-by-Feature Reconstruction Obligation Checklist

| Obligation ID | Source Evidence | Observed Behavior in `JJTimer-Edam` | `FeuTimer` Verification Proof | Status |
|---|---|---|---|---|
| `tv.leanback.manifest` | `ev_f49479ca...` | `android.software.leanback=true`, `touchscreen=false`, `LEANBACK_LAUNCHER`, landscape, `KEEP_SCREEN_ON`, TV banner | Verified via `ev_84a0139d...` + `aapt2 dump badging` + `apksigner verify` | **VERIFIED** |
| `screen.wall_clock` | `ev_43f0ccf9...` | Idle full-screen `HH:MM:SS` wall clock + top/bottom academy chrome | `ClockScreen.kt` + `FeuTimerSessionControllerTest` | **VERIFIED** |
| `screen.quick_timers` | `ev_12e236e4...`, `ev_8ffbd295...` | 15 quick durations (`00:30`..`30:00`) in 3×5 wraparound D-pad grid | `PresetCatalogTest` + `QuickTimersScreen.kt` | **VERIFIED** |
| `screen.presets_and_custom` | `ev_efd135e0...`, `ev_94faba88...` | 4 BJJ presets (`5×5m/1m`, `6×6m/30s`, `10×2m/3m`, `3×3m/30s`) + Custom (`1..99 ±1`, `10..3600 ±10s`, `0..3600 ±10s`, live total, persisted) | `PresetCatalogTest` + `RoundConfigTest` + `FeuTimerSessionControllerTest` | **VERIFIED** |
| `engine.timer_and_cues` | `ev_39dd740e...`, `ev_343485f3...` | 4s `3-2-1-HORN` lead-in, `50s` clapper, `10s` clapper + blink, `3s` rest breathing tone, `beep_fim` end alarm, auto-return to clock | `TimerEngineTest` + `WavSynthesizerTest` + `FeuTimerSessionControllerTest` | **VERIFIED** |
| `tv.remote_navigation` | `ev_e61be9f7...`, `ev_fd301b0b...` | Back returns from child screens to Clock; Play/Pause auto-focus & media keys | `MainActivity.kt` + `FeuTimerSessionControllerTest` | **VERIFIED** |
| `i18n.locales` | `resources.arsc` (`base`, `en`, `es`, `pt`) | Portuguese, English, and Spanish string tables | `values/strings.xml` (`pt-PT`), `values-en/strings.xml`, `values-es/strings.xml` | **VERIFIED** |
| `vendor.activation_gate` | `ev_71d64758...`, `ev_76333110...` | Commercial QR paywall (`timerapp.tv.br`) locking >=2m timers & rounds | Intentionally omitted (`out-of-scope` non-goal); 100% unlocked offline | **NON-GOAL (RESOLVED)** |

## 3. Residual Unknowns Audit (`/rea/audit_residual_unknowns`)

1. **`unk_75adaf6a1332337e2b1e2501af606eb05e35d91d2911f5f0e4dde05f095cecb9`** (`domain: protocol`, `severity: low`, `status: resolved`, `disposition: out-of-scope`)
   - *Question:* External server behavior of `https://timerapp.tv.br/{pt,en,es}/ativacao` when issuing 6-digit HMAC codes.
   - *Audit Disposition:* Verified `out-of-scope` via `verify_unknown_resolution` (`valid: true`, `truthVerified: false` as expected for non-goal disposition). `FeuTimer` is a private FEU BJJ app with zero licensing gates.
2. **`unk_d21317866d51c6aa11b7a399e45a193adbc7b0fa7b9ad1841b0457a4f8f4e2d6`** (`domain: runtime-audio`, `severity: low`, `status: open`)
   - *Question:* Physical Android TV HDMI receiver `SoundPool` wake-up / buffer latency when idle for long periods.
   - *Audit Disposition:* Remains bounded `open` until tested on the physical TV in Pinhal Novo (`required_authority: controlled-replay` on physical Android TV hardware). Mitigated architecturally by preloading PCM WAV cues into `SoundPool` at app startup rather than loading per tick.
