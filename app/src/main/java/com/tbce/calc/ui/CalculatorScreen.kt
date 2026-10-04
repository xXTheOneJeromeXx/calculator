package com.tbce.calc.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.AppController
import com.tbce.calc.CalcKey

private data class K(
    val label: String,
    val key: CalcKey,
    val style: KeyStyle,
    val description: String = label,
)

private val basic = listOf(
    listOf(K("AC", CalcKey.Clear, KeyStyle.FUNCTION, "clear"), K("⌫", CalcKey.Back, KeyStyle.FUNCTION, "backspace"),
        K("%", CalcKey.PostOp('%'), KeyStyle.FUNCTION, "percent"), K("÷", CalcKey.Operator('÷'), KeyStyle.OPERATOR, "divide")),
    listOf(K("7", CalcKey.Digit(7), KeyStyle.NUMBER), K("8", CalcKey.Digit(8), KeyStyle.NUMBER),
        K("9", CalcKey.Digit(9), KeyStyle.NUMBER), K("×", CalcKey.Operator('×'), KeyStyle.OPERATOR, "multiply")),
    listOf(K("4", CalcKey.Digit(4), KeyStyle.NUMBER), K("5", CalcKey.Digit(5), KeyStyle.NUMBER),
        K("6", CalcKey.Digit(6), KeyStyle.NUMBER), K("−", CalcKey.Operator('-'), KeyStyle.OPERATOR, "minus")),
    listOf(K("1", CalcKey.Digit(1), KeyStyle.NUMBER), K("2", CalcKey.Digit(2), KeyStyle.NUMBER),
        K("3", CalcKey.Digit(3), KeyStyle.NUMBER), K("+", CalcKey.Operator('+'), KeyStyle.OPERATOR, "plus")),
    listOf(K("±", CalcKey.Negate, KeyStyle.NUMBER, "plus minus"), K("0", CalcKey.Digit(0), KeyStyle.NUMBER),
        K(".", CalcKey.Dot, KeyStyle.NUMBER, "point"), K("=", CalcKey.Equals, KeyStyle.EQUALS, "equals")),
)

private fun scientific(degrees: Boolean) = listOf(
    listOf(K("(", CalcKey.LParen, KeyStyle.FUNCTION, "left parenthesis"), K(")", CalcKey.RParen, KeyStyle.FUNCTION, "right parenthesis"),
        K("x!", CalcKey.PostOp('!'), KeyStyle.FUNCTION, "factorial"),
        K(if (degrees) "Deg" else "Rad", CalcKey.Angle, KeyStyle.FUNCTION, if (degrees) "degrees" else "radians")),
    listOf(K("sin", CalcKey.Func("sin"), KeyStyle.FUNCTION), K("cos", CalcKey.Func("cos"), KeyStyle.FUNCTION),
        K("tan", CalcKey.Func("tan"), KeyStyle.FUNCTION), K("π", CalcKey.Const("pi"), KeyStyle.FUNCTION, "pi")),
    listOf(K("sin⁻¹", CalcKey.Func("asin"), KeyStyle.FUNCTION, "arcsine"), K("cos⁻¹", CalcKey.Func("acos"), KeyStyle.FUNCTION, "arccosine"),
        K("tan⁻¹", CalcKey.Func("atan"), KeyStyle.FUNCTION, "arctangent"), K("e", CalcKey.Const("e"), KeyStyle.FUNCTION, "e")),
    listOf(K("ln", CalcKey.Func("ln"), KeyStyle.FUNCTION, "natural log"), K("log", CalcKey.Func("log"), KeyStyle.FUNCTION),
        K("√", CalcKey.Func("sqrt"), KeyStyle.FUNCTION, "square root"), K("eˣ", CalcKey.Func("exp"), KeyStyle.FUNCTION, "e to the x")),
    listOf(K("x²", CalcKey.PostOp('²'), KeyStyle.FUNCTION, "square"), K("xʸ", CalcKey.Operator('^'), KeyStyle.FUNCTION, "power"),
        K("10ˣ", CalcKey.Func("tenx"), KeyStyle.FUNCTION, "ten to the x"), K("1/x", CalcKey.PostOp('i'), KeyStyle.FUNCTION, "reciprocal")),
)

@Composable
fun CalculatorScreen(app: AppController) {
    val colors = LocalCalcColors.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Display(app, Modifier.weight(if (landscape) 0.5f else 1f).fillMaxWidth(), landscape)
        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                Grid(scientific(app.degrees), app, Modifier.weight(1f), 18.sp)
                Grid(basic, app, Modifier.weight(1f), 22.sp)
            }
        } else {
            Grid(basic, app, Modifier.weight(1.5f).fillMaxWidth(), 30.sp)
        }
    }
}

@Composable
private fun Grid(rows: List<List<K>>, app: AppController, modifier: Modifier, fontSize: TextUnit) {
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        for (row in rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (k in row) KeyCell(k, app, fontSize)
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(k: K, app: AppController, fontSize: TextUnit) {
    Key(
        label = k.label,
        style = k.style,
        fontSize = fontSize,
        description = k.description,
        modifier = Modifier.weight(1f).fillMaxSize(),
        onHold = if (k.key == CalcKey.Equals) ({ app.onEqualsHeld() }) else null,
        onClick = { app.onKey(k.key) },
    )
}

@Composable
private fun Display(app: AppController, modifier: Modifier, landscape: Boolean) {
    val colors = LocalCalcColors.current
    Box(modifier) {
        if (!app.degrees) {
            Text("RAD", color = colors.dim, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart))
        }
        Column(
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            if (app.previous.isNotEmpty()) {
                Text(app.previous, color = colors.dim, fontSize = 18.sp, maxLines = 1, textAlign = TextAlign.End)
            }
            FittedText(app.display, maxSize = if (landscape) 44f else 72f)
            Text(
                app.preview,
                color = colors.dim,
                fontSize = 26.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Shrinks the text to fit one line where it can, then wraps. */
@Composable
private fun FittedText(text: String, maxSize: Float) {
    val colors = LocalCalcColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthSp = with(LocalDensity.current) { maxWidth.toSp().value }
        val size = (widthSp / (text.length * 0.6f)).coerceIn(28f, maxSize)
        Text(
            text,
            color = colors.text,
            fontSize = size.sp,
            lineHeight = (size * 1.15f).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.End,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
