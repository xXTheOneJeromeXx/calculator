package com.tbce.calc.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tbce.calc.AppController
import com.tbce.calc.notes.NotesStore

private class NoteColors(val bg: Color, val bar: Color, val card: Color, val text: Color, val dim: Color, val accent: Color)

private fun noteColors(dark: Boolean) = if (dark)
    NoteColors(Color(0xFF121210), Color(0xFF1B1B18), Color(0xFF1E1E1B), Color(0xFFECEAE3), Color(0xFF8E8B83), Color(0xFFE6B84C))
else
    NoteColors(Color(0xFFFBFAF6), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF1C1B18), Color(0xFF8A8780), Color(0xFFBE8E1C))

/**
 * The Notes disguise: a plain, working notepad. Behind it is the same reader and code. To open the
 * reader, start a new note, hold Done to arm (the note clears), type the code, and hold Done again.
 * A normal tap of Done just saves the note. With [hiddenEntry] false (the Notes tab of the
 * reference disguise, whose way in is its Search button) holding Done does nothing.
 */
@Composable
fun NotesScreen(app: AppController, hiddenEntry: Boolean = true) {
    val context = LocalContext.current
    val store = remember { NotesStore(context) }
    val c = noteColors(isSystemInDarkTheme())
    var notes by remember { mutableStateOf(store.load()) }
    // null = list; a Note (possibly new) = editor.
    var editing by remember { mutableStateOf<NotesStore.Note?>(null) }

    val current = editing
    if (current == null) {
        NotesList(notes, c, onOpen = { editing = it }, onNew = { editing = NotesStore.Note(System.currentTimeMillis(), "", System.currentTimeMillis()) })
    } else {
        NoteEditor(
            app = app,
            hiddenEntry = hiddenEntry,
            note = current,
            c = c,
            onClose = { body ->
                val trimmed = body.trim()
                val list = notes.filter { it.id != current.id }.toMutableList()
                if (trimmed.isNotEmpty()) {
                    current.body = body
                    current.updated = System.currentTimeMillis()
                    list.add(0, current)
                }
                store.save(list)
                notes = store.load()
                editing = null
            },
            onDelete = {
                val list = notes.filter { it.id != current.id }
                store.save(list)
                notes = store.load()
                editing = null
            },
        )
    }
}

@Composable
private fun NotesList(notes: List<NotesStore.Note>, c: NoteColors, onOpen: (NotesStore.Note) -> Unit, onNew: () -> Unit) {
    BackHandler(enabled = false) {}
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notes", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet. Tap + to write one.", color = c.dim, fontSize = 16.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 0.dp, 16.dp, 96.dp)) {
                    items(notes, key = { it.id }) { n ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                                .background(c.card).clickable { onOpen(n) }.padding(16.dp),
                        ) {
                            Text(n.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (n.preview.isNotEmpty()) {
                                Text(n.preview, color = c.dim, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(24.dp).size(60.dp).clip(CircleShape)
                .background(c.accent).clickable { onNew() },
            contentAlignment = Alignment.Center,
        ) { Text("+", color = Color.White, fontSize = 32.sp) }
    }
}

@Composable
private fun NoteEditor(app: AppController, hiddenEntry: Boolean, note: NotesStore.Note, c: NoteColors, onClose: (String) -> Unit, onDelete: () -> Unit) {
    var field by remember { mutableStateOf(note.body) }
    var prevArmed by remember { mutableStateOf(app.armed) }
    val editor = remember { arrayOfNulls<EditText>(1) }

    // When the hidden gesture arms or disarms, the note area becomes the code buffer and is cleared,
    // so a wrong code or a cancelled attempt leaves nothing behind.
    if (app.armed != prevArmed) {
        prevArmed = app.armed
        field = ""
        editor[0]?.setText("")
    }

    BackHandler { if (app.armed) app.faceDisarm() else onClose(field) }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = c.accent, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (app.armed) app.faceDisarm() else onClose(field) }.padding(horizontal = 14.dp, vertical = 2.dp))
            Spacer(Modifier.weight(1f))
            if (note.body.isNotEmpty()) {
                Text("Delete", color = c.dim, fontSize = 16.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onDelete() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            // Tap saves the note; a long hold is the hidden gesture (arm, then submit).
            HoldText(
                label = "Done",
                color = c.accent,
                onClick = { onClose(field) },
                onHold = { if (hiddenEntry) app.faceTrigger(field.filter { it.isDigit() }.toByteArray()) },
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            factory = { ctx ->
                EditText(ctx).apply {
                    // No keyboard learning, so a code typed here can't land in the keyboard's dictionary.
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    isSaveEnabled = false
                    gravity = Gravity.TOP or Gravity.START
                    background = null
                    setPadding(8, 16, 8, 24)
                    setTextColor(c.text.toArgb())
                    setHintTextColor(c.dim.toArgb())
                    hint = "Note"
                    textSize = 18f
                    setText(note.body)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            val t = s?.toString() ?: ""
                            field = t
                            // While armed the note holds only the code; a non-digit means "never mind".
                            if (app.armed && t.any { !it.isDigit() }) app.faceDisarm()
                        }
                    })
                    editor[0] = this
                    post { requestFocus() }
                }
            },
            onRelease = { editor[0] = null },
        )
    }
}

/** Text that fires [onClick] on a tap and [onHold] on a long press (the hidden gesture). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HoldText(label: String, color: Color, onClick: () -> Unit, onHold: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onHold)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
