package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.RunnerResult

/**
 * Derives all scorecard structure — inning boundaries, out counts, run totals — from the
 * ordered plate-appearance sequence. Nothing here is stored: after any edit the caller
 * re-runs [derive] and the separators/totals can never disagree with the rows (spec §12.2).
 */
object ScorecardEngine {

    data class Row(
        val pa: PlateAppearance,
        val inning: Int,
        /** Outs in this inning after this row (0..3). */
        val outsAfter: Int,
        /** True when this row records the 3rd out — a separator renders after it. */
        val endsInning: Boolean,
        /**
         * What the diamond glyph should show. ON_BASE rows in a completed inning display
         * as LEFT_ON_BASE without mutating stored data, so reopening the inning via an
         * edit restores them automatically.
         */
        val displayRunnerResult: RunnerResult,
        /** Runs scored in the inning this row closed (only set when [endsInning]). */
        val runsInEndedInning: Int = 0,
    )

    data class State(
        val rows: List<Row>,
        val currentInning: Int,
        val outsInCurrentInning: Int,
        /** Our runs per inning, 1-based inning → runs. */
        val runsByInning: Map<Int, Int>,
        val totalRuns: Int,
    )

    /** Outs contributed by one plate appearance: batter out and/or runner out on the bases. */
    fun outsFor(pa: PlateAppearance): Int {
        var outs = 0
        if (!pa.outcome.reachedBase) outs++
        if (pa.runnerResult == RunnerResult.OUT) outs++
        return outs
    }

    fun derive(pas: List<PlateAppearance>): State {
        val rows = mutableListOf<Row>()
        val runsByInning = mutableMapOf<Int, Int>()
        var inning = 1
        var outs = 0

        for (pa in pas) {
            if (pa.runnerResult == RunnerResult.SCORED) {
                runsByInning[inning] = (runsByInning[inning] ?: 0) + 1
            }
            outs += outsFor(pa)
            val ends = outs >= 3
            rows += Row(
                pa = pa,
                inning = inning,
                outsAfter = outs.coerceAtMost(3),
                endsInning = ends,
                displayRunnerResult = pa.runnerResult,
                runsInEndedInning = if (ends) runsByInning[inning] ?: 0 else 0,
            )
            if (ends) {
                inning++
                outs = 0
            }
        }

        // Rows still "on base" in completed innings display as left-on-base.
        val finalized = rows.map { row ->
            if (row.inning < inning && row.displayRunnerResult == RunnerResult.ON_BASE) {
                row.copy(displayRunnerResult = RunnerResult.LEFT_ON_BASE)
            } else row
        }

        return State(
            rows = finalized,
            currentInning = inning,
            outsInCurrentInning = outs,
            runsByInning = runsByInning,
            totalRuns = runsByInning.values.sum(),
        )
    }

    /** Re-number a full sequence 1..n after insert/delete/reorder edits. */
    fun resequence(pas: List<PlateAppearance>): List<PlateAppearance> =
        pas.mapIndexed { i, pa -> if (pa.sequence == i + 1) pa else pa.copy(sequence = i + 1) }
}
