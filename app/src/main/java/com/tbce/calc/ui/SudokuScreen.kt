package com.tbce.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.tbce.calc.AppController
import com.tbce.calc.sudoku.Difficulty
import com.tbce.calc.sudoku.Game
import com.tbce.calc.sudoku.Sudoku
import com.tbce.calc.sudoku.SudokuStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class SudokuColors(
    val bg: Color, val text: Color, val dim: Color, val given: Color, val entered: Color, val conflict: Color,
    val cell: Color, val peer: Color, val same: Color, val sel: Color, val thin: Color, val thick: Color,
    val accent: Color,
)

private fun sudokuColors(dark: Boolean) = if (dark)
    SudokuColors(
        bg = Color(0xFF101216), text = Color(0xFFECEEF2), dim = Color(0xFF888D96), given = Color(0xFFECEEF2),
        entered = Color(0xFF7FB0FF), conflict = Color(0xFFFF6B5E), cell = Color(0xFF181B21), peer = Color(0xFF20252E),
        same = Color(0xFF2A3A55), sel = Color(0xFF34507F), thin = Color(0xFF30353F), thick = Color(0xFF8A919E),
        accent = Color(0xFF7FB0FF),
    )
else
    SudokuColors(
        bg = Color(0xFFF7F8FA), text = Color(0xFF16181D), dim = Color(0xFF80858F), given = Color(0xFF16181D),
        entered = Color(0xFF2B5FD1), conflict = Color(0xFFD23B2C), cell = Color(0xFFFFFFFF), peer = Color(0xFFE8EEF7),
        same = Color(0xFFC9D8F0), sel = Color(0xFFB5CCF2), thin = Color(0xFFC9CED6), thick = Color(0xFF39404C),
        accent = Color(0xFF2B5FD1),
    )

/** Everything the hidden gesture restores when it ends, so a code typed on the board leaves no trace. */
private class Snapshot(val game: Game, val history: List<Game>, val selected: Int, val pencil: Boolean, val elapsed: Long)

/**
 * The Sudoku disguise: a full game (new puzzles at three levels, pencil marks, undo, hints, a timer,
 * and save/resume). Behind it is the same reader. The hidden entry is played on the board itself:
 * hold Notes to arm, then the number pad types the digits (Erase is 0, Undo removes one), and hold
 * Notes again to submit. Each press also changes the board, like ordinary play; when the gesture
 * ends the board goes back to how it was, and nothing is saved while it is armed.
 */
