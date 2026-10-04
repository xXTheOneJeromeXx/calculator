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

enum class KeyStyle { NUMBER, FUNCTION, OPERATOR, EQUALS }

/** A keypad key (the code pads). Fires [onClick] on release; sliding off cancels. */
@Composable
fun Key(
    label: String,
    style: KeyStyle,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    description: String = label,
    onClick: () -> Unit,
) {
    val colors = LocalCalcColors.current
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
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
                    // Null when cancelled (finger slid away, etc.).
                    if (waitForUpOrCancellation() != null) click()
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
