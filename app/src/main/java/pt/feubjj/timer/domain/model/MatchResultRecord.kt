package pt.feubjj.timer.domain.model

import pt.feubjj.timer.domain.util.SimpleJson

/**
 * Persisted academy match result record saved from the IBJJF Scoreboard / Wi-Fi Referee Console.
 * Supports sharing, exporting, individual deletion, and bulk clear.
 */
data class MatchResultRecord(
    val id: String,
    val timestamp: String,
    val category: String,
    val fighterAName: String,
    val fighterAPoints: Int,
    val fighterAAdvantages: Int,
    val fighterAPenalties: Int,
    val fighterBName: String,
    val fighterBPoints: Int,
    val fighterBAdvantages: Int,
    val fighterBPenalties: Int,
    val winnerSide: MatchWinnerSide,
    val method: String,
    val durationSeconds: Long,
    val remainingSeconds: Long
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "timestamp" to timestamp,
        "category" to category,
        "fighterAName" to fighterAName,
        "fighterAPoints" to fighterAPoints,
        "fighterAAdvantages" to fighterAAdvantages,
        "fighterAPenalties" to fighterAPenalties,
        "fighterBName" to fighterBName,
        "fighterBPoints" to fighterBPoints,
        "fighterBAdvantages" to fighterBAdvantages,
        "fighterBPenalties" to fighterBPenalties,
        "winnerSide" to winnerSide.name,
        "method" to method,
        "durationSeconds" to durationSeconds,
        "remainingSeconds" to remainingSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MatchResultRecord {
            val winnerStr = map["winnerSide"]?.toString().orEmpty()
            val side = MatchWinnerSide.entries.firstOrNull { it.name == winnerStr } ?: MatchWinnerSide.DRAW
            return MatchResultRecord(
                id = map["id"]?.toString().orEmpty(),
                timestamp = map["timestamp"]?.toString().orEmpty(),
                category = map["category"]?.toString().orEmpty(),
                fighterAName = map["fighterAName"]?.toString().orEmpty().ifBlank { "Atleta 1" },
                fighterAPoints = (map["fighterAPoints"] as? Number)?.toInt() ?: 0,
                fighterAAdvantages = (map["fighterAAdvantages"] as? Number)?.toInt() ?: 0,
                fighterAPenalties = (map["fighterAPenalties"] as? Number)?.toInt() ?: 0,
                fighterBName = map["fighterBName"]?.toString().orEmpty().ifBlank { "Atleta 2" },
                fighterBPoints = (map["fighterBPoints"] as? Number)?.toInt() ?: 0,
                fighterBAdvantages = (map["fighterBAdvantages"] as? Number)?.toInt() ?: 0,
                fighterBPenalties = (map["fighterBPenalties"] as? Number)?.toInt() ?: 0,
                winnerSide = side,
                method = map["method"]?.toString().orEmpty().ifBlank { "Pontos" },
                durationSeconds = (map["durationSeconds"] as? Number)?.toLong() ?: 300L,
                remainingSeconds = (map["remainingSeconds"] as? Number)?.toLong() ?: 0L
            )
        }

        fun encodeList(records: List<MatchResultRecord>): String =
            SimpleJson.encodeArray(records.map { it.toMap() })

        @Suppress("UNCHECKED_CAST")
        fun decodeList(json: String?): List<MatchResultRecord> {
            if (json.isNullOrBlank()) return emptyList()
            return SimpleJson.parseArray(json).mapNotNull { item ->
                (item as? Map<String, Any?>)?.let(::fromMap)
            }
        }
    }
}
