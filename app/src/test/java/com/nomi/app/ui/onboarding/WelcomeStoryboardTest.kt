package com.nomi.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The welcome intro is the one sequence the user did not ask for, so its timing is a promise, not
 * a preference. These tests pin the timing so a later tweak cannot quietly turn a two-second
 * intro into a five-second one.
 */
class WelcomeStoryboardTest {

    private val sentenceLength = 46

    private fun run(state: WelcomeStoryboardState): WelcomeStoryboardState {
        var current = state
        repeat(WelcomeStoryboardState.TotalTicks + 10) {
            if (current.isResting) return current
            current = current.tick()
        }
        return current
    }

    @Test
    fun `starts at the beginning of the sentence`() {
        val state = WelcomeStoryboardState(totalCharacters = sentenceLength)

        assertEquals(WelcomeStage.TYPING, state.stage)
        assertEquals(0, state.typedCharacters)
        assertEquals(0f, state.typedFraction, 0.0001f)
    }

    @Test
    fun `typing never runs past the end of the sentence`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)

        repeat(WelcomeStoryboardState.TotalTicks) {
            state = state.tick()
            assertTrue(
                "typed ${state.typedCharacters} of ${state.totalCharacters}",
                state.typedCharacters <= state.totalCharacters,
            )
        }
    }

    @Test
    fun `settles on the finished state within the advertised tick budget`() {
        val state = run(WelcomeStoryboardState(totalCharacters = sentenceLength))

        assertEquals(WelcomeStage.RESTING, state.stage)
        assertEquals(sentenceLength, state.typedCharacters)
        assertEquals(1f, state.typedFraction, 0.0001f)
    }

    @Test
    fun `a skipped sequence is finished immediately`() {
        val typed = WelcomeStoryboardState(totalCharacters = sentenceLength)
            .tick()
            .tick()

        val skipped = typed.skip()

        assertTrue(typed.stage == WelcomeStage.TYPING)
        assertEquals(WelcomeStage.RESTING, skipped.stage)
        assertEquals(sentenceLength, skipped.typedCharacters)
    }

    @Test
    fun `ticking a finished sequence changes nothing`() {
        val resting = WelcomeStoryboardState(totalCharacters = sentenceLength).skip()

        assertEquals(resting, resting.tick())
    }

    @Test
    fun `no row is visible while the sentence is still being written`() {
        val state = WelcomeStoryboardState(totalCharacters = sentenceLength)

        assertFalse(state.isRowVisible(0))
        assertFalse(state.isRowVisible(1))
    }

    @Test
    fun `the second row never arrives before the first`() {
        val state = run(WelcomeStoryboardState(totalCharacters = sentenceLength))

        // Both are shown once resting; the stagger only has to hold while the split is playing.
        assertTrue(state.isRowVisible(0))
        assertTrue(state.isRowVisible(1))

        var current = WelcomeStoryboardState(totalCharacters = sentenceLength)
        while (current.stage == WelcomeStage.TYPING) {
            current = current.tick()
        }
        var firstRowTick = -1
        var secondRowTick = -1
        var tick = 0
        while (current.stage != WelcomeStage.RESTING) {
            if (firstRowTick < 0 && current.isRowVisible(0)) firstRowTick = tick
            if (secondRowTick < 0 && current.isRowVisible(1)) secondRowTick = tick
            current = current.tick()
            tick++
        }
        assertTrue("first row never appeared", firstRowTick >= 0)
        assertTrue("second row never appeared", secondRowTick >= 0)
        assertTrue(
            "second row at $secondRowTick should follow first at $firstRowTick",
            secondRowTick > firstRowTick,
        )
    }

    @Test
    fun `the total is only accounted for once the foods are listed`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)

        while (state.stage == WelcomeStage.TYPING) {
            assertFalse(state.isSourcingVisible())
            state = state.tick()
        }
        assertFalse("total appeared before the split finished", state.isSourcingVisible())

        while (!state.isSourcingVisible()) {
            state = state.tick()
        }
        assertTrue(state.isSourcingVisible())
    }

    @Test
    fun `an empty sentence does not divide by zero`() {
        val state = WelcomeStoryboardState(totalCharacters = 0)

        assertEquals(1f, state.typedFraction, 0.0001f)
    }

    @Test
    fun `the intro stays short enough not to be annoying`() {
        val milliseconds = WelcomeStoryboardState.TotalTicks * WelcomeStoryboardState.TickMillis

        // Six beats now, so the budget is higher than the three-beat version was - but it is still
        // a budget, and this test is what stops a later tweak from quietly stretching it.
        assertTrue(
            "intro runs for ${milliseconds}ms, which is long enough to feel like a hurdle",
            milliseconds <= 4_000L,
        )
    }

    @Test
    fun `the three input methods are named in order, one at a time`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)
        val seen = mutableListOf<Int?>()

        // The first non-null is the first method beat; collect the distinct ones in order.
        while (state.stage != WelcomeStage.PHOTO) {
            state = state.tick()
        }
        while (!state.isResting) {
            val index = state.activeMethodIndex
            if (seen.lastOrNull() != index) seen += index
            state = state.tick()
        }

        assertEquals(listOf(0, 1, 2), seen)
        assertEquals("a method is still named once the sequence rests", null, state.activeMethodIndex)
    }

    @Test
    fun `no method is named before the total is on screen`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)
        var sawSourcing = false

        while (state.activeMethodIndex == null) {
            if (state.isSourcingVisible()) sawSourcing = true
            state = state.tick()
        }

        assertTrue("a method was named before the total appeared", sawSourcing)
        assertTrue(state.isSourcingVisible())
    }

    @Test
    fun `the numbers do not move while the methods are being named`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)
        val rowsWhileTyping = mutableListOf<Boolean>()

        while (!state.isResting) {
            if (state.activeMethodIndex != null) rowsWhileTyping += state.isRowVisible(0)
            state = state.tick()
        }

        assertTrue("no method beat ran", rowsWhileTyping.isNotEmpty())
        assertTrue(
            "a food row disappeared during the method beats",
            rowsWhileTyping.all { it },
        )
    }

    @Test
    fun `a skipped sequence never names a method`() {
        val skipped = WelcomeStoryboardState(totalCharacters = sentenceLength).skip()

        assertEquals(null, skipped.activeMethodIndex)
    }

    @Test
    fun `the method row is still on screen once the sequence rests`() {
        val resting = WelcomeStoryboardState(totalCharacters = sentenceLength).skip()

        // The resting state is the last thing read before the questions start, so all three ways
        // in stay visible with none of them highlighted.
        assertTrue(resting.isMethodRowVisible())
        assertEquals(null, resting.activeMethodIndex)
    }

    @Test
    fun `the method row is hidden while the meal is still being written`() {
        var state = WelcomeStoryboardState(totalCharacters = sentenceLength)

        assertFalse(state.isMethodRowVisible())
        while (state.stage == WelcomeStage.TYPING) {
            state = state.tick()
            assertFalse(state.isMethodRowVisible())
        }
        assertFalse("the row appeared before it had anything to say", state.isMethodRowVisible())
    }

    @Test
    fun `the demo total is the sum of its rows`() {
        val demo = WelcomeDemo(
            sentence = "80 g of blueberries with 200 g of Greek yoghurt",
            items = listOf(
                WelcomeDemoItem("Blueberries", 80, 46),
                WelcomeDemoItem("Greek yoghurt, 2 % fat", 200, 146),
            ),
        )

        assertEquals(192, demo.totalKilocalories)
    }
}
