package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.feubjj.timer.domain.engine.TimeFormatter
import pt.feubjj.timer.domain.engine.TimeUnitKind

class TimeFormatterTest {

    @Test
    fun `formats seconds as zero-padded MM SS`() {
        assertEquals("00:00", TimeFormatter.formatMmSs(0))
        assertEquals("00:30", TimeFormatter.formatMmSs(30))
        assertEquals("05:00", TimeFormatter.formatMmSs(300))
        assertEquals("12:05", TimeFormatter.formatMmSs(725))
        assertEquals("60:00", TimeFormatter.formatMmSs(3600))
    }

    @Test
    fun `parses MM SS quick timer strings into total seconds`() {
        assertEquals(30L, TimeFormatter.parseMmSs("00:30"))
        assertEquals(60L, TimeFormatter.parseMmSs("01:00"))
        assertEquals(300L, TimeFormatter.parseMmSs("05:00"))
        assertEquals(1800L, TimeFormatter.parseMmSs("30:00"))
    }

    @Test
    fun `formats compact workout duration matching JJTimer short time format`() {
        assertEquals("30s", TimeFormatter.formatCompactDuration(30))
        assertEquals("1m", TimeFormatter.formatCompactDuration(60))
        assertEquals("5m", TimeFormatter.formatCompactDuration(300))
        assertEquals("29m", TimeFormatter.formatCompactDuration(1740))
        assertEquals("38m 30s", TimeFormatter.formatCompactDuration(2310))
        assertEquals("47m", TimeFormatter.formatCompactDuration(2820))
        assertEquals("1h", TimeFormatter.formatCompactDuration(3600))
        assertEquals("1h 2m 10s", TimeFormatter.formatCompactDuration(3730))
    }

    @Test
    fun `extracts display value and unit kind for preset cards`() {
        assertEquals(30 to TimeUnitKind.SECOND, TimeFormatter.displayValueAndUnit(30))
        assertEquals(5 to TimeUnitKind.MINUTE, TimeFormatter.displayValueAndUnit(300))
        assertEquals(1 to TimeUnitKind.HOUR, TimeFormatter.displayValueAndUnit(3600))
        assertEquals(90 to TimeUnitKind.SECOND, TimeFormatter.displayValueAndUnit(90))
    }
}
