package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.audio.WavSynthesizer
import pt.feubjj.timer.data.InMemoryTimerStorage
import pt.feubjj.timer.domain.engine.TimerEngine
import pt.feubjj.timer.domain.model.CustomRoundBuilderState
import pt.feubjj.timer.domain.model.MatchScoreState
import pt.feubjj.timer.domain.model.MatchWinnerSide
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.model.TimerPhase

class TatamiFlexibilityTest {

    @Test
    fun `endless open-mat rounds cycle work and rest indefinitely without terminating`() {
        val endless = RoundConfig.endless(workSeconds = 4, restSeconds = 2)
        val prefs = SoundPreferences(prepSeconds = 0, beep10s = false)
        assertTrue(endless.isEndless)
        assertTrue(endless.isMultiRound)
        assertEquals("∞ × 4s / 2s", endless.summaryBadge())

        var step = TimerEngine.start(endless, prefs, includeLeadIn = false)
        assertEquals(TimerPhase.RUNNING_WORK, step.snapshot.phase)
        assertEquals(1, step.snapshot.currentRound)

        // Finish Round 1 (4s) -> enters Rest (2s) with ROUND_END_TO_REST
        repeat(4) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(TimerPhase.RUNNING_REST, step.snapshot.phase)
        assertEquals(2L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.ROUND_END_TO_REST, step.cue)

        // Finish Rest (2s) -> enters Round 2 immediately (since prepSeconds == 0) with FIGHT_HORN
        repeat(2) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(TimerPhase.RUNNING_WORK, step.snapshot.phase)
        assertEquals(2, step.snapshot.currentRound)
        assertEquals(4L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.FIGHT_HORN, step.cue)

        // Advance through 5 more full cycles -> reaches Round 7 and never hits FINISHED
        repeat(5 * 6) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(7, step.snapshot.currentRound)
        assertEquals(TimerPhase.RUNNING_WORK, step.snapshot.phase)
    }

    @Test
    fun `stopwatch mode counts upward from zero without terminating`() {
        val stopwatch = RoundConfig.stopwatch()
        val prefs = SoundPreferences(prepSeconds = 0)
        assertTrue(stopwatch.isStopwatch)

        var step = TimerEngine.start(stopwatch, prefs, includeLeadIn = false)
        assertEquals(TimerPhase.RUNNING_STOPWATCH, step.snapshot.phase)
        assertEquals(0L, step.snapshot.remainingSeconds)

        repeat(65) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(TimerPhase.RUNNING_STOPWATCH, step.snapshot.phase)
        assertEquals(65L, step.snapshot.remainingSeconds)
        assertEquals("01:05", step.snapshot.formattedRemaining)
    }

    @Test
    fun `live mid-session controls adjust time, restart round, and skip phases cleanly`() {
        val config = RoundConfig(repeat = 3, workSeconds = 300, restSeconds = 60)
        val prefs = SoundPreferences(prepSeconds = 0)
        var state = TimerEngine.start(config, prefs, includeLeadIn = false).snapshot

        repeat(40) { state = TimerEngine.tickOneSecond(state, prefs).snapshot }
        assertEquals(260L, state.remainingSeconds)

        state = TimerEngine.adjustRemainingSeconds(state, +10L)
        assertEquals(270L, state.remainingSeconds)
        state = TimerEngine.adjustRemainingSeconds(state, -30L)
        assertEquals(240L, state.remainingSeconds)

        state = TimerEngine.restartCurrentPhase(state)
        assertEquals(300L, state.remainingSeconds)
        assertEquals(1, state.currentRound)

        val skippedToRest = TimerEngine.skipToNextPhase(state, prefs)
        assertEquals(TimerPhase.RUNNING_REST, skippedToRest.snapshot.phase)
        assertEquals(60L, skippedToRest.snapshot.remainingSeconds)

        val skippedToRound2 = TimerEngine.skipToNextPhase(skippedToRest.snapshot, prefs)
        assertEquals(TimerPhase.RUNNING_WORK, skippedToRound2.snapshot.phase)
        assertEquals(2, skippedToRound2.snapshot.currentRound)
        assertEquals(300L, skippedToRound2.snapshot.remainingSeconds)

        val backToRound1 = TimerEngine.previousRound(skippedToRound2.snapshot)
        assertEquals(1, backToRound1.currentRound)
        assertEquals(TimerPhase.RUNNING_WORK, backToRound1.phase)
        assertEquals(300L, backToRound1.remainingSeconds)
    }

