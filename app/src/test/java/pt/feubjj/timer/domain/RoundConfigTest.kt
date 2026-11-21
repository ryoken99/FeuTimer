package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.domain.model.CustomRoundBuilderState
import pt.feubjj.timer.domain.model.RoundConfig

class RoundConfigTest {

    @Test
    fun `single countdown timer has zero repeats and work duration as total`() {
        val single = RoundConfig.single(workSeconds = 300)
        assertFalse(single.isMultiRound)
        assertEquals(0, single.repeat)
        assertEquals(300L, single.workSeconds)
        assertEquals(0L, single.restSeconds)
        assertEquals(300L, single.totalDurationSeconds)
        assertEquals("05:00", single.summaryBadge())
    }

    @Test
    fun `multi-round sparring calculates total duration including n minus 1 rest intervals`() {
        val fiveByFive = RoundConfig(repeat = 5, workSeconds = 300, restSeconds = 60)
        assertTrue(fiveByFive.isMultiRound)
        assertEquals(1740L, fiveByFive.totalDurationSeconds)
        assertEquals("5 × 5m / 1m", fiveByFive.summaryBadge())
    }

    @Test
    fun `custom round builder enforces JJTimer step increments and min max bounds`() {
        var state = CustomRoundBuilderState() // default: 2 reps, 60s work, 30s rest
        assertEquals(2, state.repeat)
        assertEquals(60L, state.workSeconds)
        assertEquals(30L, state.restSeconds)

        // Decrement rep stops at 1
        state = state.adjustRepeat(increase = false).adjustRepeat(increase = false)
        assertEquals(1, state.repeat)

        // Increment rep stops at 99
        repeat(120) { state = state.adjustRepeat(increase = true) }
        assertEquals(99, state.repeat)

        // Work time steps by 10s, minimum 10s, maximum 3600s
        var minWork = CustomRoundBuilderState(workSeconds = 20L)
            .adjustWorkSeconds(increase = false)
            .adjustWorkSeconds(increase = false)
        assertEquals(10L, minWork.workSeconds)

        val maxWork = CustomRoundBuilderState(workSeconds = 3590L)
            .adjustWorkSeconds(increase = true)
            .adjustWorkSeconds(increase = true)
        assertEquals(3600L, maxWork.workSeconds)

        // Rest time steps by 10s, minimum 0s, maximum 3600s
        val minRest = CustomRoundBuilderState(restSeconds = 10L)
            .adjustRestSeconds(increase = false)
            .adjustRestSeconds(increase = false)
        assertEquals(0L, minRest.restSeconds)

        val maxRest = CustomRoundBuilderState(restSeconds = 3590L)
            .adjustRestSeconds(increase = true)
            .adjustRestSeconds(increase = true)
        assertEquals(3600L, maxRest.restSeconds)
    }
}
