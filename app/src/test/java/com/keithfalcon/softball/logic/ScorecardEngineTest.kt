package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.RunnerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorecardEngineTest {

    private var nextId = 1L
    private fun pa(
        outcome: Outcome,
        runner: RunnerResult = if (outcome.reachedBase) RunnerResult.ON_BASE else RunnerResult.NONE,
        playerId: Long? = 1L,
    ) = PlateAppearance(
        id = nextId++,
        gameId = 1L,
        sequence = nextId.toInt(),
        playerId = playerId,
        outcome = outcome,
        runnerResult = runner,
    )

    @Test
    fun `three batter outs end the inning`() {
        val state = ScorecardEngine.derive(
            listOf(
                pa(Outcome.STRIKEOUT),
                pa(Outcome.SINGLE, RunnerResult.SCORED),
                pa(Outcome.FLYOUT),
                pa(Outcome.GROUNDOUT),
                pa(Outcome.SINGLE),
            )
        )
        assertTrue(state.rows[3].endsInning)
        assertEquals(1, state.rows[3].runsInEndedInning)
        assertEquals(2, state.rows[4].inning)
        assertEquals(2, state.currentInning)
        assertEquals(0, state.outsInCurrentInning)
        assertEquals(mapOf(1 to 1), state.runsByInning)
    }

    @Test
    fun `runner outs count toward the three-out total`() {
        val state = ScorecardEngine.derive(
            listOf(
                pa(Outcome.SINGLE, RunnerResult.OUT), // reached, thrown out on bases
                pa(Outcome.STRIKEOUT),
                pa(Outcome.DOUBLE, RunnerResult.OUT),
            )
        )
        assertTrue(state.rows[2].endsInning)
        assertEquals(2, state.currentInning)
    }

    @Test
    fun `manual and auto outs are playerless rows that advance the inning`() {
        val state = ScorecardEngine.derive(
            listOf(
                pa(Outcome.MANUAL_OUT, RunnerResult.NONE, playerId = null),
                pa(Outcome.AUTO_OUT, RunnerResult.NONE, playerId = null),
                pa(Outcome.OUT),
            )
        )
        assertTrue(state.rows[2].endsInning)
        assertEquals(2, state.currentInning)
    }

    @Test
    fun `on-base runners display as left-on-base once the inning completes`() {
        val state = ScorecardEngine.derive(
            listOf(
                pa(Outcome.WALK), // still ON_BASE when inning ends
                pa(Outcome.STRIKEOUT),
                pa(Outcome.FLYOUT),
                pa(Outcome.GROUNDOUT),
                pa(Outcome.SINGLE), // inning 2, still on base
            )
        )
        assertEquals(RunnerResult.LEFT_ON_BASE, state.rows[0].displayRunnerResult)
        assertEquals(RunnerResult.ON_BASE, state.rows[0].pa.runnerResult) // storage untouched
        assertEquals(RunnerResult.ON_BASE, state.rows[4].displayRunnerResult) // current inning
    }

    @Test
    fun `deleting an out reopens the inning and LOB reverts to on-base`() {
        val full = listOf(
            pa(Outcome.WALK),
            pa(Outcome.STRIKEOUT),
            pa(Outcome.FLYOUT),
            pa(Outcome.GROUNDOUT),
        )
        val before = ScorecardEngine.derive(full)
        assertEquals(RunnerResult.LEFT_ON_BASE, before.rows[0].displayRunnerResult)

        val after = ScorecardEngine.derive(full.dropLast(1))
        assertEquals(RunnerResult.ON_BASE, after.rows[0].displayRunnerResult)
        assertEquals(1, after.currentInning)
        assertEquals(2, after.outsInCurrentInning)
        assertFalse(after.rows.any { it.endsInning })
    }

    @Test
    fun `inserting an out mid-sequence shifts inning boundaries`() {
        val i1 = listOf(pa(Outcome.OUT), pa(Outcome.OUT), pa(Outcome.SINGLE, RunnerResult.SCORED), pa(Outcome.OUT))
        val i2 = listOf(pa(Outcome.SINGLE, RunnerResult.SCORED))
        val state = ScorecardEngine.derive(i1 + i2)
        assertEquals(mapOf(1 to 1, 2 to 1), state.runsByInning)

        // Forgot an out before the run-scoring single: now the single is in inning 2.
        val forgotten = pa(Outcome.FLYOUT)
        val edited = ScorecardEngine.derive(
            ScorecardEngine.resequence(i1.take(2) + forgotten + i1.drop(2) + i2)
        )
        assertEquals(mapOf(2 to 2), edited.runsByInning)
        assertEquals(2, edited.rows[3].inning) // the scoring single moved to inning 2
        assertEquals(2, edited.totalRuns)
    }

    @Test
    fun `runs tally by inning for the line score`() {
        val state = ScorecardEngine.derive(
            listOf(
                pa(Outcome.HOME_RUN, RunnerResult.SCORED),
                pa(Outcome.SINGLE, RunnerResult.SCORED),
                pa(Outcome.OUT), pa(Outcome.OUT), pa(Outcome.OUT),
                pa(Outcome.DOUBLE, RunnerResult.SCORED),
                pa(Outcome.OUT), pa(Outcome.OUT), pa(Outcome.OUT),
                pa(Outcome.TRIPLE, RunnerResult.SCORED),
            )
        )
        assertEquals(mapOf(1 to 2, 2 to 1, 3 to 1), state.runsByInning)
        assertEquals(4, state.totalRuns)
        assertEquals(3, state.currentInning)
    }

    @Test
    fun `resequence renumbers densely from one`() {
        val list = listOf(
            pa(Outcome.SINGLE).copy(sequence = 5),
            pa(Outcome.OUT).copy(sequence = 9),
            pa(Outcome.WALK).copy(sequence = 2),
        )
        assertEquals(listOf(1, 2, 3), ScorecardEngine.resequence(list).map { it.sequence })
    }

    @Test
    fun `empty scorecard is inning 1 with no outs`() {
        val state = ScorecardEngine.derive(emptyList())
        assertEquals(1, state.currentInning)
        assertEquals(0, state.outsInCurrentInning)
        assertEquals(0, state.totalRuns)
    }
}
