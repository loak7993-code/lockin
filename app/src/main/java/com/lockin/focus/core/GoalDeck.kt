package com.lockin.focus.core

import com.lockin.focus.core.model.Goal
import com.lockin.focus.core.model.GoalCategory
import kotlin.random.Random

/**
 * The replacement tasks handed out when the doom detector catches you.
 *
 * They are deliberately small, concrete and free: no equipment, no money, no
 * account, nothing that can fail because the internet is down. The point is not
 * to improve you, it is to break a groove hard enough that scrolling feels like
 * the boring option — and then to get out of the groove entirely.
 */
object GoalDeck {

    private val DECK: List<Goal> = listOf(
        // Russian
        Goal("Russian maths — 20 minutes of sums, no phone", GoalCategory.RUSSIAN, 20),
        Goal("Russian maths — write out the times tables from 6 to 12", GoalCategory.RUSSIAN, 10),
        Goal("Russian maths — 15 minutes of times tables, then stop", GoalCategory.RUSSIAN, 15),
        Goal("Russian — learn 10 new words, write them out twice", GoalCategory.RUSSIAN, 10),
        Goal("Russian — 20 Russian words, then 5 sentences using them", GoalCategory.RUSSIAN, 20),

        // English
        Goal("English — 15 vocabulary words you don't know yet", GoalCategory.ENGLISH, 15),
        Goal("English — read two pages of anything in English, out loud", GoalCategory.ENGLISH, 15),
        Goal("English — write a paragraph about what you did today", GoalCategory.ENGLISH, 10),
        Goal("English — 20 flashcards, then stop", GoalCategory.ENGLISH, 15),
        Goal("English — watch one video in English with subtitles off", GoalCategory.ENGLISH, 15),

        // Homework
        Goal("Homework — open it and do the first question. Only the first.", GoalCategory.HOMEWORK, 15),
        Goal("Homework — 25 minutes of whatever is due, phone in another room", GoalCategory.HOMEWORK, 25),
        Goal("Homework — read the brief twice and bullet the answers", GoalCategory.HOMEWORK, 20),
        Goal("Homework — the thing you have been putting off since Tuesday", GoalCategory.HOMEWORK, 25),
        Goal("Homework — 20 minutes, then list what is left", GoalCategory.HOMEWORK, 20),

        // Study
        Goal("Study — 20 minutes on the thing you locked in for", GoalCategory.STUDY, 20),
        Goal("Study — reread the last page you read. Properly, not skim.", GoalCategory.STUDY, 10),
        Goal("Study — 25 minutes, no breaks, no checking anything", GoalCategory.STUDY, 25),
        Goal("Study — make flashcards for the thing you keep forgetting", GoalCategory.STUDY, 20),
        Goal("Study — 15 minutes of past questions, timed", GoalCategory.STUDY, 15),

        // Move
        Goal("Go outside. Ten minutes. No phone in your pocket.", GoalCategory.MOVE, 10),
        Goal("Touch grass — go outside, stand somewhere green for five minutes", GoalCategory.MOVE, 5),
        Goal("Walk to the end of the road and back", GoalCategory.MOVE, 15),
        Goal("Go outside and look at the sky for two full minutes", GoalCategory.MOVE, 2),
        Goal("Walk around the block, phone stays in your pocket", GoalCategory.MOVE, 20),

        // Body
        Goal("30 push-ups, or as many as you can", GoalCategory.BODY, 5),
        Goal("Ten minutes of stretching. Shoulders and back, you know where.", GoalCategory.BODY, 10),
        Goal("20 sit-ups and 30 seconds of holding your breath", GoalCategory.BODY, 5),
        Goal("Shower. Cold water if you are feeling it.", GoalCategory.BODY, 15),

        // Chores
        Goal("Make your bed and clear one surface", GoalCategory.CHORES, 5),
        Goal("Do the washing up, all of it", GoalCategory.CHORES, 15),
        Goal("Tidy one room. One. Then stop.", GoalCategory.CHORES, 20),
        Goal("Clear the table and wipe it down", GoalCategory.CHORES, 10),

        // Offline
        Goal("Put the phone in another room for 20 minutes", GoalCategory.OFFLINE, 20),
        Goal("No screens until the timer finishes. Books, paper, talking.", GoalCategory.OFFLINE, 20),
        Goal("Drink a full glass of water, then do 15 more minutes of anything useful", GoalCategory.OFFLINE, 15),
    )

    fun size(): Int = DECK.size

    fun draw(random: Random = Random.Default): Goal = DECK[random.nextInt(DECK.size)]

    /**
     * A different goal from the same pool, guaranteed not to be the one just
     * dismissed. Falling back to the original only if the deck is somehow one long.
     */
    fun drawOtherThan(current: Goal, random: Random = Random.Default): Goal {
        if (DECK.size < 2) return current
        var next = draw(random)
        var guard = 0
        while (next == current && guard < 8) {
            next = draw(random)
            guard++
        }
        return next
    }

    fun ofCategory(category: GoalCategory): List<Goal> = DECK.filter { it.category == category }
}
