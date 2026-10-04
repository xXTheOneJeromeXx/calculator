package com.tbce.calc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CalcColors(
    val background: Color,
    val text: Color,
    val dim: Color,
    val number: Color,
    val onNumber: Color,
    val function: Color,
    val onFunction: Color,
    val operator: Color,
    val onOperator: Color,
    val equals: Color,
    val onEquals: Color,
)

private val Light = CalcColors(
    background = Color(0xFFF6F7F9),
    text = Color(0xFF1B1C1F),
    dim = Color(0xFF6B6F78),
    number = Color(0xFFFFFFFF),
    onNumber = Color(0xFF1B1C1F),
    function = Color(0xFFE3E6EC),
    onFunction = Color(0xFF2C3036),
    operator = Color(0xFFD5E2F1),
    onOperator = Color(0xFF1E4C7A),
    equals = Color(0xFF2F5F8F),
    onEquals = Color(0xFFFFFFFF),
)

private val Dark = CalcColors(
    background = Color(0xFF121316),
    text = Color(0xFFF1F2F4),
    dim = Color(0xFF9399A3),
    number = Color(0xFF26282D),
    onNumber = Color(0xFFF1F2F4),
    function = Color(0xFF34373E),
    onFunction = Color(0xFFDDE0E5),
    operator = Color(0xFF2B3A4C),
    onOperator = Color(0xFFB9D3EE),
    equals = Color(0xFF8DB8E3),
    onEquals = Color(0xFF0F2438),
)

val LocalCalcColors = staticCompositionLocalOf { Light }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val calc = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8DB8E3), onPrimary = Color(0xFF0F2438),
            background = calc.background, surface = calc.background,
            onBackground = calc.text, onSurface = calc.text,
            surfaceVariant = calc.number, onSurfaceVariant = calc.dim,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2F5F8F), onPrimary = Color.White,
            background = calc.background, surface = calc.background,
            onBackground = calc.text, onSurface = calc.text,
            surfaceVariant = calc.function, onSurfaceVariant = calc.dim,
        )
    }
    CompositionLocalProvider(LocalCalcColors provides calc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