    @Test
    fun `custom builder supports endless toggle, 5s sub-minute Tabata steps, and 1-minute jumps`() {
        var builder = CustomRoundBuilderState(repeat = 1, workSeconds = 30L, restSeconds = 10L)

        builder = builder.toggleEndless()
        assertTrue(builder.isEndless)
        assertEquals(RoundConfig.ENDLESS_ROUNDS, builder.repeat)
        builder = builder.toggleEndless()
        assertFalse(builder.isEndless)
        assertEquals(1, builder.repeat)

        builder = builder.adjustWorkSmart(increase = false)
        builder = builder.adjustWorkSmart(increase = false)
        assertEquals(20L, builder.workSeconds)

        builder = builder.adjustWorkSeconds(increase = true, stepSeconds = 60L)
        assertEquals(80L, builder.workSeconds)
    }

    @Test
    fun `configurable 5s vocal prep countdown and independent mid-round warnings trigger accurately`() {
        val config = RoundConfig.single(workSeconds = 125)
        val prefs = SoundPreferences(
            prepSeconds = 5,
            warn2m = true,
            warn1m = true,
            warn30s = true,
            beep50s = false,
            warnHalfway = false
        )

        var step = TimerEngine.start(config, prefs, includeLeadIn = true)
        assertEquals(TimerPhase.LEAD_IN, step.snapshot.phase)
        assertEquals(5, step.snapshot.leadInCount)
        assertEquals(SoundCue.COUNT_5, step.cue)

        // The sixth tick consumes the first second after the horn.
        repeat(6) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(TimerPhase.RUNNING_WORK, step.snapshot.phase)
        assertEquals(124L, step.snapshot.remainingSeconds)

        // 124 -> 120: emits WARN_2M ("Dois minutos!")
        repeat(4) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(120L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.WARN_2M, step.cue)

        // 120 -> 60: emits WARN_1M ("Um minuto!")
        repeat(60) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(60L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.WARN_1M, step.cue)

        // 60 -> 30: emits WARN_30S ("Trinta segundos!")
        repeat(30) { step = TimerEngine.tickOneSecond(step.snapshot, prefs) }
        assertEquals(30L, step.snapshot.remainingSeconds)
        assertEquals(SoundCue.WARN_30S, step.cue)
    }

    @Test
    fun `storage persists editable preset slots and resets to defaults`() {
        val storage = InMemoryTimerStorage()
        val defaults = storage.loadPresetSlots()
        assertEquals(4, defaults.size)

        val customPreset = RoundConfig(repeat = 10, workSeconds = 300L, restSeconds = 90L)
        storage.savePresetSlot(index = 0, config = customPreset)
        assertEquals(customPreset, storage.loadPresetSlots()[0].config)

        storage.resetPresetSlots()
        assertEquals(defaults[0].config, storage.loadPresetSlots()[0].config)
    }

    @Test
    fun `preset slot name is optional, trimmed, capped, and cleared by saving without one`() {
        val storage = InMemoryTimerStorage()
        val cfg = RoundConfig(repeat = 3, workSeconds = 120L, restSeconds = 30L)

        storage.savePresetSlot(index = 1, config = cfg)
        assertEquals(null, storage.loadPresetSlots()[1].customName)

        storage.savePresetSlot(index = 1, config = cfg, name = "  Open mat sábado muito longo  ")
        assertEquals("Open mat sábad", storage.loadPresetSlots()[1].customName)

        storage.savePresetSlot(index = 1, config = cfg, name = "   ")
        assertEquals(null, storage.loadPresetSlots()[1].customName)
    }

    @Test
    fun `IBJJF competition scoreboard tracks names, direct edits, swaps, tie-breaks, and resets`() {
        var score = MatchScoreState()
            .withFighterAName("Carlos Prates")
            .withFighterBName("Pedro Dias")
            .addFighterAPoints(2)
            .addFighterBPoints(2)
            .addFighterBAdvantages(1)

        // Tied 2-2 on points, Fighter B ahead by 1 advantage -> B wins by Vantagens
        assertEquals(MatchWinnerSide.FIGHTER_B, score.computeIbjjfWinner())
        assertEquals("Vantagens", score.defaultIbjjfMethod())

        // Direct edit Fighter A points to 12
        score = score.setFighterAPoints(12)
        assertEquals(12, score.fighterAPoints)
        assertEquals(MatchWinnerSide.FIGHTER_A, score.computeIbjjfWinner())

        // Swap sides flips names and scores
        val swapped = score.swapSides()
        assertEquals("Pedro Dias", swapped.fighterAName)
        assertEquals(12, swapped.fighterBPoints)

        val reset = swapped.reset()
        assertNotNull(reset)
        assertEquals(0, reset.fighterAPoints)
        assertEquals("", reset.fighterAName)
    }

    @Test
    fun `synthesizes distinct valid WAV buffers across all 3 tatami sound packs`() {
        for (pack in SoundPack.entries) {
            val wav = WavSynthesizer.generateCueWav(SoundCue.FIGHT_HORN, pack)
            assertTrue("Expected valid WAV for $pack", wav.size > 1000)
        }
    }
}
