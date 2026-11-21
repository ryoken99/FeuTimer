package pt.feubjj.timer.domain.engine

import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.domain.model.TimerSnapshot
import kotlin.math.abs

/**
 * Pure, deterministic state machine for the FEU BJJ tatami timer:
 * - Vocal countdown numbers (`COUNT_10`..`COUNT_1`) for ALL pre-round pips AND final 10s round countdown
 * - Mid-round vocal announcements (`WARN_HALFWAY`, `WARN_2M`, `WARN_1M`, `WARN_50S`, `WARN_30S`)
 * - Phase transition announcements (`FIGHT_HORN` + `"Combate!"`, `ROUND_END_TO_REST` + `"Tempo! Descanso."`, `ROUND_END_ALARM` + `"Tempo! Fim do treino!"`)
 * - Peaceful, distraction-free rest breaks (`RUNNING_REST` runs silently on screen with zero breathing pips).
 */
object TimerEngine {

    data class StepResult(
        val snapshot: TimerSnapshot,
        val cue: SoundCue? = null
    )

    fun start(
        config: RoundConfig,
        preferences: SoundPreferences,
        includeLeadIn: Boolean = true
    ): StepResult {
        if (config.isStopwatch) {
            val useLeadIn = includeLeadIn && preferences.prepSeconds > 0
            return if (useLeadIn) {
                StepResult(
                    snapshot = TimerSnapshot(
                        phase = TimerPhase.LEAD_IN,
                        config = config,
                        currentRound = 1,
                        totalRounds = 0,
                        remainingSeconds = 0L,
                        phaseTotalSeconds = 0L,
                        leadInCount = preferences.prepSeconds,
                        isBlinking = true
                    ),
                    cue = SoundCue.forCountdownSecond(preferences.prepSeconds)
                )
            } else {
                StepResult(
                    snapshot = TimerSnapshot(
                        phase = TimerPhase.RUNNING_STOPWATCH,
                        config = config,
                        currentRound = 1,
                        totalRounds = 0,
                        remainingSeconds = 0L,
                        phaseTotalSeconds = 0L,
                        leadInCount = 0,
                        isBlinking = false
                    ),
                    cue = if (includeLeadIn) SoundCue.FIGHT_HORN else null
                )
            }
        }

        val totalRounds = if (config.isEndless) 0 else config.repeat.coerceAtLeast(0)
        val leadInSec = preferences.prepSeconds.coerceAtLeast(0)

        return if (includeLeadIn && leadInSec > 0) {
            StepResult(
                snapshot = TimerSnapshot(
                    phase = TimerPhase.LEAD_IN,
                    config = config,
                    currentRound = 1,
                    totalRounds = totalRounds,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    leadInCount = leadInSec,
                    isBlinking = true
                ),
                cue = SoundCue.forCountdownSecond(leadInSec)
            )
        } else {
            StepResult(
                snapshot = TimerSnapshot(
                    phase = TimerPhase.RUNNING_WORK,
                    config = config,
                    currentRound = 1,
                    totalRounds = totalRounds,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    leadInCount = 0,
                    isBlinking = config.workSeconds <= 10L
                ),
                cue = if (includeLeadIn) SoundCue.FIGHT_HORN else null
            )
        }
    }

    fun tickOneSecond(
        current: TimerSnapshot,
        preferences: SoundPreferences
    ): StepResult {
        val config = current.config ?: return StepResult(current, null)

        return when (current.phase) {
            TimerPhase.IDLE,
            TimerPhase.PAUSED,
            TimerPhase.FINISHED -> StepResult(current, null)

            TimerPhase.LEAD_IN -> tickLeadIn(current, config)
            TimerPhase.RUNNING_WORK -> tickWork(current, config, preferences)
            TimerPhase.RUNNING_REST -> tickRest(current, config, preferences)
            TimerPhase.RUNNING_STOPWATCH -> tickStopwatch(current)
        }
    }

    fun pause(current: TimerSnapshot): TimerSnapshot {
        if (current.phase != TimerPhase.RUNNING_WORK &&
            current.phase != TimerPhase.RUNNING_REST &&
            current.phase != TimerPhase.RUNNING_STOPWATCH &&
            current.phase != TimerPhase.LEAD_IN
        ) {
            return current
        }
        return current.copy(
            phase = TimerPhase.PAUSED,
            isBlinking = true,
            pausedFromPhase = current.phase
        )
    }

