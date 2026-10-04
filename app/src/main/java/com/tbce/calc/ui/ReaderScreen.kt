package com.tbce.calc.ui

import android.app.Activity
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.tbce.calc.AppController
import com.tbce.calc.Config
import com.tbce.calc.Disguise
import com.tbce.calc.R
import com.tbce.calc.reader.Block
import com.tbce.calc.reader.Break
import com.tbce.calc.reader.Heading
import com.tbce.calc.reader.Hit
import com.tbce.calc.reader.Overlay
import com.tbce.calc.reader.Para
import com.tbce.calc.reader.ReaderState
import com.tbce.calc.reader.ReaderTheme
import com.tbce.calc.reader.Ref
import com.tbce.calc.reader.Tab
import com.tbce.calc.reader.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PANIC_TAP_GAP_MS = 500L
private val PANIC_CORNER = 96.dp

class ReaderColors(val bg: Color, val text: Color, val dim: Color, val accent: Color, val surface: Color, val dark: Boolean)

fun readerColors(t: ReaderTheme) = when (t) {
    ReaderTheme.PAPER -> ReaderColors(Color(0xFFF8F4EC), Color(0xFF2A2622), Color(0xFF8A8076), Color(0xFF8C5A2B), Color(0xFFEFE9DD), false)
    ReaderTheme.SEPIA -> ReaderColors(Color(0xFFEEE1C6), Color(0xFF3F3225), Color(0xFF85725C), Color(0xFF8A4F1D), Color(0xFFE3D4B4), false)
    ReaderTheme.DARK -> ReaderColors(Color(0xFF1B1C1F), Color(0xFFDCD8D0), Color(0xFF8E8A83), Color(0xFFD9A066), Color(0xFF26282C), true)
    ReaderTheme.BLACK -> ReaderColors(Color(0xFF000000), Color(0xFFC4C0B8), Color(0xFF7A766F), Color(0xFFC8925C), Color(0xFF121212), true)
}

/** The unlocked side: reader, search and settings. Replaces the beta 0.1 placeholder. */
@Composable
fun ReaderScreen(app: AppController) {
    val session = app.session ?: return
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val state = remember(app.vaultOpens) { ReaderState(context, session, systemDark) }
    var lastActivity by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val touch = { lastActivity = SystemClock.elapsedRealtime() }
    var showPicker by remember { mutableStateOf(false) }

    app.beforeLock = {
        state.save()
        state.dispose()
    }

    // Export and import go through the system file picker (Storage Access Framework).
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        app.pickerOpen = false
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null && app.session != null) {
            state.notice = try {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                "Backup saved. Keep the passphrase somewhere other than the phone."
            } catch (e: Exception) {
                "The file couldn't be written."
            }
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        app.pickerOpen = false
        if (uri == null || app.session == null) return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 64 * 1024 * 1024) throw java.io.IOException()
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
        if (bytes == null) state.notice = "That file couldn't be read." else state.overlay = Overlay.Passphrase(export = false, file = bytes)
    }
    val startImport = { app.pickerOpen = true; openDoc.launch(arrayOf("*/*")) }
    val exportReady: (ByteArray) -> Unit = { blob ->
        pendingExport = blob
        app.pickerOpen = true
        createDoc.launch("calc-data")
    }

    BackHandler {
        when {
            showPicker -> showPicker = false
            state.selection != null -> state.selection = null
            state.tab != Tab.READ -> state.tab = Tab.READ
            else -> app.lock()
        }
    }

    // Idle lock: any touch or typing resets the clock.
    LaunchedEffect(lastActivity) {
        delay(Config.IDLE_LOCK_MS)
        app.lock()
    }

    val c = readerColors(state.theme)
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val cornerPx = rememberUpdatedState(
        with(density) {
            (insets.getLeft(density, layoutDir) + PANIC_CORNER.toPx()) to (insets.getTop(density) + PANIC_CORNER.toPx())
        },
    )
    // The keyboard belongs to the search field only.
    LaunchedEffect(state.tab, showPicker, state.overlay) {
        if ((state.tab != Tab.SEARCH && state.overlay == null) || showPicker) {
            (view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !c.dark
            isAppearanceLightNavigationBars = !c.dark
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .pointerInput(Unit) {
                // Watches every touch without consuming it: resets the idle clock, and two taps in
                // the top-left corner up to PANIC_TAP_GAP_MS apart lock (the controls there still work).
                var lastTap = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touch()
                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                    touch()
                    val corner = cornerPx.value
                    if (up != null && down.position.x < corner.first && down.position.y < corner.second) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTap <= PANIC_TAP_GAP_MS) app.lock() else lastTap = now
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(app, state, c, onPicker = { showPicker = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    Tab.READ -> ReadPane(state, c)
                    Tab.SAVED -> SavedPane(state, c, touch)
                    Tab.SEARCH -> SearchPane(state, c, touch)
                    Tab.SETTINGS -> SettingsPane(app, state, c, startImport)
                }
            }
            if (state.selection != null && state.tab == Tab.READ) SelectionBar(state, c) else BottomBar(state, c)
        }
        if (showPicker) Picker(state, c) { showPicker = false }
        when (val o = state.overlay) {
            is Overlay.Editor -> EditorOverlay(state, o, c, touch)
            is Overlay.SavedDetail -> SavedDetailOverlay(state, o, c, touch)
            is Overlay.Passphrase -> PassphraseOverlay(state, o, c, touch, exportReady)
            Overlay.ChangeCode -> CodeEntry(
                ui = state.meta.ui,
                colors = CodeColors(c.bg, c.text, c.dim),
                changing = true,
                onDone = { code -> app.changeCode(code); state.overlay = null; state.notice = "Code changed. Use the new code from now on." },
                onCancel = { state.overlay = null },
            )
            null -> {}
        }
        state.notice?.let { msg ->
            AlertDialog(
                onDismissRequest = { state.notice = null },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { state.notice = null }) { Text("OK") } },
            )
        }

    }
}

