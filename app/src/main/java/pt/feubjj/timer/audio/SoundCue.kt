package pt.feubjj.timer.audio

/**
 * Complete acoustic & vocal cue vocabulary for the FEU BJJ tatami timer:
 * - [COUNT_10]..[COUNT_1]: Spoken PT-PT countdown numbers + subtle pip (used for BOTH all pre-round pips AND final 10s round countdown)
 * - [WARN_HALFWAY], [WARN_2M], [WARN_1M], [WARN_50S], [WARN_30S]: Mid-round vocal + clapper announcements
 * - [FIGHT_HORN]: Horn/Bell/Whistle + `"Combate!"`
 * - [ROUND_END_TO_REST]: End Horn/Bell/Whistle + `"Tempo! Descanso."` (followed by peaceful, silent break countdown)
 * - [ROUND_END_ALARM]: End Horn/Bell/Whistle + `"Tempo! Fim do treino!"`
 */
enum class SoundCue {
    COUNT_10,
    COUNT_9,
    COUNT_8,
    COUNT_7,
    COUNT_6,
    COUNT_5,
    COUNT_4,
    COUNT_3,
    COUNT_2,
    COUNT_1,
    COUNTDOWN_PIP,

    WARN_HALFWAY,
    WARN_2M,
    WARN_1M,
    WARN_50S,
    WARN_30S,
    WARNING_CLAP,

    PREP_READY,
    FIGHT_HORN,
    ROUND_END_TO_REST,
    MATCH_END,
    ROUND_END_ALARM;

    companion object {
        fun forCountdownSecond(second: Int): SoundCue = when (second) {
            10 -> COUNT_10
            9 -> COUNT_9
            8 -> COUNT_8
            7 -> COUNT_7
            6 -> COUNT_6
            5 -> COUNT_5
            4 -> COUNT_4
            3 -> COUNT_3
            2 -> COUNT_2
            1 -> COUNT_1
            else -> COUNTDOWN_PIP
        }
    }
}
