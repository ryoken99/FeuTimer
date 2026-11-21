package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.domain.engine.TimerEngine
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.model.TimerPhase

class TimerEngineTest {

    @Test
    fun `starting a round runs vocal 3-2-1-HORN lead-in before active work countdown`() {
        val config = RoundConfig.single(workSeconds = 60)
        val prefs = SoundPreferences(prepSeconds = 3)

        val start = TimerEngine.start(config, prefs)
        assertEquals(TimerPhase.LEAD_IN, start.snapshot.phase)
        assertEquals(3, start.snapshot.leadInCount)
        assertEquals(60L, start.snapshot.remainingSeconds)
        assertTrue(start.snapshot.isBlinking)
        assertEquals(SoundCue.COUNT_3, start.cue)

        val tick1 = TimerEngine.tickOneSecond(start.snapshot, prefs)
        assertEquals(TimerPhase.LEAD_IN, tick1.snapshot.phase)
        assertEquals(2, tick1.snapshot.leadInCount)
        assertEquals(SoundCue.COUNT_2, tick1.cue)

        val tick2 = TimerEngine.tickOneSecond(tick1.snapshot, prefs)
        assertEquals(TimerPhase.LEAD_IN, tick2.snapshot.phase)
        assertEquals(1, tick2.snapshot.leadInCount)
        assertEquals(SoundCue.COUNT_1, tick2.cue)

        // 4th second plays FIGHT_HORN ("Combate!") while displaying GO / COMBATE!
        val tick3 = TimerEngine.tickOneSecond(tick2.snapshot, prefs)
        assertEquals(TimerPhase.LEAD_IN, tick3.snapshot.phase)
        assertEquals(0, tick3.snapshot.leadInCount)
        assertEquals(SoundCue.FIGHT_HORN, tick3.cue)

        // One second has elapsed since the start horn
        val tick4 = TimerEngine.tickOneSecond(tick3.snapshot, prefs)
        assertEquals(TimerPhase.RUNNING_WORK, tick4.snapshot.phase)
        assertEquals(59L, tick4.snapshot.remainingSeconds)
        assertFalse(tick4.snapshot.isBlinking)
        assertNull(tick4.cue)
    }

    @Test
    fun `emits halfway, 50s warning, and full 10-to-1 vocal countdown before round end`() {
        val config = RoundConfig.single(workSeconds = 60)
        val prefs = SoundPreferences(
            warnHalfway = true,
            beep50s = true,
            beep10s = true
        )

        var state = TimerEngine.start(config, prefs, includeLeadIn = false).snapshot
        // 60 -> 50: emits WARN_50S at 50s remaining
        var step = TimerEngine.StepResult(state, null)
        repeat(10) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(50L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.WARN_50S, step.cue)

        // 50 -> 30: emits WARN_HALFWAY at 30s remaining (halfway of 60s)
        repeat(20) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(30L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.WARN_HALFWAY, step.cue)

        // 30 -> 11: silent
        repeat(19) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(11L, step.snapshot.remainingSeconds)
        assertNull(step.cue)

        // 11 -> 1: emits COUNT_10, COUNT_9, ..., COUNT_1 and blinks!
        val expectedCountdown = listOf(
            SoundCue.COUNT_10, SoundCue.COUNT_9, SoundCue.COUNT_8, SoundCue.COUNT_7, SoundCue.COUNT_6,
            SoundCue.COUNT_5, SoundCue.COUNT_4, SoundCue.COUNT_3, SoundCue.COUNT_2, SoundCue.COUNT_1
        )
        for ((idx, expectedCue) in expectedCountdown.withIndex()) {
            step = TimerEngine.tickOneSecond(step.snapshot, prefs)
            assertEquals((10 - idx).toLong(), step.snapshot.remainingSeconds)
            assertEquals(expectedCue, step.cue)
            assertTrue(step.snapshot.isBlinking)
        }
    }

    @Test
    fun `multi-round sparring runs silent peaceful rest breaks without breathing pips`() {
        val config = RoundConfig(repeat = 2, workSeconds = 12, restSeconds = 6)
        val prefs = SoundPreferences(beep50s = false, beep10s = false, warnHalfway = false)

        val state = TimerEngine.start(config, prefs, includeLeadIn = false).snapshot
        var step = TimerEngine.StepResult(state, null)
        repeat(12) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }

        // Enters rest with ROUND_END_TO_REST ("Tempo! Descanso.")
        assertEquals(TimerPhase.RUNNING_REST, step.snapshot.phase)
        assertEquals(6L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.ROUND_END_TO_REST, step.cue)

        // All intermediate rest seconds (5, 4, 3, 2, 1) are 100% silent
        repeat(5) {
            step = TimerEngine.tickOneSecond(step.snapshot, prefs)
            assertNull("Expected silent rest break tick", step.cue)
        }

        // Rest hits 0 -> Round 2 lead-in ("Três!")
        step = TimerEngine.tickOneSecond(step.snapshot, prefs)
        assertEquals(TimerPhase.LEAD_IN, step.snapshot.phase)
        assertEquals(2, step.snapshot.currentRound)
        assertEquals(SoundCue.COUNT_3, step.cue)

        // Finish lead-in + Round 2 -> FINISHED + ROUND_END_ALARM ("Tempo! Fim do treino!")
        repeat(3 + 12) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(TimerPhase.FINISHED, step.snapshot.phase)
        assertEquals(SoundCue.ROUND_END_ALARM, step.cue)
    }

    @Test
    fun `zero-second rest interval advances directly from round 1 to round 2 without phantom rest phase`() {
        val config = RoundConfig(repeat = 2, workSeconds = 5, restSeconds = 0)
        val prefs = SoundPreferences()

        var step = TimerEngine.start(config, prefs, includeLeadIn = false)
        repeat(5) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(2, step.snapshot.currentRound)
        assertEquals(TimerPhase.LEAD_IN, step.snapshot.phase)
    }

    @Test
    fun `pause and resume preserve exact phase and remaining time`() {
        val config = RoundConfig.single(workSeconds = 180)
        val prefs = SoundPreferences()
        val running = TimerEngine.start(config, prefs, includeLeadIn = false).snapshot
        val afterTen = (1..10).fold(running) { acc, _ -> TimerEngine.tickOneSecond(acc, prefs).snapshot }
        assertEquals(170L, afterTen.remainingSeconds)

        val paused = TimerEngine.pause(afterTen)
        assertEquals(TimerPhase.PAUSED, paused.phase)
        assertEquals(170L, paused.remainingSeconds)
        assertTrue(paused.isBlinking)

        val stillPaused = TimerEngine.tickOneSecond(paused, prefs)
        assertEquals(170L, stillPaused.snapshot.remainingSeconds)
        assertNull(stillPaused.cue)

        val resumed = TimerEngine.resume(paused)
        assertEquals(TimerPhase.RUNNING_WORK, resumed.phase)
        assertEquals(170L, resumed.remainingSeconds)
        assertFalse(resumed.isBlinking)
    }
}