@Composable
private fun TopBar(app: AppController, state: ReaderState, c: ReaderColors, onPicker: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Not keyboard-focusable, so a stray Enter or Space can't trigger it.
        IconButton(onClick = { app.lock() }, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(painterResource(R.drawable.ic_hide), contentDescription = "Hide", tint = c.dim)
        }
        if (state.tab == Tab.READ) {
            val (book, ch) = state.location()
            Text(
                "${book.name} $ch ▾",
                color = c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPicker).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Text(
                when (state.tab) { Tab.SAVED -> "Saved"; Tab.SEARCH -> "Search"; else -> "Settings" },
                color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            Text(
                state.translation.abbr + " ▾",
                color = c.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, c.dim.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { menu = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                for (t in state.meta.translations) {
                    DropdownMenuItem(
                        text = { Text("${t.abbr}  ·  ${t.name}", color = if (t.id == state.translationId) c.accent else c.text) },
                        onClick = { menu = false; state.switchTranslation(t.id) },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun BottomBar(state: ReaderState, c: ReaderColors) {
    HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
    Row(Modifier.fillMaxWidth().height(56.dp)) {
        for ((tab, label) in listOf(Tab.READ to "Read", Tab.SAVED to "Saved", Tab.SEARCH to "Search", Tab.SETTINGS to "Settings")) {
            val selected = state.tab == tab
            Box(
                Modifier.weight(1f).fillMaxSize().clickable { state.tab = tab },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) c.accent else c.dim, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------- reading

@Composable
private fun ReadPane(state: ReaderState, c: ReaderColors) {
    val t = state.translation
    val pager = rememberPagerState(initialPage = state.position) { t.totalChapters }
    LaunchedEffect(state.position) {
        if (pager.currentPage != state.position) pager.scrollToPage(state.position)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect {
            if (it != state.position) { state.position = it; state.pendingVerse = 0; state.selection = null }
            val (b, ch) = state.location(it)
            state.annotations.visit(b.code, ch)
        }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { "${t.id}-$it" }) { page ->
        ChapterPage(state, c, page)
    }
}

@Composable
private fun ChapterPage(state: ReaderState, c: ReaderColors, global: Int) {
    val t = state.translation
    val (meta, chapter) = state.location(global)
    val blocks by produceState<List<Block>?>(null, t.id, global) {
        value = withContext(Dispatchers.Default) {
            try { state.book(t.id, meta.code).chapters[chapter - 1] } catch (e: Exception) { emptyList() }
        }
    }
    val list = rememberLazyListState()
    val family = remember(state.typeface) { state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif }
    val boldFamily = remember(state.boldTypeface) { state.boldTypeface?.let { FontFamily(it) } ?: family }
    val b = blocks ?: return

    LaunchedEffect(b, state.pendingVerse, state.position) {
        val v = state.pendingVerse
        if (v > 0 && global == state.position) {
            val i = b.indexOfFirst { it is Para && it.segs.any { s -> s.start && s.verse == v } }
            if (i >= 0) list.scrollToItem(i + 1)
            state.pendingVerse = 0
        }
    }

    val size = state.fontSize
    val base = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size * state.lineSpacing).sp, color = c.text)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(meta.name.uppercase(), color = c.dim, fontSize = 13.sp, letterSpacing = 2.sp)
                Text("$chapter", color = c.text, fontFamily = family, fontSize = 44.sp, fontWeight = FontWeight.Light)
            }
        }
        itemsIndexed(b) { _, block ->
            when (block) {
                is Heading -> HeadingText(block, base, boldFamily, c)
                is Title -> Text(block.text, style = base.copy(fontStyle = FontStyle.Italic, color = c.dim, fontSize = (size * 0.9f).sp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                is Para -> ParaText(block, meta.code, chapter, base, c, size, state)
                Break -> Spacer(Modifier.height((size * 0.6f).dp))
            }
        }
        item { ChapterFooter(state, c, meta.code, chapter, family) }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { state.position = global - 1 }, enabled = global > 0) { Text("‹ Previous", color = c.dim) }
                TextButton(onClick = { state.position = global + 1 }, enabled = global < t.totalChapters - 1) { Text("Next ›", color = c.dim) }
            }
        }
    }
}

@Composable
private fun HeadingText(h: Heading, base: TextStyle, bold: FontFamily, c: ReaderColors) {
    when (h.level) {
        0 -> Text(h.text.uppercase(), style = base.copy(fontSize = 13.sp, letterSpacing = 2.sp, color = c.dim, lineHeight = 18.sp),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp))
        1 -> Text(h.text, style = base.copy(fontFamily = bold, fontSize = (base.fontSize.value * 0.92f).sp, lineHeight = (base.fontSize.value * 1.25f).sp),
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        else -> Text(h.text, style = base.copy(fontStyle = FontStyle.Italic, color = c.dim, fontSize = (base.fontSize.value * 0.9f).sp),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Picker(state: ReaderState, c: ReaderColors, close: () -> Unit) {
    val t = state.translation
    val (current, _) = state.location()
    var open by remember { mutableIntStateOf(t.bookIndex(current.code)) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (open - 2).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.meta.ui("book"), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = close) { Text("Close", color = c.accent) }
        }
        val recent = state.annotations.recent.drop(1).take(6)
        if (recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RECENT", color = c.dim, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(end = 4.dp))
                for (r in recent) {
                    Text(
                        state.label(r.book, r.chapter), color = c.text, fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface)
                            .clickable { state.goTo(Ref(r.book, r.chapter)); close() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(t.books) { i, book ->
                if (i == 0 || i == 39) {
                    Text(
                        state.meta.ui(if (i == 0) "ot" else "nt").uppercase(),
                        color = c.dim, fontSize = 12.sp, letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                Text(
                    book.name,
                    color = if (i == open) c.accent else c.text,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = if (open == i) -1 else i }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
                if (i == open) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (ch in 1..book.chapters) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surface)
                                    .clickable { state.goTo(Ref(book.code, ch)); close() },
                                contentAlignment = Alignment.Center,
                            ) { Text("$ch", color = c.text, fontSize = 16.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
private fun SearchPane(state: ReaderState, c: ReaderColors, touch: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var count by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val t = state.translation
    val ref = remember(query) { state.references.parse(query) }

    LaunchedEffect(query, t.id) {
        delay(300)
        val q = query.trim()
        if (q.length < 2) { hits = emptyList(); count = 0; return@LaunchedEffect }
        busy = true
        val (h, n) = withContext(Dispatchers.Default) { state.searchIndex().search(q) }
        hits = h; count = n; busy = false
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PrivateField(
            hint = state.meta.ui("search_hint"),
            c = c,
            onChange = { query = it; touch() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (ref != null) {
                item {
                    val name = t.books.firstOrNull { it.code == ref.book }?.name ?: ref.book
                    val label = buildString {
                        append("${state.meta.ui("go_to")} $name ${ref.chapter}")
                        if (ref.verse > 0) append(":${ref.verse}")
                        if (ref.verseEnd > 0) append("–${ref.verseEnd}")
                    }
                    Text(
                        label, color = c.accent, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().clickable { state.goTo(ref) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
                }
            }
            if (busy) item { Text("Searching…", color = c.dim, modifier = Modifier.padding(20.dp)) }
            else if (query.trim().length >= 2) item {
                Text(
                    if (count == 0) state.meta.ui("no_results") else "$count ${state.meta.ui("results")}" + if (count > hits.size) " (first ${hits.size})" else "",
                    color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            itemsIndexed(hits) { _, h ->
                val book = t.books[h.book]
                Column(
                    Modifier.fillMaxWidth().clickable { state.goTo(Ref(book.code, h.chapter, h.verse)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("${book.name} ${h.chapter}:${h.verse}", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(highlight(h.text, query, c), color = c.text, fontSize = 16.sp, lineHeight = 22.sp,
                        fontFamily = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif)
                }
            }
        }
    }
}

private fun highlight(text: String, query: String, c: ReaderColors): AnnotatedString {
    val words = query.split(Regex("\\W+")).filter { it.length >= 2 }
    if (words.isEmpty()) return AnnotatedString(text)
    val rx = Regex(words.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    return buildAnnotatedString {
        var last = 0
        for (m in rx.findAll(text)) {
            append(text, last, m.range.first)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.accent)) { append(m.value) }
            last = m.range.last + 1
        }
        append(text, last, text.length)
    }
}

/** Text field that asks the keyboard not to learn, suggest or autofill, and blocks copy. */
@Composable
fun PrivateField(
    hint: String,
    c: ReaderColors,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    initial: String = "",
    multiline: Boolean = false,
    autoFocus: Boolean = true,
) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(c.surface),
        factory = { ctx ->
            EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0)
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    (if (multiline) 0 else EditorInfo.IME_ACTION_SEARCH)
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                isSingleLine = !multiline
                if (multiline) {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    setLineSpacing(0f, 1.25f)
                }
                setText(initial)
                setSelection(initial.length)
                background = null
                setPadding(36, 28, 36, 28)
                this.hint = hint
                setHintTextColor(c.dim.toArgb())
                setTextColor(c.text.toArgb())
                textSize = 17f
                val block = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                }
                customSelectionActionModeCallback = block
                customInsertionActionModeCallback = block
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                    override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
                })
                if (autoFocus) post {
                    requestFocus()
                    (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .showSoftInput(this, 0)
                }
            }
        },
        update = { it.setTextColor(c.text.toArgb()); it.setHintTextColor(c.dim.toArgb()) },
        onRelease = { it.text.clear() },
    )
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsPane(app: AppController, state: ReaderState, c: ReaderColors, onImport: () -> Unit) {
    val context = LocalContext.current
    var about by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    var pendingDisguise by remember { mutableStateOf<Disguise?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    if (about) {
        AboutPane(state, c) { about = false }
        return
    }
    if (guide) {
        GuidePane(state, c) { guide = false }
        return
    }
    val family = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Label("Theme", c)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((t, name) in listOf(ReaderTheme.PAPER to "Paper", ReaderTheme.SEPIA to "Sepia", ReaderTheme.DARK to "Dark", ReaderTheme.BLACK to "Black")) {
                val tc = readerColors(t)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { state.theme = t }) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(tc.bg)
                            .border(if (state.theme == t) 3.dp else 1.dp, if (state.theme == t) c.accent else c.dim.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = tc.text, fontFamily = family) }
                    Text(name, color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Label("Text size", c)
        Slider(
            value = state.fontSize, onValueChange = { state.fontSize = it }, valueRange = 14f..30f, steps = 15,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.dim.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Label("Line spacing", c)
        Slider(
            value = state.lineSpacing, onValueChange = { state.lineSpacing = it }, valueRange = 1.2f..2.0f, steps = 7,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.dim.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Text(
            "The quick brown fox jumps over the lazy dog. Sample text at this size and spacing.",
            color = c.text, fontFamily = family, fontSize = state.fontSize.sp, lineHeight = (state.fontSize * state.lineSpacing).sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Label(state.meta.ui("translation"), c)
        for (t in state.meta.translations) {
            Text(
                "${t.abbr}  ·  ${t.name}",
                color = if (t.id == state.translationId) c.accent else c.text,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable { state.switchTranslation(t.id) }.padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
        Label("Disguise", c)
        Text(
            "How the app looks on your home screen. Only one shows at a time; the icon and name change right away.",
            color = c.dim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 4.dp),
        )
        for (d in Disguise.entries) {
            val on = app.disguise == d
            Text(
                (if (on) "✓  " else "     ") + d.label,
                color = if (on) c.accent else c.text,
                fontSize = 16.sp,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clickable { if (!on) pendingDisguise = d }.padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
        Text(state.meta.ui("guide"), color = c.text, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { guide = true }.padding(vertical = 14.dp))
        Text(state.meta.ui("about"), color = c.text, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { about = true }.padding(vertical = 14.dp))
        BackupAndSecurity(app, state, c, onImport)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
        Text("Erase everything", color = Color(0xFFC0392B), fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { confirmErase = true }.padding(vertical = 14.dp))
        Text(
            "Locks after ${Config.IDLE_LOCK_MS / 1000} seconds without a touch, when you leave the app or turn the screen off, " +
                "and when you double-tap the top-left corner.",
            color = c.dim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp),
        )
    }
    pendingDisguise?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDisguise = null },
            title = { Text("Look like ${d.label}?") },
            text = { Text(state.meta.ui(if (d == Disguise.NOTES) "entry_notes" else "entry_calc") + "\n\nThe icon and name change now, and the app returns to the home screen.") },
            confirmButton = { TextButton(onClick = { pendingDisguise = null; app.setDisguise(context, d) }) { Text("Switch") } },
            dismissButton = { TextButton(onClick = { pendingDisguise = null }) { Text("Cancel") } },
        )
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = { Text("This deletes the code and all settings. It can't be undone. You'll set a new code the next time you open the reader.") },
            confirmButton = { TextButton(onClick = { confirmErase = false; app.beforeLock = null; app.eraseEverything() }) { Text("Erase") } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String, c: ReaderColors) {
    Text(text.uppercase(), color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
}


@Composable
private fun AboutPane(state: ReaderState, c: ReaderColors, close: () -> Unit) {
    val text = remember { state.packs.text("about") }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = close, modifier = Modifier.padding(start = 8.dp)) { Text("‹ Back", color = c.accent) }
        Text(text, color = c.text, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

/** The user guide (docs/USER_GUIDE.md, shipped encrypted), with headings, lists and paragraphs. */
@Composable
private fun GuidePane(state: ReaderState, c: ReaderColors, close: () -> Unit) {
    val lines = remember { state.packs.text("guide").replace("`", "").lines() }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = close, modifier = Modifier.padding(start = 8.dp)) { Text("‹ Back", color = c.accent) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // Join wrapped lines into paragraphs and list items.
            val blocks = ArrayList<String>()
            for (l in lines) {
                val t = l.trimEnd()
                val starts = t.isEmpty() || t.startsWith("#") || t.startsWith("- ") || Regex("^\\d+\\. ").containsMatchIn(t)
                if (t.isEmpty()) blocks += ""
                else if (starts || blocks.isEmpty() || blocks.last().isEmpty() || blocks.last().startsWith("#")) blocks += t
                else blocks[blocks.lastIndex] = blocks.last() + " " + t.trim()
            }
            itemsIndexed(blocks.filter { it.isNotEmpty() }) { _, b ->
                when {
                    b.startsWith("# ") -> Text(b.removePrefix("# "), color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    b.startsWith("## ") -> Text(b.removePrefix("## "), color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                    b.startsWith("- ") -> Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = c.dim, fontSize = 15.sp, modifier = Modifier.width(18.dp))
                        Text(b.removePrefix("- "), color = c.text, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                    else -> Text(b, color = c.text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