    fun resume(current: TimerSnapshot): TimerSnapshot {
        if (current.phase != TimerPhase.PAUSED) return current
        val restoredPhase = current.pausedFromPhase ?: TimerPhase.RUNNING_WORK
        val shouldBlink = when (restoredPhase) {
            TimerPhase.LEAD_IN -> true
            TimerPhase.RUNNING_WORK -> current.remainingSeconds in 1L..10L
            else -> false
        }
        return current.copy(
            phase = restoredPhase,
            isBlinking = shouldBlink,
            pausedFromPhase = null
        )
    }

    /** Adds or subtracts seconds live during an active or paused session. */
    fun adjustRemainingSeconds(current: TimerSnapshot, deltaSeconds: Long): TimerSnapshot {
        val activePhase = if (current.phase == TimerPhase.PAUSED) current.pausedFromPhase else current.phase
        if (activePhase == TimerPhase.RUNNING_STOPWATCH) {
            val adjusted = (current.remainingSeconds + deltaSeconds).coerceAtLeast(0L)
            return current.copy(remainingSeconds = adjusted)
        }
        if (activePhase != TimerPhase.RUNNING_WORK &&
            activePhase != TimerPhase.RUNNING_REST &&
            activePhase != TimerPhase.LEAD_IN
        ) {
            return current
        }
        val adjusted = (current.remainingSeconds + deltaSeconds).coerceIn(1L, 7200L)
        val newTotal = maxOf(current.phaseTotalSeconds, adjusted)
        val shouldBlink = when (current.phase) {
            TimerPhase.PAUSED, TimerPhase.LEAD_IN -> true
            TimerPhase.RUNNING_WORK -> adjusted <= 10L
            else -> false
        }
        return current.copy(
            remainingSeconds = adjusted,
            phaseTotalSeconds = newTotal,
            isBlinking = shouldBlink
        )
    }

    /** Sets the exact remaining seconds on an active or paused timer (e.g., referee clock edit). */
    fun setExactRemainingSeconds(current: TimerSnapshot, exactSeconds: Long): TimerSnapshot {
        val clamped = exactSeconds.coerceIn(1L, 7200L)
        val newTotal = maxOf(current.phaseTotalSeconds, clamped)
        val shouldBlink = when (current.phase) {
            TimerPhase.PAUSED, TimerPhase.LEAD_IN -> true
            TimerPhase.RUNNING_WORK -> clamped <= 10L
            else -> false
        }
        return current.copy(
            remainingSeconds = clamped,
            phaseTotalSeconds = newTotal,
            isBlinking = shouldBlink
        )
    }

    /** Resets the current round or stopwatch back to its initial duration. */
    fun restartCurrentPhase(current: TimerSnapshot): TimerSnapshot {
        val config = current.config ?: return current
        if (config.isStopwatch) {
            return current.copy(
                phase = if (current.phase == TimerPhase.PAUSED) TimerPhase.PAUSED else TimerPhase.RUNNING_STOPWATCH,
                remainingSeconds = 0L,
                isBlinking = current.phase == TimerPhase.PAUSED
            )
        }
        return current.copy(
            phase = if (current.phase == TimerPhase.PAUSED) TimerPhase.PAUSED else TimerPhase.RUNNING_WORK,
            pausedFromPhase = if (current.phase == TimerPhase.PAUSED) TimerPhase.RUNNING_WORK else null,
            remainingSeconds = config.workSeconds,
            phaseTotalSeconds = config.workSeconds,
            leadInCount = 0,
            isBlinking = if (current.phase == TimerPhase.PAUSED) true else config.workSeconds <= 10L
        )
    }

    /** Skips immediately from Lead-In -> Work, Work -> Rest/Next Round, or Rest -> Next Round. */
    fun skipToNextPhase(
        current: TimerSnapshot,
        preferences: SoundPreferences
    ): StepResult {
        val config = current.config ?: return StepResult(current, null)
        if (config.isStopwatch) return StepResult(current, null)

        val effectivePhase = if (current.phase == TimerPhase.PAUSED) {
            current.pausedFromPhase ?: TimerPhase.RUNNING_WORK
        } else {
            current.phase
        }

        return when (effectivePhase) {
            TimerPhase.LEAD_IN -> StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.RUNNING_WORK,
                    pausedFromPhase = null,
                    leadInCount = 0,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    isBlinking = config.workSeconds <= 10L
                ),
                cue = SoundCue.FIGHT_HORN
            )

