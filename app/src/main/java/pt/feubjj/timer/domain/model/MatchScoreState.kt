package pt.feubjj.timer.domain.model

enum class MatchWinnerSide {
    FIGHTER_A,
    FIGHTER_B,
    DRAW
}

/**
 * IBJJF Jiu-Jitsu competition scoreboard state for two athletes (`Atleta A` vs `Atleta B`),
 * supporting live athlete name editing, division label, additive & direct point/advantage/penalty editing,
 * side swapping, and official IBJJF tie-break winner resolution.
 */
data class MatchScoreState(
    val fighterAName: String = "",
    val fighterBName: String = "",
    val categoryLabel: String = "",
    val fighterAPoints: Int = 0,
    val fighterAAdvantages: Int = 0,
    val fighterAPenalties: Int = 0,
    val fighterBPoints: Int = 0,
    val fighterBAdvantages: Int = 0,
    val fighterBPenalties: Int = 0
) {
    fun withFighterAName(name: String): MatchScoreState =
        copy(fighterAName = name.take(40))

    fun withFighterBName(name: String): MatchScoreState =
        copy(fighterBName = name.take(40))

    fun withCategoryLabel(label: String): MatchScoreState =
        copy(categoryLabel = label.take(48))

    fun addFighterAPoints(delta: Int): MatchScoreState =
        copy(fighterAPoints = (fighterAPoints + delta).coerceIn(0, 999))

    fun setFighterAPoints(exact: Int): MatchScoreState =
        copy(fighterAPoints = exact.coerceIn(0, 999))

    fun addFighterAAdvantages(delta: Int): MatchScoreState =
        copy(fighterAAdvantages = (fighterAAdvantages + delta).coerceIn(0, 99))

    fun setFighterAAdvantages(exact: Int): MatchScoreState =
        copy(fighterAAdvantages = exact.coerceIn(0, 99))

    fun addFighterAPenalties(delta: Int): MatchScoreState =
        copy(fighterAPenalties = (fighterAPenalties + delta).coerceIn(0, 99))

    fun setFighterAPenalties(exact: Int): MatchScoreState =
        copy(fighterAPenalties = exact.coerceIn(0, 99))

    fun addFighterBPoints(delta: Int): MatchScoreState =
        copy(fighterBPoints = (fighterBPoints + delta).coerceIn(0, 999))

    fun setFighterBPoints(exact: Int): MatchScoreState =
        copy(fighterBPoints = exact.coerceIn(0, 999))

    fun addFighterBAdvantages(delta: Int): MatchScoreState =
        copy(fighterBAdvantages = (fighterBAdvantages + delta).coerceIn(0, 99))

    fun setFighterBAdvantages(exact: Int): MatchScoreState =
        copy(fighterBAdvantages = exact.coerceIn(0, 99))

    fun addFighterBPenalties(delta: Int): MatchScoreState =
        copy(fighterBPenalties = (fighterBPenalties + delta).coerceIn(0, 99))

    fun setFighterBPenalties(exact: Int): MatchScoreState =
        copy(fighterBPenalties = exact.coerceIn(0, 99))

    fun swapSides(): MatchScoreState = copy(
        fighterAName = fighterBName,
        fighterBName = fighterAName,
        fighterAPoints = fighterBPoints,
        fighterBPoints = fighterAPoints,
        fighterAAdvantages = fighterBAdvantages,
        fighterBAdvantages = fighterAAdvantages,
        fighterAPenalties = fighterBPenalties,
        fighterBPenalties = fighterAPenalties
    )

    fun resetScoresKeepNames(): MatchScoreState = copy(
        fighterAPoints = 0,
        fighterAAdvantages = 0,
        fighterAPenalties = 0,
        fighterBPoints = 0,
        fighterBAdvantages = 0,
        fighterBPenalties = 0
    )

    fun reset(): MatchScoreState = MatchScoreState()

    /** Official IBJJF tie-break hierarchy: Points -> Advantages -> Fewer Penalties -> Draw. */
    fun computeIbjjfWinner(): MatchWinnerSide = when {
        fighterAPoints != fighterBPoints ->
            if (fighterAPoints > fighterBPoints) MatchWinnerSide.FIGHTER_A else MatchWinnerSide.FIGHTER_B
        fighterAAdvantages != fighterBAdvantages ->
            if (fighterAAdvantages > fighterBAdvantages) MatchWinnerSide.FIGHTER_A else MatchWinnerSide.FIGHTER_B
        fighterAPenalties != fighterBPenalties ->
            if (fighterAPenalties < fighterBPenalties) MatchWinnerSide.FIGHTER_A else MatchWinnerSide.FIGHTER_B
        else -> MatchWinnerSide.DRAW
    }

    fun defaultIbjjfMethod(): String = when {
        fighterAPoints != fighterBPoints -> "Pontos"
        fighterAAdvantages != fighterBAdvantages -> "Vantagens"
        fighterAPenalties != fighterBPenalties -> "Punições"
        else -> "Empate"
    }
}
