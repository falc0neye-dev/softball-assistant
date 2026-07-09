package com.keithfalcon.softball.logic

import com.keithfalcon.softball.data.LineupType
import com.keithfalcon.softball.logic.LineupEngine.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupEngineTest {

    // Player ids used across tests (names from spec §5.2 example)
    private val keith = 1L
    private val jon = 2L
    private val steven = 3L
    private val mike = 4L
    private val robert = 5L
    private val ely = 10L
    private val erin = 11L

    private fun dynamic(
        males: List<Long> = listOf(keith, jon, steven, mike, robert),
        females: List<Long> = listOf(ely, erin),
        ratioMale: Int = 3,
        ratioFemale: Int = 1,
        autoOut: Boolean = false,
    ) = LineupEngine.Config(
        type = LineupType.DYNAMIC,
        maleQueue = males,
        femaleQueue = females,
        ratioMale = ratioMale,
        ratioFemale = ratioFemale,
        autoOutOnEmptyFemaleSlot = autoOut,
    )

    private fun playerIds(slots: List<Slot>) =
        slots.map { (it as Slot.Batter).playerId }

    @Test
    fun `static lineup cycles in fixed order`() {
        val config = LineupEngine.Config(
            type = LineupType.STATIC,
            battingQueue = listOf(keith, jon, ely),
        )
        val slots = LineupEngine.preview(config, emptyList(), 7)
        assertEquals(
            listOf(keith, jon, ely, keith, jon, ely, keith),
            playerIds(slots),
        )
    }

    @Test
    fun `dynamic 3-1 matches spec section 5_2 example exactly`() {
        // Males (Keith, Jon, Steven, Mike, Robert), Females (Ely, Erin) →
        // Keith, Jon, Steven, Ely, Mike, Robert, Keith, Erin, Jon, Steven, Mike, Ely
        val slots = LineupEngine.preview(dynamic(), emptyList(), 12)
        assertEquals(
            listOf(keith, jon, steven, ely, mike, robert, keith, erin, jon, steven, mike, ely),
            playerIds(slots),
        )
        // Female slots flagged for UI highlighting
        assertEquals(
            listOf(3, 7, 11),
            slots.withIndex().filter { (it.value as Slot.Batter).isFemaleSlot }.map { it.index },
        )
    }

    @Test
    fun `zero females falls back to males-only cycle`() {
        val slots = LineupEngine.preview(dynamic(females = emptyList()), emptyList(), 8)
        assertEquals(
            listOf(keith, jon, steven, mike, robert, keith, jon, steven),
            playerIds(slots),
        )
    }

    @Test
    fun `zero females with auto-out rule records automatic outs on female slots`() {
        val slots = LineupEngine.preview(
            dynamic(females = emptyList(), autoOut = true),
            emptyList(),
            8,
        )
        assertEquals(Slot.AutoOut, slots[3])
        assertEquals(Slot.AutoOut, slots[7])
        assertEquals(
            listOf(keith, jon, steven, mike, robert, keith),
            playerIds(slots.filterIsInstance<Slot.Batter>()),
        )
    }

    @Test
    fun `zero males yields females-only cycle`() {
        val slots = LineupEngine.preview(dynamic(males = emptyList()), emptyList(), 5)
        assertEquals(listOf(ely, erin, ely, erin, ely), playerIds(slots))
    }

    @Test
    fun `one female cycles alone through every female slot`() {
        val slots = LineupEngine.preview(dynamic(females = listOf(ely)), emptyList(), 12)
        assertEquals(
            listOf(keith, jon, steven, ely, mike, robert, keith, ely, jon, steven, mike, ely),
            playerIds(slots),
        )
    }

    @Test
    fun `both queues empty yields no slot`() {
        assertNull(LineupEngine.nextSlot(dynamic(males = emptyList(), females = emptyList()), emptyList()))
        assertTrue(LineupEngine.preview(dynamic(males = emptyList(), females = emptyList()), emptyList(), 5).isEmpty())
    }

    @Test
    fun `mid-game removal skips player going forward, history preserved`() {
        // Steven batted 3rd, then leaves. Queue no longer contains him.
        val history = listOf<Long?>(keith, jon, steven, ely)
        val withoutSteven = dynamic(males = listOf(keith, jon, mike, robert))
        // Next male after Steven (removed): last in-queue male in history is Jon → Mike
        val slots = LineupEngine.preview(withoutSteven, history, 4)
        assertEquals(listOf(mike, robert, keith, erin), playerIds(slots))
    }

    @Test
    fun `late arrival joins the back of their queue`() {
        val newGuy = 6L
        val history = listOf<Long?>(keith, jon, steven, ely, mike)
        val withNewGuy = dynamic(males = listOf(keith, jon, steven, mike, robert, newGuy))
        // Remaining male order after Mike: Robert, NewGuy, then wrap to Keith
        val slots = LineupEngine.preview(withNewGuy, history, 4)
        assertEquals(listOf(robert, newGuy, erin, keith), playerIds(slots))
    }

    @Test
    fun `configurable ratio 2-1 alternates correctly`() {
        val slots = LineupEngine.preview(dynamic(ratioMale = 2, ratioFemale = 1), emptyList(), 9)
        assertEquals(
            listOf(keith, jon, ely, steven, mike, erin, robert, keith, ely),
            playerIds(slots),
        )
    }

    @Test
    fun `nextSlot resumes correctly from persisted history after process death`() {
        // Same config, history rebuilt from DB — generator has no hidden state to lose.
        val history = listOf<Long?>(keith, jon, steven, ely, mike, robert, keith)
        val next = LineupEngine.nextSlot(dynamic(), history) as Slot.Batter
        assertEquals(erin, next.playerId)
        assertTrue(next.isFemaleSlot)
    }

    @Test
    fun `auto-out consumes the female slot so pattern stays aligned`() {
        // History contains an auto-out (null) at the female slot position.
        val history = listOf<Long?>(keith, jon, steven, null)
        val next = LineupEngine.nextSlot(dynamic(females = emptyList(), autoOut = true), history) as Slot.Batter
        assertEquals(mike, next.playerId)
    }

    @Test
    fun `female arriving after males-only fallback restores female slots`() {
        // No females for the first cycle (fallback consumed the female slot at pos 3),
        // then Ely arrives. Position 7 is the next female slot.
        val history = listOf<Long?>(keith, jon, steven, mike, robert, keith, jon)
        val next = LineupEngine.nextSlot(dynamic(females = listOf(ely)), history) as Slot.Batter
        assertEquals(ely, next.playerId)
        assertTrue(next.isFemaleSlot)
    }
}
