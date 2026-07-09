package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.LineupType

/**
 * Pure lineup generation (spec §5). No Android or DB dependencies.
 *
 * The engine never materializes a fixed batting list; callers ask for the *next* slot
 * given the current queues and the history of batting slots already consumed. That makes
 * mid-game edits (late arrival, no-show removal, reorder) work naturally: the queues
 * change, and the next slot is derived from whoever batted last among players still in
 * the relevant queue.
 */
object LineupEngine {

    sealed interface Slot {
        /** A real batter. [isFemaleSlot] marks slots produced by the female part of the ratio. */
        data class Batter(val playerId: Long, val isFemaleSlot: Boolean) : Slot

        /** Co-ed auto-out rule (spec §5.2): a female slot came up with no female available. */
        data object AutoOut : Slot
    }

    data class Config(
        val type: LineupType,
        /** STATIC: the single ordered batting list. */
        val battingQueue: List<Long> = emptyList(),
        /** DYNAMIC: independently ordered + cycling queues. */
        val maleQueue: List<Long> = emptyList(),
        val femaleQueue: List<Long> = emptyList(),
        val ratioMale: Int = 3,
        val ratioFemale: Int = 1,
        val autoOutOnEmptyFemaleSlot: Boolean = false,
    )

    /**
     * The next batting slot, or null when no one can bat (all relevant queues empty).
     *
     * [history] is the ordered list of batting slots already consumed in the game:
     * one entry per plate appearance that occupied a lineup slot (playerId, or null for
     * an auto-out). Manual "+1 out" rows must NOT be included — they don't consume a slot.
     */
    fun nextSlot(config: Config, history: List<Long?>): Slot? = when (config.type) {
        LineupType.STATIC -> {
            val queue = config.battingQueue
            if (queue.isEmpty()) null
            else Slot.Batter(nextInQueue(queue, history), isFemaleSlot = false)
        }
        LineupType.DYNAMIC -> nextDynamicSlot(config, history)
    }

    /** Simulate the next [count] slots without consuming them (spec §5.3 preview). */
    fun preview(config: Config, history: List<Long?>, count: Int): List<Slot> {
        val simulated = history.toMutableList()
        val out = mutableListOf<Slot>()
        repeat(count) {
            val slot = nextSlot(config, simulated) ?: return out
            out += slot
            simulated += (slot as? Slot.Batter)?.playerId
        }
        return out
    }

    private fun nextDynamicSlot(config: Config, history: List<Long?>): Slot? {
        val males = config.maleQueue
        val females = config.femaleQueue
        if (males.isEmpty() && females.isEmpty()) return null

        val femaleSlot = isFemaleSlotAt(history.size, config.ratioMale, config.ratioFemale)
        return if (femaleSlot) {
            when {
                females.isNotEmpty() ->
                    Slot.Batter(nextInQueue(females, history), isFemaleSlot = true)
                config.autoOutOnEmptyFemaleSlot -> Slot.AutoOut
                // Fall back to males-only cycle; the slot is still consumed so the
                // pattern realigns if a female arrives late.
                else -> Slot.Batter(nextInQueue(males, history), isFemaleSlot = false)
            }
        } else {
            when {
                males.isNotEmpty() -> Slot.Batter(nextInQueue(males, history), isFemaleSlot = false)
                else -> Slot.Batter(nextInQueue(females, history), isFemaleSlot = true)
            }
        }
    }

    private fun isFemaleSlotAt(position: Int, ratioMale: Int, ratioFemale: Int): Boolean {
        val cycle = (ratioMale + ratioFemale).coerceAtLeast(1)
        return position % cycle >= ratioMale
    }

    /**
     * Whoever follows the most recent batter (in [history]) that is still in [queue];
     * queue cycles when exhausted. Players removed mid-game simply aren't found, so the
     * rotation continues from the last present member — past at-bats are untouched.
     */
    private fun nextInQueue(queue: List<Long>, history: List<Long?>): Long {
        val lastIndex = history.lastOrNull { it != null && it in queue }
            ?.let { queue.indexOf(it) }
            ?: return queue.first()
        return queue[(lastIndex + 1) % queue.size]
    }
}
