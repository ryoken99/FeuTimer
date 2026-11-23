package pt.feubjj.timer.domain.model

import pt.feubjj.timer.audio.SoundPack

/**
 * User-configurable tatami vocal coach, warning marks, countdown, and clock display settings.
 *
 * Breaks (`DESCANSO`) run peacefully and silently on screen with no repetitive breathing pips.
 *
 * - [voiceEnabled]: Enables PT-PT vocal announcements (`"Dez..Um"`, `"Metade do tempo!"`, `"Cinquenta segundos!"`, `"Combate!"`, `"Tempo! Descanso."`).
 * - [warnHalfway]: Announces `"Metade do tempo!"` at the exact halfway second of rounds >= 40s.
 * - [warn2m]: Announces `"Dois minutos!"` at `02:00` remaining.
 * - [warn1m]: Announces `"Um minuto!"` at `01:00` remaining.
 * - [beep50s]: Announces `"Cinquenta segundos!"` (or clapper) at `00:50` remaining.
 * - [warn30s]: Announces `"Trinta segundos!"` at `00:30` remaining.
 * - [beep10s]: Enables the 10-second vocal countdown (`10..1`) + visual pulse at round end.
 * - [prepSeconds]: Pre-round lead-in countdown (`0`, `3`, `5`, or `10` seconds; default `3`).
 * - [soundPack]: Acoustic profile ([SoundPack.CLASSIC_HORN], [SoundPack.BELL_GONG], [SoundPack.WHISTLE]).
 * - [use24HourClock]: `true` for 24h (`21:15:08`), `false` for 12h AM/PM (`9:15:08 PM`).
 */
data class SoundPreferences(
    val voiceEnabled: Boolean = true,
    val warnHalfway: Boolean = true,
    val warn2m: Boolean = false,
    val warn1m: Boolean = false,
    val beep50s: Boolean = true,
    val warn30s: Boolean = false,
    val beep10s: Boolean = true,
    val prepSeconds: Int = 3,
    val soundPack: SoundPack = SoundPack.CLASSIC_HORN,
    val use24HourClock: Boolean = true,
    val soundEnabled: Boolean = true
)
