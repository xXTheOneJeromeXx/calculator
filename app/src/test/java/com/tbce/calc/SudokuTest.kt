package com.tbce.calc

import com.tbce.calc.sudoku.Difficulty
import com.tbce.calc.sudoku.Game
import com.tbce.calc.sudoku.Sudoku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuTest {
    private fun validSolution(s: IntArray) = (0 until 81).all { i -> s[i] in 1..9 && Sudoku.peers[i].none { s[it] == s[i] } }

    @Test fun generatedPuzzlesHaveOneValidSolution() {
        for (d in Difficulty.entries) {
            repeat(3) { seed ->
                val (p, s) = Sudoku.generate(d, Random(seed))
                assertTrue(validSolution(s))
                assertTrue("givens match the solution", (0 until 81).all { p[it] == 0 || p[it] == s[it] })
                assertEquals("unique", 1, Sudoku.countSolutions(p, 2))
                assertTrue("not too many givens for $d", p.count { it != 0 } <= d.givens + 6)
            }
        }
    }

    @Test fun peersAreTwentyCells() {
        assertTrue(Sudoku.peers.all { it.size == 20 })
    }

    @Test fun movesNotesUndoAndSolve() {
        val g = Game.new(Difficulty.EASY, Random(7))
        val blank = (0 until 81).first { !g.isGiven(it) }
        val given = (0 until 81).first { g.isGiven(it) }
        assertTrue("givens can't change", g.place(given, 1) === g)

        val noted = g.toggleNote(blank, 3).toggleNote(blank, 5)
        assertEquals((1 shl 3) or (1 shl 5), noted.notes[blank])
        val placed = noted.place(blank, 4)
        assertEquals(4, placed.cells[blank])
        assertEquals("placing clears the cell's marks", 0, placed.notes[blank])
        assertEquals("placing the same digit again clears it", 0, placed.place(blank, 4).cells[blank])
        assertEquals(0, placed.erase(blank).cells[blank])
        assertEquals(g.solution[blank], g.hint(blank).cells[blank])

        var solved = g
        for (i in 0 until 81) solved = solved.hint(i)
        assertTrue(solved.solved)
        assertFalse(g.solved)
    }

    @Test fun placingClearsThatDigitFromPeerMarks() {
        val g = Game.new(Difficulty.EASY, Random(3))
        val blanks = (0 until 81).filter { !g.isGiven(it) }
        val a = blanks.first()
        val b = blanks.first { it != a && it in Sudoku.peers[a] }
        val after = g.toggleNote(b, 6).place(a, 6)
        assertEquals(0, after.notes[b] and (1 shl 6))
    }

    @Test fun saveAndResumeRoundTrips() {
        val g = Game.new(Difficulty.MEDIUM, Random(1))
        val blank = (0 until 81).first { !g.isGiven(it) }
        val played = g.place(blank, 9).toggleNote((0 until 81).last { !g.isGiven(it) }, 2).withElapsed(65_000)
        val back = Game.fromJson(played.toJson())
        assertNotNull(back)
        back!!
        assertTrue(back.puzzle.contentEquals(played.puzzle))
        assertTrue(back.solution.contentEquals(played.solution))
        assertTrue(back.cells.contentEquals(played.cells))
        assertTrue(back.notes.contentEquals(played.notes))
        assertEquals(Difficulty.MEDIUM, back.difficulty)
        assertEquals(65_000, back.elapsedMs)
    }

    @Test fun damagedSavesAreIgnored() {
        assertNull(Game.fromJson(null))
        assertNull(Game.fromJson("not json"))
        assertNull(Game.fromJson("{\"p\":\"123\"}"))
    }
}
