package pt.feubjj.timer.domain.model

/**
 * Optional repeat + rest settings applied when a Quick Timer (`00:30`..`30:00`) is started:
 * - [repeat] = how many times the chosen time runs (`1` = a single countdown, `2..99` = repeated rounds).
 * - [restSeconds] = rest between repeats (`0` = back-to-back, no rest). Ignored when [repeat] is `1`.
 */
data class QuickTimerOptions(
    val repeat: Int = 1,
    val restSeconds: Long = 60L
) {
    fun adjustRepeat(increase: Boolean): QuickTimerOptions =
        copy(repeat = (repeat + if (increase) 1 else -1).coerceIn(MIN_REPEAT, MAX_REPEAT))

    /** `5s` steps below 30s, `10s` steps from 30s up, `0` = no rest. */
    fun adjustRest(increase: Boolean): QuickTimerOptions {
        val step = if ((increase && restSeconds < 30L) || (!increase && restSeconds <= 30L)) 5L else 10L
        val next = restSeconds + if (increase) step else -step
        return copy(restSeconds = next.coerceIn(0L, MAX_REST_SECONDS))
    }

    fun toRoundConfig(workSeconds: Long): RoundConfig =
        if (repeat <= 1) {
            RoundConfig.single(workSeconds)
        } else {
            RoundConfig(repeat = repeat, workSeconds = workSeconds, restSeconds = restSeconds)
        }

    companion object {
        const val MIN_REPEAT = 1
        const val MAX_REPEAT = 99
        const val MAX_REST_SECONDS = 3600L
    }
}
