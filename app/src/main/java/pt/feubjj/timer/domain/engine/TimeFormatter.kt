package pt.feubjj.timer.domain.engine

import java.util.Locale

enum class TimeUnitKind {
    HOUR,
    MINUTE,
    SECOND
}

/**
 * Pure formatting and parsing utilities for countdown clocks and BJJ round cards.
 */
object TimeFormatter {

    fun formatMmSs(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val minutes = clamped / 60L
        val seconds = clamped % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun parseMmSs(label: String): Long {
        val parts = label.trim().split(":")
        require(parts.size == 2) { "Expected MM:SS format, got: $label" }
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        return minutes * 60L + seconds
    }

    /**
     * Compact duration string matching JJTimer-Edam's `TimeUtils.convertSecondsToShortTime`
     * (e.g. `300 -> "5m"`, `2310 -> "38m 30s"`, `3600 -> "1h"`, `30 -> "30s"`).
     */
    fun formatCompactDuration(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        if (clamped == 0L) return "0s"
        val hours = clamped / 3600L
        val minutes = (clamped % 3600L) / 60L
        val seconds = clamped % 60L

        val parts = buildList {
            if (hours > 0L) add("${hours}h")
            if (minutes > 0L) add("${minutes}m")
            if (seconds > 0L) add("${seconds}s")
        }
        return parts.joinToString(" ")
    }

    /**
     * Extracts clean integer display value and unit kind for preset cards:
     * exact hours -> HOUR, exact minutes -> MINUTE, otherwise SECOND.
     */
    fun displayValueAndUnit(totalSeconds: Long): Pair<Int, TimeUnitKind> {
        val clamped = totalSeconds.coerceAtLeast(0L)
        return when {
            clamped >= 3600L && clamped % 3600L == 0L -> (clamped / 3600L).toInt() to TimeUnitKind.HOUR
            clamped >= 60L && clamped % 60L == 0L -> (clamped / 60L).toInt() to TimeUnitKind.MINUTE
            else -> clamped.toInt() to TimeUnitKind.SECOND
        }
    }
}
