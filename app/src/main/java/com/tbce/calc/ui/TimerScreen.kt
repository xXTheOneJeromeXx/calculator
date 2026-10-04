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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.AppController
import com.tbce.calc.Config
import kotlinx.coroutines.delay
import java.util.Calendar

private class ClockColors(val bg: Color, val text: Color, val dim: Color, val key: Color, val accent: Color, val onAccent: Color)

private fun clockColors(dark: Boolean) = if (dark)
    ClockColors(Color(0xFF0E0F12), Color(0xFFEDEEF2), Color(0xFF8A8E96), Color(0xFF1C1E24), Color(0xFFE6584C), Color(0xFFFFFFFF))
else
    ClockColors(Color(0xFFF6F7F9), Color(0xFF16181D), Color(0xFF898D96), Color(0xFFECEEF2), Color(0xFFD64838), Color(0xFFFFFFFF))

/**
 * The Clock disguise: a working wall clock plus a countdown timer. Behind it is the same reader.
 * To open the reader: hold Start to arm (the timer clears), type the code on the pad, then hold
 * Start again. A normal tap of Start just runs a countdown of whatever is entered.
 */
@Composable
fun TimerScreen(app: AppController) {
    val c = clockColors(isSystemInDarkTheme())

    // Live wall clock, so the screen reads as a plain clock.
    var clock by remember { mutableStateOf(nowClock()) }
    LaunchedEffect(Unit) { while (true) { clock = nowClock(); delay(1000) } }

    // entry = the raw digits typed on the pad (also the code buffer while armed).
    var entry by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableLongStateOf(0L) }
    var prevArmed by remember { mutableStateOf(app.armed) }

    // Arming or disarming the hidden gesture resets the timer, so a wrong or abandoned code
    // leaves nothing on screen.
    if (app.armed != prevArmed) {
        prevArmed = app.armed
        entry = ""
        running = false
        remaining = 0L
    }

    // Countdown tick.
    LaunchedEffect(running) {
        if (running) {
            var last = android.os.SystemClock.elapsedRealtime()
            while (running && remaining > 0) {
                delay(100)
                val n = android.os.SystemClock.elapsedRealtime()
                remaining = (remaining - (n - last)).coerceAtLeast(0)
                last = n
                if (remaining == 0L) running = false
            }
        }
    }

    BackHandler { if (app.armed) app.faceDisarm() }

    val showSeconds = if (running || remaining > 0) remaining / 1000 else entrySeconds(entry)

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Clock", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(clock, color = c.dim, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(formatHms(showSeconds), color = c.text, fontSize = 64.sp, fontWeight = FontWeight.Light)
        }

        // Digit pad: builds the countdown length, or the code while armed.
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (label in row) {
                        Box(Modifier.weight(1f).aspectRatio(1.7f)) {
                            when (label) {
                                "⌫" -> PadKey("⌫", c.key, c.text) { if (entry.isNotEmpty()) entry = entry.dropLast(1) }
                                "✓" -> PadKey("Reset", c.key, c.dim) {
                                    if (app.armed) app.faceDisarm()
                                    entry = ""; running = false; remaining = 0L
                                }
                                else -> PadKey(label, c.key, c.text) { if (entry.length < 6) entry += label }
                            }
                        }
                    }
                }
            }
        }

        // Tap Start = run the countdown; a long hold is the hidden gesture (arm, then submit).
        Box(
            Modifier.fillMaxWidth().height(60.dp).padding(bottom = 8.dp).clip(CircleShape).background(c.accent)
                .faceHoldable(
                    onTap = {
                        if (running) {
                            running = false
                        } else {
                            if (remaining == 0L) remaining = entrySeconds(entry) * 1000L
                            if (remaining > 0) running = true
                        }
                    },
                    onHold = { app.faceTrigger(entry.toByteArray()) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (running) "Pause" else "Start", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PadKey(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 24.sp, fontWeight = FontWeight.Medium) }
}

/** Reads the typed digits as HHMMSS filled from the right, then normalises to total seconds. */
private fun entrySeconds(entry: String): Long {
    if (entry.isEmpty()) return 0
    val d = entry.takeLast(6).padStart(6, '0')
    val hh = d.substring(0, 2).toLong()
    val mm = d.substring(2, 4).toLong()
    val ss = d.substring(4, 6).toLong()
    return hh * 3600 + mm * 60 + ss
}

private fun formatHms(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun nowClock(): String {
    val c = Calendar.getInstance()
    return String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
}
