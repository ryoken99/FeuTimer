package pt.feubjj.timer.domain.model

/**
 * Active timer lifecycle phases, including count-up [RUNNING_STOPWATCH] mode.
 */
enum class TimerPhase {
    IDLE,
    LEAD_IN,
    RUNNING_WORK,
    RUNNING_REST,
    RUNNING_STOPWATCH,
    PAUSED,
    FINISHED
}
