package com.tbce.calc.sudoku

import kotlin.random.Random

enum class Difficulty(val label: String, val givens: Int) {
    EASY("Easy", 40),
    MEDIUM("Medium", 32),
    HARD("Hard", 26),
}

/** Puzzle generation and solving. Grids are 81 ints, row by row, 0 = blank. */
object Sudoku {
    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** The 20 other cells that share a row, column or box with [i]. */
    val peers: Array<IntArray> = Array(81) { i ->
        (0 until 81).filter { j -> j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i)) }.toIntArray()
    }

    /** A new puzzle with exactly one solution. Returns (puzzle, solution). */
    fun generate(difficulty: Difficulty, rnd: Random = Random.Default): Pair<IntArray, IntArray> {
        val solution = IntArray(81)
        fill(solution, rnd)
        val puzzle = solution.copyOf()
        var givens = 81
        for (i in (0 until 81).shuffled(rnd)) {
            if (givens <= difficulty.givens) break
            val keep = puzzle[i]
            puzzle[i] = 0
            if (countSolutions(puzzle, 2) == 1) givens-- else puzzle[i] = keep
        }
        return puzzle to solution
    }

    /** Fills [grid] with a random complete solution. */
    private fun fill(grid: IntArray, rnd: Random): Boolean {
        val i = grid.indexOf(0)
        if (i < 0) return true
        for (v in (1..9).shuffled(rnd)) {
            if (canPlace(grid, i, v)) {
                grid[i] = v
                if (fill(grid, rnd)) return true
                grid[i] = 0
            }
        }
        return false
    }

    fun canPlace(grid: IntArray, i: Int, v: Int): Boolean = peers[i].none { grid[it] == v }

    /** Counts solutions of [grid], stopping at [limit]. [grid] is left unchanged. */
    fun countSolutions(grid: IntArray, limit: Int): Int {
        val g = grid.copyOf()
        var count = 0
        fun solve() {
            if (count >= limit) return
            // Most-constrained blank first keeps this fast.
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                var used = 0
                for (p in peers[i]) used = used or (1 shl g[p])
                val mask = used.inv() and 0x3FE
                val n = Integer.bitCount(mask)
                if (n < bestCount) { best = i; bestMask = mask; bestCount = n; if (n <= 1) break }
            }
            if (best < 0) { count++; return }
            for (v in 1..9) {
                if (bestMask and (1 shl v) == 0) continue
                g[best] = v
                solve()
                g[best] = 0
                if (count >= limit) return
            }
        }
        solve()
        return count
    }
}
