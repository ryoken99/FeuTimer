package pt.feubjj.timer.domain.model

import pt.feubjj.timer.domain.engine.TimeFormatter

/**
 * Complete immutable state of an active or idle timer session.
 */
data class TimerSnapshot(
    val phase: TimerPhase = TimerPhase.IDLE,
    val config: RoundConfig? = null,
    val currentRound: Int = 1,
    val totalRounds: Int = 0,
    val remainingSeconds: Long = 0L,
    val phaseTotalSeconds: Long = 0L,
    val leadInCount: Int = 0,
    val isBlinking: Boolean = false,
    val pausedFromPhase: TimerPhase? = null
) {
    val formattedRemaining: String
        get() = TimeFormatter.formatMmSs(remainingSeconds)

    val progressFraction: Float
        get() = if (phaseTotalSeconds <= 0L) {
            0f
        } else {
            (remainingSeconds.toFloat() / phaseTotalSeconds.toFloat()).coerceIn(0f, 1f)
        }
}