@Composable
fun SudokuScreen(app: AppController) {
    val c = sudokuColors(isSystemInDarkTheme())
    val store = SudokuStore(LocalContext.current)
    val scope = rememberCoroutineScope()

    var game by remember { mutableStateOf(store.load()) }
    var history by remember { mutableStateOf(emptyList<Game>()) }
    var elapsed by remember { mutableLongStateOf(game?.elapsedMs ?: 0L) }
    var selected by remember { mutableIntStateOf(-1) }
    var pencil by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    // Hidden entry: the code being typed and the board as it was when the gesture armed.
    var code by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var prevArmed by remember { mutableStateOf(app.armed) }

    if (app.armed != prevArmed) {
        prevArmed = app.armed
        code = ""
        if (app.armed) {
            game?.let { snapshot = Snapshot(it, history, selected, pencil, elapsed) }
        } else {
            snapshot?.let { s -> game = s.game; history = s.history; selected = s.selected; pencil = s.pencil; elapsed = s.elapsed }
            snapshot = null
        }
    }

    fun save() {
        if (!app.armed) game?.let { store.save(it.withElapsed(elapsed)) }
    }

    fun commit(next: Game) {
        val g = game ?: return
        if (next === g) return
        history = (history + g).takeLast(200)
        game = next
        save()
    }

    fun newGame(d: Difficulty) {
        // Drop the snapshot first, so ending the gesture can't put the old board back over the new one.
        snapshot = null
        if (app.armed) app.faceDisarm()
        generating = true
        scope.launch {
            val g = withContext(Dispatchers.Default) { Game.new(d) }
            game = g; history = emptyList(); elapsed = 0; selected = -1; pencil = false
            generating = false
            save()
        }
    }

    // First launch: start an easy game.
    LaunchedEffect(Unit) { if (game == null) newGame(Difficulty.EASY) }

    // The clock runs only while the screen is in front and the puzzle is unsolved.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && game?.solved == false && !generating
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var ticks = 0
        try {
            while (true) {
                delay(1000)
                elapsed += 1000
                if (++ticks % 10 == 0) save()
            }
        } finally {
            save()
        }
    }

    BackHandler(enabled = app.armed) { app.faceDisarm() }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sudoku", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("New game", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { choosing = true }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(game?.difficulty?.label ?: "", color = c.dim, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(clockText(elapsed), color = c.dim, fontSize = 14.sp)
        }

        val g = game
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (g == null || generating) Text("Making a puzzle…", color = c.dim, fontSize = 15.sp)
            else Board(g, selected, c) { selected = it }
        }

        Spacer(Modifier.height(14.dp))

        // Tools. Notes: a tap toggles pencil marks; a hold is the hidden gesture (arm, then submit).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tool("↶", "Undo", c) {
                if (app.armed) code = code.dropLast(1)
                history.lastOrNull()?.let { game = it; history = history.dropLast(1); save() }
            }
            Tool("⌫", "Erase", c) {
                if (app.armed && code.length < 16) code += "0"
                val s = selected
                if (g != null && s >= 0) commit(g.erase(s))
            }
            Tool("✎", if (pencil) "Notes on" else "Notes", c, active = pencil,
                modifier = Modifier.faceHoldable(
                    onTap = { pencil = !pencil },
                    onHold = { app.faceTrigger(code.toByteArray()) },
                ),
            )
            Tool("✦", "Hint", c) {
                val s = selected
                if (g != null && s >= 0) commit(g.hint(s))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..9) {
                // A digit with all nine placed is dimmed but stays pressable.
                val done = g != null && g.count(n) >= 9
                Box(
                    Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(10.dp)).clickable {
                        if (app.armed && code.length < 16) code += n.toString()
                        val s = selected
                        if (g != null && s >= 0) commit(if (pencil) g.toggleNote(s, n) else g.place(s, n))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(n.toString(), color = if (done) c.dim.copy(alpha = 0.4f) else c.accent, fontSize = 30.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("New game") },
            text = {
                Column {
                    for (d in Difficulty.entries) {
                        Text(d.label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                            .clickable { choosing = false; newGame(d) }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }

    val g = game
    if (g != null && g.solved && !app.armed && !generating && !choosing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Solved!") },
            text = { Text("${g.difficulty.label} in ${clockText(elapsed)}.") },
            confirmButton = { TextButton(onClick = { choosing = true }) { Text("New game") } },
        )
    }
}

@Composable
private fun Board(g: Game, selected: Int, c: SudokuColors, onSelect: (Int) -> Unit) {
    val selValue = if (selected >= 0) g.cells[selected] else 0
    Column(
        Modifier.fillMaxSize().background(c.cell).drawWithContent {
            drawContent()
            val step = size.width / 9f
            for (k in 0..9) {
                val thick = k % 3 == 0
                val w = if (thick) 2.dp.toPx() else 0.6.dp.toPx()
                val color = if (thick) c.thick else c.thin
                val p = (k * step).coerceIn(w / 2, size.width - w / 2)
                drawLine(color, Offset(p, 0f), Offset(p, size.height), w)
                drawLine(color, Offset(0f, p), Offset(size.width, p), w)
            }
        },
    ) {
        for (r in 0 until 9) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 9) {
                    val i = r * 9 + col
                    val v = g.cells[i]
                    val bg = when {
                        i == selected -> c.sel
                        selValue != 0 && v == selValue -> c.same
                        selected >= 0 && (Sudoku.row(i) == Sudoku.row(selected) || Sudoku.col(i) == Sudoku.col(selected) ||
                            Sudoku.box(i) == Sudoku.box(selected)) -> c.peer
                        else -> Color.Transparent
                    }
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(bg).clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (v != 0) {
                            Text(
                                v.toString(),
                                color = when { g.conflict(i) && !g.isGiven(i) -> c.conflict; g.isGiven(i) -> c.given; else -> c.entered },
                                fontSize = 22.sp,
                                fontWeight = if (g.isGiven(i)) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        } else if (g.notes[i] != 0) {
                            PencilMarks(g.notes[i], c)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PencilMarks(mask: Int, c: SudokuColors) {
    Column(Modifier.fillMaxSize().padding(2.dp)) {
        for (r in 0 until 3) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (k in 1..3) {
                    val v = r * 3 + k
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl v) != 0) Text(v.toString(), color = c.dim, fontSize = 9.sp, lineHeight = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tool(
    icon: String, label: String, c: SudokuColors, active: Boolean = false,
    modifier: Modifier? = null, onClick: () -> Unit = {},
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).then(modifier ?: Modifier.clickable { onClick() })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = if (active) c.accent else c.text, fontSize = 22.sp)
        Text(label, color = if (active) c.accent else c.dim, fontSize = 12.sp)
    }
}

private fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%02d:%02d", s / 60, s % 60)
}
