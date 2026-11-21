package pt.feubjj.timer.ui.viewmodel

/**
 * Top-level Android TV screens:
 * - [CLOCK]: Idle Academy Wall Clock
 * - [QUICK_TIMERS]: 3x5 Quick Countdown Grid + Stopwatch launcher
 * - [ROUNDS]: 4 Saveable Academy Presets + Template Library + Custom Round Builder
 * - [SCOREBOARD]: Full IBJJF Competition Scoreboard (Points / Advantages / Penalties + Match Timer)
 * - [SETTINGS]: Configurable Prep Countdown, Warning Threshold, Sound Profile, 12h/24h Clock & Language
 * - [ACTIVE_TIMER]: Colossal full-screen sparring countdown / stopwatch with compact live controls
 */
enum class TvScreen {
    CLOCK,
    QUICK_TIMERS,
    ROUNDS,
    SCOREBOARD,
    SETTINGS,
    ACTIVE_TIMER
}
