package pt.feubjj.timer.domain.presets

/** Rules for the optional preset-slot name (kept short so it always fits on a card title). */
object PresetNames {
    const val MAX_LENGTH = 14

    fun clean(raw: String?): String? = raw?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotBlank() }
}
