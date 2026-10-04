package com.tbce.calc.sudoku

import android.content.Context

/**
 * The decoy Sudoku's saved game. Like the Notes face's notes, it is ordinary game state kept in
 * plain preferences so the disguise looks lived-in. Nothing is saved while the hidden gesture is
 * armed, so a code typed on the board never reaches this file.
 */
class SudokuStore(context: Context) {
    private val prefs = context.getSharedPreferences("s", Context.MODE_PRIVATE)

    fun load(): Game? = Game.fromJson(prefs.getString("g", null))

    fun save(game: Game) {
        prefs.edit().putString("g", game.toJson()).apply()
    }
}
