package com.tbce.calc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours for [PdfScreen], taken from the face that hosts it. */
class PdfColors(val bg: Color, val text: Color, val dim: Color, val accent: Color, val line: Color)

/**
 * An open PDF. [PdfRenderer] allows one open page at a time and isn't thread-safe, so every use
 * goes through this object's lock. [sizes] are the pages' sizes in points, read once on open.
 */
internal class PdfDoc(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer, val name: String) {
    val sizes: List<Pair<Int, Int>> = (0 until renderer.pageCount).map { i ->
        val p = renderer.openPage(i)
        try { p.width to p.height } finally { p.close() }
    }
    private var closed = false

    /** Page [index] rendered [width] pixels wide on white, or null if closed or out of memory. */
    fun render(index: Int, width: Int): Bitmap? = synchronized(this) {
        if (closed) return null
        val (w, h) = sizes[index]
        val height = (width.toLong() * h / w).toInt().coerceAtLeast(1)
        val bitmap = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { return null }
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val page = renderer.openPage(index)
        try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } finally { page.close() }
        bitmap
    }

    fun close() = synchronized(this) {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }

    companion object {
        /** Opens [uri] read-only; the error text is for the user. */
        fun open(context: Context, uri: Uri): Result<PdfDoc> {
            var pfd: ParcelFileDescriptor? = null
            return try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return Result.failure(Exception("That file couldn't be opened."))
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "Document"
                Result.success(PdfDoc(pfd, PdfRenderer(pfd), name))
            } catch (_: SecurityException) {
                pfd?.close()
                Result.failure(Exception("This PDF is password-protected, which isn't supported."))
            } catch (_: Exception) {
                pfd?.close()
                Result.failure(Exception("That file couldn't be opened as a PDF."))
            }
        }
    }
}

/**
 * The open PDF and where the reader is in it. Held by the hosting face so that switching tabs
 * (to look a word up, say) keeps the document open and in place. Only in memory: it is gone when
 * the app's process ends, and [close] releases the file.
 */
class PdfHolder {
    internal var doc by mutableStateOf<PdfDoc?>(null)
    internal var list = LazyListState()
    internal var zoom by mutableFloatStateOf(1f)
    internal var error by mutableStateOf<String?>(null)
    internal var opening by mutableStateOf(false)

    internal fun show(new: PdfDoc?) {
        doc?.close()
        doc = new
        list = LazyListState()
        zoom = 1f
    }

    fun close() = show(null)
}

/**
 * The reference face's PDF tab: views a PDF picked with the system file picker (no permissions).
 * Nothing about opened files (name or location) is stored, and the file is read, never written.
 * Pinch to zoom; drag to move around. Pages render on demand into a cache capped by size.
 */
@Composable
fun PdfScreen(holder: PdfHolder, c: PdfColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by holder::doc
    var error by holder::error
    var opening by holder::opening

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        opening = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { PdfDoc.open(context, uri) }
            opening = false
            result.onSuccess { holder.show(it) }.onFailure { error = it.message }
        }
    }
    val current = doc
    BackHandler(enabled = current != null) { holder.close() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                Text("‹", color = c.accent, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { holder.close() }.padding(horizontal = 14.dp, vertical = 2.dp))
                Text(current.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Text("PDF", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            }
            Text("Open", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { pick.launch(arrayOf("application/pdf")) }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        // A file that fails while another is open is reported over it; the open one stays.
        if (current != null && error != null) {
            AlertDialog(
                onDismissRequest = { error = null },
                text = { Text(error!!) },
                confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
            )
        }
        when {
            current != null -> Pages(current, holder, c)
            else -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        opening -> "Opening…"
                        error != null -> error!!
                        else -> "Read PDF files saved on this phone."
                    },
                    color = c.dim, fontSize = 16.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text("Open a PDF", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(c.accent).clickable { pick.launch(arrayOf("application/pdf")) }
                        .padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }
}

private const val MAX_ZOOM = 4f
/** Widest page bitmap, in pixels. A 2048 px A4 page is about 23 MB. */
private const val MAX_RENDER_WIDTH = 2048

@Composable
private fun Pages(doc: PdfDoc, holder: PdfHolder, c: PdfColors) {
    val list = holder.list
    val hScroll = rememberScrollState()
    var zoom by holder::zoom
    // Rendered pages, keyed by page and width. Capped at an eighth of the heap, so a long PDF
    // never holds more than a few pages; evicted bitmaps are left to the garbage collector.
    val cache = remember(doc) {
        val max = (Runtime.getRuntime().maxMemory() / 8).toInt()
        object : LruCache<String, Bitmap>(max) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    }
    DisposableEffect(cache) { onDispose { cache.evictAll() } }
    val page by remember(list) { derivedStateOf { list.firstVisibleItemIndex + 1 } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.line).pointerInput(doc) {
            // Two fingers zoom and move; one finger is left to the scrolling below.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val old = zoom
                        val new = (old * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                        val focus = event.calculateCentroid()
                        val pan = event.calculatePan()
                        zoom = new
                        val ratio = new / old
                        // Keep the point between the fingers in place, then follow the fingers.
                        hScroll.dispatchRawDelta((hScroll.value + focus.x) * ratio - focus.x - hScroll.value - pan.x)
                        list.dispatchRawDelta(focus.y * (ratio - 1f) - pan.y)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        val density = LocalDensity.current
        val baseWidthPx = with(density) { maxWidth.toPx() }
        // Re-render sharper once zoomed in; 1x and 2x keep the number of bitmaps small.
        val renderWidth = (baseWidthPx * (if (zoom >= 1.4f) 2f else 1f)).toInt().coerceAtMost(MAX_RENDER_WIDTH)
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxHeight().horizontalScroll(hScroll).width(maxWidth * zoom),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(doc.sizes.size, key = { it }) { i ->
                val (w, h) = doc.sizes[i]
                val key = "$i@$renderWidth"
                var bitmap by remember(doc, i, renderWidth) { mutableStateOf(cache.get(key) ?: cache.snapshot().entries.firstOrNull { it.key.startsWith("$i@") }?.value) }
                LaunchedEffect(doc, i, renderWidth) {
                    if (cache.get(key) == null) {
                        withContext(Dispatchers.IO) { doc.render(i, renderWidth) }?.let { cache.put(key, it); bitmap = it }
                    }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h).background(Color.White)) {
                    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        Text(
            "$page / ${doc.sizes.size}", color = Color.White, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
