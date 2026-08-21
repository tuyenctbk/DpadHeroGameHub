package com.tdpham.games

import com.tdpham.games.trivia.TriviaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewGamesUnitTest {

    @Test
    fun testTriviaDatabase_CategoriesAndQuestions() {
        val allQuestions = TriviaDatabase.getQuestionsForCategory("ALL")
        assertTrue("All category should have multiple questions", allQuestions.size >= 25)

        val gamingQuestions = TriviaDatabase.getQuestionsForCategory("Gaming")
        assertTrue("Gaming category should have questions", gamingQuestions.isNotEmpty())
        assertTrue("Gaming questions should belong to Gaming category", gamingQuestions.all { it.category.contains("Gaming", ignoreCase = true) })

        // Verify valid question structure
        for (q in allQuestions) {
            assertEquals("Each question must have 4 options", 4, q.options.size)
            assertTrue("Correct index must be within 0..3", q.correctIndex in 0..3)
            assertFalse("Question text cannot be empty", q.question.isBlank())
        }
    }

    @Test
    fun testConnectFour_WinDetectionLogic() {
        val rows = 6
        val cols = 7
        val board = Array(rows) { IntArray(cols) { 0 } }

        fun checkWin(p: Int): Boolean {
            // Horizontal
            for (r in 0 until rows) {
                for (c in 0..cols - 4) {
                    if (board[r][c] == p && board[r][c + 1] == p && board[r][c + 2] == p && board[r][c + 3] == p) return true
                }
            }
            // Vertical
            for (r in 0..rows - 4) {
                for (c in 0 until cols) {
                    if (board[r][c] == p && board[r + 1][c] == p && board[r + 2][c] == p && board[r + 3][c] == p) return true
                }
            }
            // Diagonal \
            for (r in 0..rows - 4) {
                for (c in 0..cols - 4) {
                    if (board[r][c] == p && board[r + 1][c + 1] == p && board[r + 2][c + 2] == p && board[r + 3][c + 3] == p) return true
                }
            }
            // Diagonal /
            for (r in 3 until rows) {
                for (c in 0..cols - 4) {
                    if (board[r][c] == p && board[r - 1][c + 1] == p && board[r - 2][c + 2] == p && board[r - 3][c + 3] == p) return true
                }
            }
            return false
        }

        // Horizontal 4-in-a-row
        board[5][0] = 1
        board[5][1] = 1
        board[5][2] = 1
        board[5][3] = 1
        assertTrue("Horizontal 4-in-a-row should be detected as a win", checkWin(1))
        assertFalse("Player 2 should not have won", checkWin(2))

        // Reset
        for (r in 0 until rows) {
            for (c in 0 until cols) board[r][c] = 0
        }

        // Diagonal / 4-in-a-row
        board[5][0] = 2
        board[4][1] = 2
        board[3][2] = 2
        board[2][3] = 2
        assertTrue("Diagonal / 4-in-a-row should be detected as a win", checkWin(2))
    }

    @Test
    fun testBlackjack_HandValueCalculations() {
        data class Card(val suit: Int, val rank: Int) {
            val value: Int get() = when (rank) {
                1 -> 11 // Ace
                in 11..13 -> 10 // Face card
                else -> rank
            }
        }

        fun calculateScore(hand: List<Card>): Int {
            var total = 0
            var aces = 0
            for (c in hand) {
                total += c.value
                if (c.rank == 1) aces++
            }
            while (total > 21 && aces > 0) {
                total -= 10
                aces--
            }
            return total
        }

        // Natural Blackjack: Ace + King = 21
        val blackjackHand = listOf(Card(0, 1), Card(1, 13))
        assertEquals(21, calculateScore(blackjackHand))

        // Soft 17: Ace + 6 = 17
        val soft17 = listOf(Card(0, 1), Card(2, 6))
        assertEquals(17, calculateScore(soft17))

        // Three Aces + Nine = 11 + 1 + 1 + 9 = 22 -> 1 + 1 + 1 + 9 = 12
        val multiAces = listOf(Card(0, 1), Card(1, 1), Card(2, 1), Card(3, 9))
        assertEquals(12, calculateScore(multiAces))

        // Bust: 10 + 6 + 7 = 23
        val bustHand = listOf(Card(0, 10), Card(1, 6), Card(2, 7))
        assertEquals(23, calculateScore(bustHand))
    }
}
