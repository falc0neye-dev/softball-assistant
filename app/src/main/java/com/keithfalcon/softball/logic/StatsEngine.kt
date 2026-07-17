package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.RunnerResult

/** Season stats auto-computed from scorecards (spec §7.3). Pure Kotlin. */
object StatsEngine {

    data class PlayerStats(
        val playerId: Long,
        val games: Int,
        val plateAppearances: Int,
        val atBats: Int,
        val hits: Int,
        val walks: Int,
        val runs: Int,
        val totalBases: Int,
    ) {
        val avg: Double get() = if (atBats == 0) 0.0 else hits.toDouble() / atBats
        val obp: Double
            get() {
                val denominator = plateAppearances
                if (denominator == 0) return 0.0
                return (hits + walks + 0.0) / denominator
            }
        val slg: Double get() = if (atBats == 0) 0.0 else totalBases.toDouble() / atBats
        val ops: Double get() = obp + slg
    }

    private val hitOutcomes = setOf(Outcome.SINGLE, Outcome.DOUBLE, Outcome.TRIPLE, Outcome.HOME_RUN)
    private val walkOutcomes = setOf(Outcome.WALK, Outcome.HIT_BY_PITCH)
    private val nonAtBatOutcomes = walkOutcomes + Outcome.SAC_FLY

    private val basesByOutcome = mapOf(
        Outcome.SINGLE to 1,
        Outcome.DOUBLE to 2,
        Outcome.TRIPLE to 3,
        Outcome.HOME_RUN to 4,
    )

    fun compute(pas: List<PlateAppearance>): Map<Long, PlayerStats> =
        pas.filter { it.playerId != null && it.outcome != Outcome.AUTO_OUT && it.outcome != Outcome.MANUAL_OUT }
            .groupBy { it.playerId!! }
            .mapValues { (playerId, rows) ->
                PlayerStats(
                    playerId = playerId,
                    games = rows.map { it.gameId }.distinct().size,
                    plateAppearances = rows.size,
                    atBats = rows.count { it.outcome !in nonAtBatOutcomes },
                    hits = rows.count { it.outcome in hitOutcomes },
                    walks = rows.count { it.outcome in walkOutcomes },
                    runs = rows.count { it.runnerResult == RunnerResult.SCORED },
                    totalBases = rows.sumOf { basesByOutcome[it.outcome] ?: 0 },
                )
            }

    fun format3(value: Double): String {
        val s = "%.3f".format(value)
        return if (s.startsWith("0")) s.substring(1) else s // .375 like a scorebook
    }
}
