# Dependencies & Their Purpose

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| `com.android.application` | `8.7.3` | Gradle Plugin | Compiles resources, DEX bytecode, Android Lint, and universal signed Android TV APKs. |
| `org.jetbrains.kotlin.android` / `plugin.compose` | `2.0.21` | Gradle Plugin | Kotlin 2.0 compiler + Jetpack Compose compiler plugin. |
| `androidx.compose:compose-bom` | `2024.10.01` | Implementation | Aligns `ui`, `foundation`, `material3`, and `material-icons-extended` versions deterministically. |
| `androidx.tv:tv-material` | `1.0.0` | Implementation | Android TV 10-foot Material design primitives and Leanback compatibility. |
| `androidx.activity:activity-compose` | `1.9.3` | Implementation | Hosts Compose inside `ComponentActivity` with `OnBackPressedDispatcher` integration. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` / `runtime-compose` | `2.8.7` | Implementation | Retains `FeuTimerViewModel` and active sparring timers across configuration events. |
| `androidx.core:core-ktx` | `1.15.0` | Implementation | Core Android compatibility extensions back to API 23. |
| `junit:junit` | `4.13.2` | Test | JVM unit testing framework for `TimerEngine`, `TimeFormatter`, `RoundConfig`, `PresetCatalog`, and `WavSynthesizer`. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.9.0` | Test | Virtual-time scheduler (`StandardTestDispatcher`, `advanceTimeBy`) for deterministic multi-minute timer tests in milliseconds. |

*Intentionally Omitted from Original APK:* `firebase-crashlytics`, `play-services-measurement`, `gson`, `zxing` QR generator, and `com.pairip.licensecheck` — keeping `FeuTimer` 100% offline, fast, and dependency-minimal.
