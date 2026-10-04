package com.tbce.calc.sudoku

import org.json.JSONArray
import org.json.JSONObject

/**
 * One Sudoku game. Immutable: every move returns a new Game, which makes undo a simple stack.
 * [notes] holds pencil marks as a bitmask per cell (bit v set = v pencilled in).
 */
class Game(
    val puzzle: IntArray,
    val solution: IntArray,
    val cells: IntArray,
    val notes: IntArray,
    val difficulty: Difficulty,
    val elapsedMs: Long = 0,
) {
    fun isGiven(i: Int) = puzzle[i] != 0

    val solved: Boolean get() = cells.contentEquals(solution)

    /** True if cell [i]'s value repeats in its row, column or box. */
    fun conflict(i: Int): Boolean {
        val v = cells[i]
        return v != 0 && Sudoku.peers[i].any { cells[it] == v }
    }

    /** How many of [v] are on the board (a digit with all 9 placed is done). */
    fun count(v: Int) = cells.count { it == v }

    /** Places [v] in [i] (placing the same digit again clears it) and clears [v] from peers' pencil marks. */
    fun place(i: Int, v: Int): Game {
        if (isGiven(i)) return this
        val c = cells.copyOf()
        val n = notes.copyOf()
        c[i] = if (c[i] == v) 0 else v
        n[i] = 0
        if (c[i] != 0) for (p in Sudoku.peers[i]) n[p] = n[p] and (1 shl v).inv()
        return Game(puzzle, solution, c, n, difficulty, elapsedMs)
    }

    fun toggleNote(i: Int, v: Int): Game {
        if (isGiven(i) || cells[i] != 0) return this
        val n = notes.copyOf()
        n[i] = n[i] xor (1 shl v)
        return Game(puzzle, solution, cells, n, difficulty, elapsedMs)
    }

    fun erase(i: Int): Game {
        if (isGiven(i) || (cells[i] == 0 && notes[i] == 0)) return this
        val c = cells.copyOf()
        val n = notes.copyOf()
        c[i] = 0
        n[i] = 0
        return Game(puzzle, solution, c, n, difficulty, elapsedMs)
    }

    /** Fills [i] with its answer. */
    fun hint(i: Int): Game = if (isGiven(i) || cells[i] == solution[i]) this else place(i, solution[i])

    fun withElapsed(ms: Long) = Game(puzzle, solution, cells, notes, difficulty, ms)

    fun toJson(): String = JSONObject()
        .put("p", digits(puzzle)).put("s", digits(solution)).put("c", digits(cells))
        .put("n", JSONArray(notes.toList())).put("d", difficulty.name).put("t", elapsedMs)
        .toString()

    companion object {
        fun new(difficulty: Difficulty, random: kotlin.random.Random = kotlin.random.Random.Default): Game {
            val (p, s) = Sudoku.generate(difficulty, random)
            return Game(p, s, p.copyOf(), IntArray(81), difficulty)
        }

        /** Null if [json] is missing or damaged. */
        fun fromJson(json: String?): Game? = try {
            val o = JSONObject(json ?: return null)
            val n = o.getJSONArray("n")
            val g = Game(
                parse(o.getString("p")), parse(o.getString("s")), parse(o.getString("c")),
                IntArray(81) { n.getInt(it) }, Difficulty.valueOf(o.getString("d")), o.getLong("t"),
            )
            if (n.length() != 81 || (0 until 81).any { g.isGiven(it) && g.cells[it] != g.puzzle[it] }) null else g
        } catch (_: Exception) {
            null
        }

        private fun digits(a: IntArray) = a.joinToString("")

        private fun parse(s: String): IntArray {
            require(s.length == 81 && s.all { it in '0'..'9' })
            return IntArray(81) { s[it] - '0' }
        }
    }
}