            TimerPhase.RUNNING_WORK -> {
                val hasMoreRounds = config.isEndless || (config.isMultiRound && current.currentRound < current.totalRounds)
                if (!hasMoreRounds) {
                    StepResult(
                        snapshot = current.copy(
                            phase = TimerPhase.FINISHED,
                            pausedFromPhase = null,
                            remainingSeconds = 0L,
                            isBlinking = false
                        ),
                        cue = SoundCue.ROUND_END_ALARM
                    )
                } else if (config.restSeconds > 0L) {
                    StepResult(
                        snapshot = current.copy(
                            phase = TimerPhase.RUNNING_REST,
                            pausedFromPhase = null,
                            remainingSeconds = config.restSeconds,
                            phaseTotalSeconds = config.restSeconds,
                            isBlinking = false
                        ),
                        cue = SoundCue.ROUND_END_TO_REST
                    )
                } else {
                    advanceToNextWorkRound(current, config, preferences)
                }
            }

            TimerPhase.RUNNING_REST -> StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.RUNNING_WORK,
                    pausedFromPhase = null,
                    currentRound = current.currentRound + 1,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    leadInCount = 0,
                    isBlinking = config.workSeconds <= 10L
                ),
                cue = SoundCue.FIGHT_HORN
            )

            else -> StepResult(current, null)
        }
    }

    /** Steps back to the start of the previous round (or Round 1 if already on Round 1). */
    fun previousRound(current: TimerSnapshot): TimerSnapshot {
        val config = current.config ?: return current
        if (config.isStopwatch) return restartCurrentPhase(current)
        val prevRound = (current.currentRound - 1).coerceAtLeast(1)
        return current.copy(
            phase = TimerPhase.RUNNING_WORK,
            pausedFromPhase = null,
            currentRound = prevRound,
            remainingSeconds = config.workSeconds,
            phaseTotalSeconds = config.workSeconds,
            leadInCount = 0,
            isBlinking = config.workSeconds <= 10L
        )
    }

    private fun tickStopwatch(current: TimerSnapshot): StepResult {
        val next = current.remainingSeconds + 1L
        return StepResult(
            snapshot = current.copy(remainingSeconds = next, isBlinking = false),
            cue = null
        )
    }

    private fun tickLeadIn(current: TimerSnapshot, config: RoundConfig): StepResult {
        return when {
            current.leadInCount > 1 -> {
                val nextCount = current.leadInCount - 1
                StepResult(
                    snapshot = current.copy(leadInCount = nextCount, isBlinking = true),
                    cue = SoundCue.forCountdownSecond(nextCount)
                )
            }
            current.leadInCount == 1 -> {
                StepResult(
                    snapshot = current.copy(leadInCount = 0, isBlinking = true),
                    cue = SoundCue.FIGHT_HORN
                )
            }
            else -> {
                val nextPhase = if (config.isStopwatch) TimerPhase.RUNNING_STOPWATCH else TimerPhase.RUNNING_WORK
                StepResult(
                    snapshot = current.copy(
                        phase = nextPhase,
                        leadInCount = 0,
                        isBlinking = nextPhase == TimerPhase.RUNNING_WORK && current.remainingSeconds in 1L..10L
                    ),
                    cue = null
                )
            }
        }
    }

    private fun tickWork(
        current: TimerSnapshot,
        config: RoundConfig,
        preferences: SoundPreferences
    ): StepResult {
        val nextSeconds = (current.remainingSeconds - 1L).coerceAtLeast(0L)
        if (nextSeconds > 0L) {
            val cue = resolveWorkTickCue(nextSeconds, config.workSeconds, preferences)
            val blinking = current.isBlinking || nextSeconds <= 10L
            return StepResult(
                snapshot = current.copy(
                    remainingSeconds = nextSeconds,
                    isBlinking = blinking
                ),
                cue = cue
            )
        }

        // Work round reached 00:00
        val hasMoreRounds = config.isEndless || (config.isMultiRound && current.currentRound < current.totalRounds)
        if (!hasMoreRounds) {
            return StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.FINISHED,
                    remainingSeconds = 0L,
                    isBlinking = false
                ),
                cue = SoundCue.ROUND_END_ALARM
            )
        }

        return if (config.restSeconds > 0L) {
            StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.RUNNING_REST,
                    remainingSeconds = config.restSeconds,
                    phaseTotalSeconds = config.restSeconds,
                    isBlinking = false
                ),
                cue = SoundCue.ROUND_END_TO_REST
            )
        } else {
            advanceToNextWorkRound(current, config, preferences)
        }
    }

    private fun resolveWorkTickCue(
        nextSeconds: Long,
        totalWorkSeconds: Long,
        preferences: SoundPreferences
    ): SoundCue? {
        // 1. Final 10-second vocal countdown (10..1)
        if (nextSeconds in 1L..10L) {
            return if (preferences.beep10s) SoundCue.forCountdownSecond(nextSeconds.toInt()) else null
        }

        if (nextSeconds >= totalWorkSeconds) return null

        // 2. Explicit mid-round / end-of-round time marks
        when {
            nextSeconds == 120L && preferences.warn2m -> return SoundCue.WARN_2M
            nextSeconds == 60L && preferences.warn1m -> return SoundCue.WARN_1M
            nextSeconds == 50L && preferences.beep50s -> return SoundCue.WARN_50S
            nextSeconds == 30L && preferences.warn30s -> return SoundCue.WARN_30S
        }

        // 3. Halfway callout ("Metade do tempo!") on rounds >= 40s unless adjacent to an enabled fixed mark
        if (preferences.warnHalfway && totalWorkSeconds >= 40L) {
            val halfway = totalWorkSeconds / 2L
            if (nextSeconds == halfway && halfway > 12L && !isNearEnabledFixedMark(halfway, totalWorkSeconds, preferences)) {
                return SoundCue.WARN_HALFWAY
            }
        }

        return null
    }

    private fun isNearEnabledFixedMark(
        candidateSec: Long,
        totalWorkSeconds: Long,
        preferences: SoundPreferences
    ): Boolean {
        val activeMarks = buildList {
            if (preferences.warn2m && totalWorkSeconds > 120L) add(120L)
            if (preferences.warn1m && totalWorkSeconds > 60L) add(60L)
            if (preferences.beep50s && totalWorkSeconds > 50L) add(50L)
            if (preferences.warn30s && totalWorkSeconds > 30L) add(30L)
        }
        return activeMarks.any { mark -> abs(candidateSec - mark) < 3L }
    }

    private fun tickRest(
        current: TimerSnapshot,
        config: RoundConfig,
        preferences: SoundPreferences
    ): StepResult {
        val nextSeconds = (current.remainingSeconds - 1L).coerceAtLeast(0L)
        if (nextSeconds > 0L) {
            // Peaceful, silent rest countdown — athletes simply see their break remaining on screen
            return StepResult(
                snapshot = current.copy(
                    remainingSeconds = nextSeconds,
                    isBlinking = false
                ),
                cue = null
            )
        }

        return advanceToNextWorkRound(current, config, preferences)
    }

    private fun advanceToNextWorkRound(
        current: TimerSnapshot,
        config: RoundConfig,
        preferences: SoundPreferences
    ): StepResult {
        val nextRoundNumber = current.currentRound + 1
        val leadInSec = preferences.prepSeconds.coerceAtLeast(0)
        return if (leadInSec > 0) {
            StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.LEAD_IN,
                    currentRound = nextRoundNumber,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    leadInCount = leadInSec,
                    isBlinking = true
                ),
                cue = SoundCue.forCountdownSecond(leadInSec)
            )
        } else {
            StepResult(
                snapshot = current.copy(
                    phase = TimerPhase.RUNNING_WORK,
                    currentRound = nextRoundNumber,
                    remainingSeconds = config.workSeconds,
                    phaseTotalSeconds = config.workSeconds,
                    leadInCount = 0,
                    isBlinking = config.workSeconds <= 10L
                ),
                cue = SoundCue.FIGHT_HORN
            )
        }
    }
}
