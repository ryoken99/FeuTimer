package pt.feubjj.timer.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.audio.SoundPlayerPort
import pt.feubjj.timer.data.InMemoryTimerStorage
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.ui.input.HomeShortcut

@OptIn(ExperimentalCoroutinesApi::class)
class FeuTimerSessionControllerTest {

    private class RecordingSoundPlayer : SoundPlayerPort {
        val playedCues = mutableListOf<SoundCue>()
        var stopCount = 0

        override fun play(cue: SoundCue, pack: SoundPack, voiceEnabled: Boolean) {
            playedCues += cue
        }

        override fun stopAll() {
            stopCount++
        }

        override fun release() = Unit
    }

    @Test
    fun `navigates TV screens and handles TV remote Back button matching JJTimer behavior`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = InMemoryTimerStorage()
        val sounds = RecordingSoundPlayer()
        val controller = FeuTimerSessionController(storage, sounds, backgroundScope, dispatcher)

        assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)
        assertFalse(controller.onBackPressed())

        controller.openQuickTimers()
        assertEquals(TvScreen.QUICK_TIMERS, controller.uiState.value.screen)
        assertTrue(controller.onBackPressed())
        assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)

        controller.openRounds()
        assertEquals(TvScreen.ROUNDS, controller.uiState.value.screen)
        assertTrue(controller.onBackPressed())
        assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)
    }

    @Test
    fun `home remote wheel maps each direction and OK to its destination, only from the idle clock`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = FeuTimerSessionController(InMemoryTimerStorage(), RecordingSoundPlayer(), backgroundScope, dispatcher)
        val destinations = mapOf(
            HomeShortcut.UP to TvScreen.QUICK_TIMERS,
            HomeShortcut.LEFT to TvScreen.ROUNDS,
            HomeShortcut.RIGHT to TvScreen.SCOREBOARD,
            HomeShortcut.DOWN to TvScreen.SETTINGS,
            HomeShortcut.OK to TvScreen.ACTIVE_TIMER
        )
        for ((shortcut, screen) in destinations) {
            controller.runHomeShortcut(shortcut)
            assertEquals(screen, controller.uiState.value.screen)
            // Away from the clock the wheel is inert: arrows belong to that screen
            controller.runHomeShortcut(HomeShortcut.DOWN)
            assertEquals(screen, controller.uiState.value.screen)
            controller.returnToClock()
            assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)
        }
        controller.runHomeShortcut(HomeShortcut.OK)
        assertTrue(controller.uiState.value.timer.config?.isStopwatch == true)
    }

    @Test
    fun `persists sound toggles, custom round builder, and last session badge`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = InMemoryTimerStorage()
        val sounds = RecordingSoundPlayer()
        val controller = FeuTimerSessionController(storage, sounds, backgroundScope, dispatcher)

        controller.toggleBeep50s()
        assertFalse(controller.uiState.value.soundPreferences.beep50s)
        assertFalse(storage.loadSoundPreferences().beep50s)

        controller.adjustCustomRepeat(increase = true)
        controller.adjustCustomRepeat(increase = true)
        controller.adjustCustomWork(increase = true)
        controller.adjustCustomWork(increase = true)
        controller.adjustCustomRest(increase = false)
        controller.startCustomRound()
        runCurrent()

        assertEquals(TvScreen.ACTIVE_TIMER, controller.uiState.value.screen)
        val savedCustom = storage.loadCustomRound()
        assertNotNull(savedCustom)
        assertEquals(RoundConfig(repeat = 4, workSeconds = 80L, restSeconds = 20L), savedCustom)
        assertEquals("4 × 1m 20s / 20s", controller.uiState.value.lastSessionBadge)

        assertTrue(controller.onBackPressed())
        assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)
        controller.repeatLastSession()
        runCurrent()
        assertEquals(TvScreen.ACTIVE_TIMER, controller.uiState.value.screen)
        assertEquals(4, controller.uiState.value.timer.totalRounds)
    }

    @Test
    fun `runs full vocal countdown to completion and automatically returns to academy wall clock`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = InMemoryTimerStorage()
        val sounds = RecordingSoundPlayer()
        val controller = FeuTimerSessionController(storage, sounds, backgroundScope, dispatcher)

        controller.startQuickTimer("00:30")
        runCurrent()

        assertEquals(TvScreen.ACTIVE_TIMER, controller.uiState.value.screen)
        assertEquals(TimerPhase.LEAD_IN, controller.uiState.value.timer.phase)
        assertEquals(listOf(SoundCue.COUNT_3), sounds.playedCues)

        // One second after the horn, the clock shows 29s
        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(TimerPhase.RUNNING_WORK, controller.uiState.value.timer.phase)
        assertEquals(29L, controller.uiState.value.timer.remainingSeconds)
        assertTrue(sounds.playedCues.contains(SoundCue.FIGHT_HORN))

        // Pause via TV remote Play/Pause key for 5 seconds -> time does not advance
        controller.togglePlayPause()
        assertEquals(TimerPhase.PAUSED, controller.uiState.value.timer.phase)
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(29L, controller.uiState.value.timer.remainingSeconds)

        // Resume and run 29 seconds to 00:00 -> counts 10..1, plays end alarm, returns to CLOCK after 3s
        controller.togglePlayPause()
        advanceTimeBy(29_000L)
        runCurrent()
        assertEquals(TimerPhase.FINISHED, controller.uiState.value.timer.phase)
        assertTrue(sounds.playedCues.contains(SoundCue.COUNT_10))
        assertTrue(sounds.playedCues.contains(SoundCue.COUNT_1))
        assertTrue(sounds.playedCues.contains(SoundCue.ROUND_END_ALARM))

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(TvScreen.CLOCK, controller.uiState.value.screen)
        assertEquals(TimerPhase.IDLE, controller.uiState.value.timer.phase)
    }

    @Test
    fun `scoreboard supports 3-minute fights with vocal count-in, 10s countdown, and MATCH_END`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sounds = RecordingSoundPlayer()
        val controller = FeuTimerSessionController(InMemoryTimerStorage(), sounds, backgroundScope, dispatcher)

        controller.selectScoreboardDuration(180L)
        assertEquals(TvScreen.SCOREBOARD, controller.uiState.value.screen)
        assertEquals(180L, controller.uiState.value.timer.remainingSeconds)
        assertTrue(controller.uiState.value.scoreboardArmed)

        // First Play runs the vocal count-in (3-2-1-GO) before the match clock starts
        controller.togglePlayPause()
        runCurrent()
        assertEquals(TimerPhase.LEAD_IN, controller.uiState.value.timer.phase)
        assertEquals(SoundCue.COUNT_3, sounds.playedCues.first())
        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(TimerPhase.RUNNING_WORK, controller.uiState.value.timer.phase)
        assertEquals(179L, controller.uiState.value.timer.remainingSeconds)
        // Every count-in beat is announced, in order: Três, Dois, Um, Combate!
        assertEquals(
            listOf(SoundCue.COUNT_3, SoundCue.COUNT_2, SoundCue.COUNT_1, SoundCue.FIGHT_HORN),
            sounds.playedCues.take(4)
        )

        // Run the full 3:00 -> vocal 10..1 countdown and match end announcement
        advanceTimeBy(179_000L)
        runCurrent()
        assertEquals(TimerPhase.FINISHED, controller.uiState.value.timer.phase)
        assertTrue(sounds.playedCues.contains(SoundCue.COUNT_10))
        assertTrue(sounds.playedCues.contains(SoundCue.COUNT_1))
        assertTrue(sounds.playedCues.contains(SoundCue.MATCH_END))
        assertFalse(sounds.playedCues.contains(SoundCue.ROUND_END_ALARM))
    }

    @Test
    fun `quick timer honours chosen repeat count and rest and persists them`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = InMemoryTimerStorage()
        val controller = FeuTimerSessionController(storage, RecordingSoundPlayer(), backgroundScope, dispatcher)

        controller.adjustQuickRepeat(true) // 2
        controller.adjustQuickRepeat(true) // 3
        controller.adjustQuickRest(false)  // 55s
        assertEquals(3, controller.uiState.value.quickOptions.repeat)
        assertEquals(3, storage.loadQuickOptions().repeat)

        controller.startQuickTimer("02:00")
        runCurrent()
        val cfg = controller.uiState.value.timer.config
        assertEquals(RoundConfig(repeat = 3, workSeconds = 120L, restSeconds = storage.loadQuickOptions().restSeconds), cfg)
        assertEquals(3, controller.uiState.value.timer.totalRounds)
    }
}
