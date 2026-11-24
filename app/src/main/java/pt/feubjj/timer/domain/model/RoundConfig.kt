package pt.feubjj.timer.domain.model

import pt.feubjj.timer.domain.engine.TimeFormatter

/**
 * Immutable configuration for single countdown (`repeat == 0`), finite multi-round (`repeat > 0`),
 * open-mat endless rounds (`repeat == ENDLESS_ROUNDS`), or progressive stopwatch (`isStopwatch == true`).
 */
data class RoundConfig(
    val repeat: Int,
    val workSeconds: Long,
    val restSeconds: Long,
    val isStopwatch: Boolean = false
) {
    val isEndless: Boolean
        get() = repeat == ENDLESS_ROUNDS

    val isMultiRound: Boolean
        get() = repeat > 0 || isEndless

    val totalDurationSeconds: Long
        get() = when {
            isStopwatch || isEndless -> 0L
            repeat <= 0 -> workSeconds
            else -> (repeat.toLong() * workSeconds) + ((repeat - 1).coerceAtLeast(0).toLong() * restSeconds)
        }

    fun summaryBadge(): String {
        return when {
            isStopwatch -> "00:00 ↑"
            isEndless -> "∞ × ${TimeFormatter.formatCompactDuration(workSeconds)} / ${TimeFormatter.formatCompactDuration(restSeconds)}"
            !isMultiRound -> TimeFormatter.formatMmSs(workSeconds)
            else -> "$repeat × ${TimeFormatter.formatCompactDuration(workSeconds)} / ${TimeFormatter.formatCompactDuration(restSeconds)}"
        }
    }

    companion object {
        const val ENDLESS_ROUNDS = -1

        fun single(workSeconds: Long): RoundConfig = RoundConfig(
            repeat = 0,
            workSeconds = workSeconds.coerceAtLeast(1L),
            restSeconds = 0L
        )

        fun endless(workSeconds: Long, restSeconds: Long): RoundConfig = RoundConfig(
            repeat = ENDLESS_ROUNDS,
            workSeconds = workSeconds.coerceAtLeast(1L),
            restSeconds = restSeconds.coerceAtLeast(0L)
        )

        fun stopwatch(): RoundConfig = RoundConfig(
            repeat = 0,
            workSeconds = 0L,
            restSeconds = 0L,
            isStopwatch = true
        )
    }
}

/**
 * Editable state for the Custom Round Builder on Android TV,
 * supporting finite (`1..99`) or endless (`∞`) rounds, `5s` sub-30s Tabata steps, `10s` steps, and `±1m` jumps.
 */
data class CustomRoundBuilderState(
    val repeat: Int = 2,
    val workSeconds: Long = 60L,
    val restSeconds: Long = 30L,
    private val lastFiniteRepeat: Int = 2
) {
    val isEndless: Boolean
        get() = repeat == RoundConfig.ENDLESS_ROUNDS

    val formattedRepeat: String
        get() = if (isEndless) "∞" else repeat.toString()

    val formattedWork: String
        get() = TimeFormatter.formatMmSs(workSeconds)

    val formattedRest: String
        get() = TimeFormatter.formatMmSs(restSeconds)

    val formattedTotal: String
        get() = if (isEndless) "∞" else TimeFormatter.formatCompactDuration(toRoundConfig().totalDurationSeconds)

    fun toggleEndless(): CustomRoundBuilderState {
        return if (isEndless) {
            copy(repeat = lastFiniteRepeat.coerceIn(MIN_REPEAT, MAX_REPEAT))
        } else {
            copy(
                repeat = RoundConfig.ENDLESS_ROUNDS,
                lastFiniteRepeat = repeat.coerceIn(MIN_REPEAT, MAX_REPEAT)
            )
        }
    }

    fun adjustRepeat(increase: Boolean, step: Int = 1): CustomRoundBuilderState {
        if (isEndless) {
            return if (increase) copy(repeat = lastFiniteRepeat.coerceIn(MIN_REPEAT, MAX_REPEAT)) else this
        }
        val delta = if (increase) step else -step
        val next = (repeat + delta).coerceIn(MIN_REPEAT, MAX_REPEAT)
        return copy(repeat = next, lastFiniteRepeat = next)
    }

    fun adjustWorkSeconds(increase: Boolean, stepSeconds: Long = 10L): CustomRoundBuilderState {
        val delta = if (increase) stepSeconds else -stepSeconds
        return copy(workSeconds = (workSeconds + delta).coerceIn(minOf(workSeconds, MIN_WORK_SECONDS), MAX_SECONDS))
    }

    /** Uses 5s steps below 30s (for Tabata 20s/15s) and 10s steps at 30s+. */
    fun adjustWorkSmart(increase: Boolean): CustomRoundBuilderState {
        val step = when {
            increase && workSeconds < 30L -> 5L
            !increase && workSeconds <= 30L -> 5L
            else -> 10L
        }
        val delta = if (increase) step else -step
        return copy(workSeconds = (workSeconds + delta).coerceIn(MIN_SMALL_WORK_SECONDS, MAX_SECONDS))
    }

    fun adjustRestSeconds(increase: Boolean, stepSeconds: Long = 10L): CustomRoundBuilderState {
        val delta = if (increase) stepSeconds else -stepSeconds
        return copy(restSeconds = (restSeconds + delta).coerceIn(MIN_REST_SECONDS, MAX_SECONDS))
    }

    fun adjustRestSmart(increase: Boolean): CustomRoundBuilderState {
        val step = when {
            increase && restSeconds < 30L -> 5L
            !increase && restSeconds <= 30L -> 5L
            else -> 10L
        }
        val delta = if (increase) step else -step
        return copy(restSeconds = (restSeconds + delta).coerceIn(MIN_REST_SECONDS, MAX_SECONDS))
    }

    fun toRoundConfig(): RoundConfig = RoundConfig(
        repeat = if (isEndless) RoundConfig.ENDLESS_ROUNDS else repeat.coerceIn(MIN_REPEAT, MAX_REPEAT),
        workSeconds = workSeconds.coerceIn(1L, MAX_SECONDS),
        restSeconds = restSeconds.coerceIn(MIN_REST_SECONDS, MAX_SECONDS)
    )

    companion object {
        const val MIN_REPEAT = 1
        const val MAX_REPEAT = 99
        const val MIN_SMALL_WORK_SECONDS = 5L
        const val MIN_WORK_SECONDS = 10L
        const val MIN_REST_SECONDS = 0L
        const val MAX_SECONDS = 3600L

        fun fromRoundConfig(config: RoundConfig): CustomRoundBuilderState {
            val rep = when {
                config.isEndless -> RoundConfig.ENDLESS_ROUNDS
                config.repeat > 0 -> config.repeat.coerceIn(MIN_REPEAT, MAX_REPEAT)
                else -> 5
            }
            return CustomRoundBuilderState(
                repeat = rep,
                workSeconds = config.workSeconds.coerceIn(1L, MAX_SECONDS),
                restSeconds = config.restSeconds.coerceIn(MIN_REST_SECONDS, MAX_SECONDS),
                lastFiniteRepeat = if (rep > 0) rep else 5
            )
        }
    }
}
