package com.lockin.focus.core

import com.lockin.focus.core.model.GoalCategory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalDeckTest {

    @Test
    fun `the deck is big enough that a reroll feels different`() {
        assertTrue("expected a real spread, got ${GoalDeck.size()}", GoalDeck.size() >= 25)
    }

    @Test
    fun `every category the brief named is represented`() {
        for (category in GoalCategory.values()) {
            val goals = GoalDeck.ofCategory(category)
            assertTrue("no goals for $category", goals.isNotEmpty())
            assertTrue("$category goals must all declare that category", goals.all { it.category == category })
        }
    }

    @Test
    fun `every goal is concrete enough to start immediately`() {
        val random = Random(7)
        repeat(200) {
            val goal = GoalDeck.draw(random)
            assertTrue("goal is empty: $goal", goal.text.isNotBlank())
            assertTrue("goal has no length: $goal", goal.minutes in 1..60)
        }
    }

    @Test
    fun `rerolling never hands back the same goal`() {
        val random = Random(11)
        repeat(500) {
            val first = GoalDeck.draw(random)
            assertNotEquals(first, GoalDeck.drawOtherThan(first, random))
        }
    }

    @Test
    fun `the draw is reproducible from a seed`() {
        val a = Random(99)
        val b = Random(99)
        repeat(50) { assertEquals(GoalDeck.draw(a), GoalDeck.draw(b)) }
    }

    @Test
    fun `no goal needs the internet, money or an account`() {
        for (category in GoalCategory.values()) {
            for (goal in GoalDeck.ofCategory(category)) {
                val text = goal.text.lowercase()
                listOf("buy", "download", "sign up", "subscribe", "log in", "install").forEach { verb ->
                    assertTrue("\"$verb\" in $goal", !text.contains(verb))
                }
            }
        }
    }
}
