package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.RunnerResult
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsEngineTest {

    private var id = 1L
    private fun pa(playerId: Long?, outcome: Outcome, runner: RunnerResult = RunnerResult.NONE, gameId: Long = 1) =
        PlateAppearance(id = id++, gameId = gameId, sequence = id.toInt(), playerId = playerId, outcome = outcome, runnerResult = runner)

    @Test
    fun `computes AB, H, BB, AVG, runs across games`() {
        val pas = listOf(
            pa(1, Outcome.SINGLE, RunnerResult.SCORED, gameId = 1),
            pa(1, Outcome.WALK, RunnerResult.ON_BASE, gameId = 1),
            pa(1, Outcome.STRIKEOUT, gameId = 1),
            pa(1, Outcome.HOME_RUN, RunnerResult.SCORED, gameId = 2),
            pa(1, Outcome.SAC_FLY, gameId = 2),
            pa(2, Outcome.OUT, gameId = 1),
            pa(null, Outcome.AUTO_OUT, gameId = 1), // ignored
        )
        val stats = StatsEngine.compute(pas)

        val p1 = stats.getValue(1)
        assertEquals(2, p1.games)
        assertEquals(5, p1.plateAppearances)
        assertEquals(3, p1.atBats) // walk + sac fly excluded
        assertEquals(2, p1.hits)
        assertEquals(1, p1.walks)
        assertEquals(2, p1.runs)
        assertEquals(".667", StatsEngine.format3(p1.avg))

        val p2 = stats.getValue(2)
        assertEquals(1, p2.atBats)
        assertEquals(0, p2.hits)
        assertEquals(".000", StatsEngine.format3(p2.avg))
    }

    @Test
    fun `computes SLG and OPS from total bases`() {
        val pas = listOf(
            pa(1, Outcome.SINGLE),          // 1 base
            pa(1, Outcome.DOUBLE),          // 2 bases
            pa(1, Outcome.HOME_RUN, RunnerResult.SCORED), // 4 bases
            pa(1, Outcome.STRIKEOUT),
            pa(1, Outcome.WALK),            // no AB, no bases
        )
        val p1 = StatsEngine.compute(pas).getValue(1)
        assertEquals(7, p1.totalBases)
        assertEquals(4, p1.atBats)
        // SLG = 7/4 = 1.75; OBP = (3 hits + 1 walk) / 5 PA = 0.8; OPS = 2.55
        assertEquals(1.75, p1.slg, 1e-9)
        assertEquals(0.8, p1.obp, 1e-9)
        assertEquals(2.55, p1.ops, 1e-9)
        assertEquals("2.550", StatsEngine.format3(p1.ops))
    }

    @Test
    fun `OPS is zero with no at-bats or plate appearances`() {
        val p = StatsEngine.compute(listOf(pa(1, Outcome.WALK))).getValue(1)
        assertEquals(0.0, p.slg, 1e-9) // no at-bats
        assertEquals(1.0, p.ops, 1e-9) // pure OBP
        assertEquals(0.0, StatsEngine.compute(emptyList()).size.toDouble(), 1e-9)
    }
}
