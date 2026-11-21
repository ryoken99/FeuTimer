# Code Structure, Organization & Component Interactions

## Layered Unidirectional Data Flow (UDF)

```text
[Android TV Remote Keys / D-Pad]
        │
        ▼
  MainActivity.kt ──► FeuTimerViewModel ──► FeuTimerSessionController
                                                   │
                     ┌─────────────────────────────┼─────────────────────────────┐
                     ▼                             ▼                             ▼
              TimerEngine.kt               TimerStorage.kt               SoundPlayerPort.kt
          (Pure State Machine)       (SharedPreferences Storage)       (Preloaded SoundPool +
                     │                                                  WavSynthesizer PCM)
                     ▼
        StateFlow<FeuTimerUiState>
                     │
                     ▼
           FeuTimerAppRoot.kt
   ┌─────────────────┼──────────────────┬───────────────────┐
   ▼                 ▼                  ▼                   ▼
ClockScreen   QuickTimersScreen    RoundsScreen     ActiveTimerScreen
```

## Module Interaction Rules
1. **`domain/` has zero Android UI dependencies:** `TimerEngine`, `TimeFormatter`, `RoundConfig`, and `PresetCatalog` are pure Kotlin, making every rule testable on the JVM in `< 1s`.
2. **`FeuTimerSessionController` coordinates side-effects through ports:** Persistence goes through `TimerStorage` and audio goes through `SoundPlayerPort`, decoupling business logic from `Context` and `SoundPool`.
3. **Compose UI (`ui/`) is stateless and state-driven:** Screens read `FeuTimerUiState` and emit user intents (`startQuickTimer`, `startPresetRound`, `adjustCustomWork`, `togglePlayPause`) upward.
