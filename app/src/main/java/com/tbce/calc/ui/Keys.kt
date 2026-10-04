package com.tbce.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tbce.calc.Config

enum class KeyStyle { NUMBER, FUNCTION, OPERATOR, EQUALS }

/**
 * A keypad key. Fires [onClick] on release. If [onHold] is set, holding for [Config.HOLD_MS]
 * fires it instead, while the finger is still down, and the release then does nothing.
 * Holding any key looks the same, so a hold on '=' shows nothing different.
 */
@Composable
fun Key(
    label: String,
    style: KeyStyle,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    description: String = label,
    onHold: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalCalcColors.current
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    val (bg, fg) = when (style) {
        KeyStyle.NUMBER -> colors.number to colors.onNumber
        KeyStyle.FUNCTION -> colors.function to colors.onFunction
        KeyStyle.OPERATOR -> colors.operator to colors.onOperator
        KeyStyle.EQUALS -> colors.equals to colors.onEquals
    }
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (pressed) lerp(bg, fg) else bg)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { click(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    // 0 = held long enough, 1 = released, 2 = cancelled (finger slid away, etc.)
                    var outcome = 0
                    val h = hold
                    if (h == null) {
                        outcome = if (waitForUpOrCancellation() != null) 1 else 2
                    } else {
                        withTimeoutOrNull(Config.HOLD_MS) {
                            outcome = if (waitForUpOrCancellation() != null) 1 else 2
                        }
                    }
                    when (outcome) {
                        1 -> click()
                        0 -> {
                            h?.invoke()
                            waitForUpOrCancellation()
                        }
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = fontSize, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}

private fun lerp(a: Color, b: Color): Color = androidx.compose.ui.graphics.lerp(a, b, 0.18f)

/** Spacing between keys. */
val KeyGap = 10.dp
