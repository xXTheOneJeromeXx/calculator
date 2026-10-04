package com.tbce.calc.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Expression calculator with normal precedence, using BigDecimal so 0.1 + 0.2 is 0.3.
 * The UI calls one method per key and reads [display], [preview] and [previous].
 */
class Calculator {

    private sealed interface Tok
    private data class Num(val text: String, val neg: Boolean = false) : Tok
    private data class Op(val c: Char) : Tok
    private data object LParen : Tok
    private data object RParen : Tok
    private data class Fn(val name: String) : Tok
    private data class Const(val name: String) : Tok
    private data class Post(val c: Char) : Tok
    private data class Ans(val value: BigDecimal) : Tok

    private val tokens = mutableListOf<Tok>()
    private var result: BigDecimal? = null
    private var justEvaluated = false
    private var repeat: Pair<Char, BigDecimal>? = null
    private var error: String? = null

    /** Expression that produced the current result, shown small above it. */
    var previous: String = ""
        private set

    var degrees: Boolean = true
        private set

    val display: String
        get() = when {
            error != null -> error!!
            justEvaluated -> format(result!!)
            tokens.isEmpty() -> "0"
            else -> render(tokens)
        }

    /** Live result while typing, or "" when there is nothing useful to show. */
    val preview: String
        get() {
            if (error != null || justEvaluated || tokens.isEmpty()) return ""
            if (tokens.none { it is Op || it is Fn || it is Post || it is Const }) return ""
            if (tokens.last() is Op || tokens.last().let { it is Num && it.text.isEmpty() }) return ""
            return try {
                format(evaluate(closed(tokens)).value)
            } catch (e: Exception) {
                ""
            }
        }

    val isError: Boolean get() = error != null

    fun clear() {
        tokens.clear()
        result = null
        justEvaluated = false
        repeat = null
        error = null
        previous = ""
    }

    fun toggleAngle() {
        degrees = !degrees
    }

    fun digit(d: Int) {
        startFreshIfNeeded()
        val last = tokens.lastOrNull()
        if (last is Num) {
            if (digitCount(last.text) >= MAX_DIGITS) return
            val t = if (last.text == "0") "$d" else last.text + d
            tokens[tokens.lastIndex] = last.copy(text = t)
        } else {
            implicitMultiply()
            tokens += Num("$d")
        }
    }

    fun decimal() {
        startFreshIfNeeded()
        val last = tokens.lastOrNull()
        if (last is Num) {
            if ('.' in last.text) return
            tokens[tokens.lastIndex] = last.copy(text = (last.text.ifEmpty { "0" }) + ".")
        } else {
            implicitMultiply()
            tokens += Num("0.")
        }
    }

    /** '+', '-', '×', '÷' or '^'. */
    fun operator(c: Char) {
        if (error != null) clear()
        if (justEvaluated) continueFromResult()
        val last = tokens.lastOrNull()
        when {
            last == null -> if (c == '-') tokens += Num("", neg = true) else {
                tokens += Num("0"); tokens += Op(c)
            }
            last is Num && last.text.isEmpty() -> {
                // A lone minus sign: an operator replaces it unless there is something before it.
                if (c != '-' && tokens.size >= 2 && tokens[tokens.lastIndex - 1] is Op) {
                    tokens.removeAt(tokens.lastIndex)
                    tokens[tokens.lastIndex] = Op(c)
                }
            }
            last is Op -> {
                if (c == '-' && last.c != '+' && last.c != '-') tokens += Num("", neg = true)
                else tokens[tokens.lastIndex] = Op(c)
            }
            last is LParen || last is Fn -> if (c == '-') tokens += Num("", neg = true)
            else -> tokens += Op(c)
        }
    }

    /** '%', '!', '²' or '⁻¹'. */
    fun postfix(c: Char) {
        if (error != null) return
        if (justEvaluated) continueFromResult()
        if (endsWithValue()) tokens += Post(c)
    }

    fun negate() {
        if (error != null) return
        if (justEvaluated) {
            result = result!!.negate()
            return
        }
        val last = tokens.lastOrNull()
        when {
            last is Num -> {
                if (last.text.isEmpty()) tokens.removeAt(tokens.lastIndex)
                else tokens[tokens.lastIndex] = last.copy(neg = !last.neg)
            }
            last == null || last is Op || last is LParen || last is Fn -> tokens += Num("", neg = true)
        }
    }

    fun openParen() {
        startFreshIfNeeded()
        implicitMultiply()
        tokens += LParen
    }

    fun closeParen() {
        if (error != null || justEvaluated) return
        if (openCount(tokens) > 0 && endsWithValue()) tokens += RParen
    }

    /** sin cos tan asin acos atan ln log sqrt exp tenx */
    fun function(name: String) {
        startFreshIfNeeded()
        implicitMultiply()
        tokens += Fn(name)
    }

    /** "pi" or "e". */
    fun constant(name: String) {
        startFreshIfNeeded()
        implicitMultiply()
        tokens += Const(name)
    }

    fun backspace() {
        if (error != null || justEvaluated) {
            clear(); return
        }
        val last = tokens.lastOrNull() ?: return
        if (last is Num && last.text.isNotEmpty()) {
            val t = last.text.dropLast(1)
            if (t.isEmpty() && !last.neg) tokens.removeAt(tokens.lastIndex)
            else tokens[tokens.lastIndex] = last.copy(text = t)
        } else {
            tokens.removeAt(tokens.lastIndex)
        }
    }

    fun equals() {
        if (error != null) return
        if (justEvaluated) {
            val (op, rhs) = repeat ?: return
            val lhs = result!!
            try {
                val v = binary(op, lhs, rhs)
                previous = "${format(lhs)}${opSymbol(op)}${format(rhs)}="
                result = v
            } catch (e: Exception) {
                fail(e)
            }
            return
        }
        while (tokens.isNotEmpty() && (tokens.last() is Op || tokens.last().let { it is Num && it.text.isEmpty() })) {
            tokens.removeAt(tokens.lastIndex)
        }
        if (tokens.isEmpty()) return
        val toks = closed(tokens)
        try {
            val r = evaluate(toks)
            previous = render(toks) + "="
            result = r.value
            repeat = r.repeat
            tokens.clear()
            justEvaluated = true
        } catch (e: Exception) {
            fail(e)
        }
    }

    // ---- input helpers ----

    private fun fail(e: Exception) {
        tokens.clear()
        justEvaluated = false
        result = null
        repeat = null
        error = if (e is DivideByZero) "Can't divide by 0" else "Error"
    }

    private fun startFreshIfNeeded() {
        if (error != null || justEvaluated) {
            val prev = previous
            clear()
            previous = if (justEvaluated) prev else ""
        }
    }

    private fun continueFromResult() {
        val r = result!!
        clear()
        tokens += Ans(r)
    }

    private fun endsWithValue(): Boolean = when (val last = tokens.lastOrNull()) {
        is Num -> last.text.isNotEmpty()
        is RParen, is Const, is Post, is Ans -> true
        else -> false
    }

    private fun implicitMultiply() {
        if (endsWithValue()) tokens += Op('×')
    }

    private fun openCount(ts: List<Tok>) = ts.count { it is LParen || it is Fn } - ts.count { it is RParen }

    private fun closed(ts: List<Tok>): List<Tok> {
        val out = ts.toMutableList()
        repeat(openCount(ts)) { out += RParen }
        return out
    }

    // ---- rendering ----

    private fun render(ts: List<Tok>): String = buildString {
        for (t in ts) append(
            when (t) {
                is Num -> (if (t.neg) "−" else "") + group(t.text)
                is Op -> opSymbol(t.c)
                LParen -> "("
                RParen -> ")"
                is Fn -> FN_LABELS.getValue(t.name)
                is Const -> if (t.name == "pi") "π" else "e"
                is Post -> t.c.toString().replace("i", "⁻¹")
                is Ans -> format(t.value)
            }
        )
    }

    private fun opSymbol(c: Char) = when (c) {
        '-' -> "−"
        else -> c.toString()
    }

    // ---- parsing and evaluation ----

    private class DivideByZero : ArithmeticException()

    private sealed interface Node
    private class Lit(val v: BigDecimal) : Node
    private class Bin(val op: Char, val l: Node, val r: Node) : Node
    private class Neg(val n: Node) : Node
    private class Postfix(val c: Char, val n: Node) : Node
    private class Call(val name: String, val arg: Node) : Node

    private class Result(val value: BigDecimal, val repeat: Pair<Char, BigDecimal>?)

    private fun evaluate(ts: List<Tok>): Result {
        val p = Parser(ts)
        val root = p.expr()
        if (!p.done()) throw IllegalArgumentException()
        val value = eval(root)
        val rep = if (root is Bin && root.r !is Postfix) root.op to eval(root.r) else null
        return Result(value, rep)
    }

    private inner class Parser(val ts: List<Tok>) {
        var i = 0
        fun done() = i == ts.size
        private fun peek() = ts.getOrNull(i)

        fun expr(): Node {
            var n = term()
            while (true) {
                val t = peek()
                if (t is Op && (t.c == '+' || t.c == '-')) { i++; n = Bin(t.c, n, term()) } else return n
            }
        }

        private fun term(): Node {
            var n = power()
            while (true) {
                val t = peek()
                if (t is Op && (t.c == '×' || t.c == '÷')) { i++; n = Bin(t.c, n, power()) } else return n
            }
        }

        private fun power(): Node {
            val base = unary()
            val t = peek()
            return if (t is Op && t.c == '^') { i++; Bin('^', base, power()) } else base
        }

        private fun unary(): Node {
            val t = peek()
            if (t is Num && t.text.isEmpty() && t.neg) { i++; return Neg(unary()) }
            return postfixed()
        }

        private fun postfixed(): Node {
            var n = primary()
            while (true) {
                val t = peek()
                if (t is Post) { i++; n = Postfix(t.c, n) } else return n
            }
        }

        private fun primary(): Node {
            val t = peek() ?: throw IllegalArgumentException()
            i++
            return when (t) {
                is Num -> {
                    val v = BigDecimal(t.text.trimEnd('.'))
                    Lit(if (t.neg) v.negate() else v)
                }
                is Const -> Lit(if (t.name == "pi") PI else E)
                is Ans -> Lit(t.value)
                LParen -> expr().also { expect() }
                is Fn -> Call(t.name, expr()).also { expect() }
                else -> throw IllegalArgumentException()
            }
        }

        private fun expect() {
            if (peek() != RParen) throw IllegalArgumentException()
            i++
        }
    }

    private fun eval(n: Node): BigDecimal = when (n) {
        is Lit -> n.v
        is Neg -> eval(n.n).negate()
        is Bin -> {
            val l = eval(n.l)
            if ((n.op == '+' || n.op == '-') && n.r is Postfix && n.r.c == '%') {
                // a + b% means a plus b percent of a, as on a desk calculator.
                val pct = l.multiply(eval(n.r.n), MC).divide(HUNDRED, MC)
                if (n.op == '+') l.add(pct, MC) else l.subtract(pct, MC)
            } else {
                binary(n.op, l, eval(n.r))
            }
        }
        is Postfix -> {
            val v = eval(n.n)
            when (n.c) {
                '%' -> v.divide(HUNDRED, MC)
                '²' -> v.multiply(v, MC)
                '!' -> factorial(v)
                'i' -> binary('÷', BigDecimal.ONE, v)
                else -> throw IllegalArgumentException()
            }
        }
        is Call -> call(n.name, eval(n.arg))
    }.also { check(it) }

    private fun check(v: BigDecimal) {
        if (v.signum() != 0 && (v.precision() - v.scale() - 1) > 9_999) throw ArithmeticException()
    }

    private fun binary(op: Char, l: BigDecimal, r: BigDecimal): BigDecimal = when (op) {
        '+' -> l.add(r, MC)
        '-' -> l.subtract(r, MC)
        '×' -> l.multiply(r, MC)
        '÷' -> if (r.signum() == 0) throw DivideByZero() else l.divide(r, MC)
        '^' -> pow(l, r)
        else -> throw IllegalArgumentException()
    }.also { check(it) }

    private fun pow(b: BigDecimal, e: BigDecimal): BigDecimal {
        val isInt = e.signum() == 0 || e.stripTrailingZeros().scale() <= 0
        if (isInt && e.abs() <= BigDecimal(9_999)) {
            val k = e.toInt()
            return if (k >= 0) b.pow(k, MC)
            else if (b.signum() == 0) throw DivideByZero()
            else BigDecimal.ONE.divide(b.pow(-k, MC), MC)
        }
        return fromDouble(Math.pow(b.toDouble(), e.toDouble()))
    }

    private fun factorial(v: BigDecimal): BigDecimal {
        val isInt = v.signum() == 0 || v.stripTrailingZeros().scale() <= 0
        if (!isInt || v.signum() < 0 || v > BigDecimal(3_000)) throw ArithmeticException()
        var r = BigDecimal.ONE
        for (k in 2..v.toInt()) r = r.multiply(BigDecimal(k), MC)
        return r
    }

    private fun call(name: String, x: BigDecimal): BigDecimal {
        val d = x.toDouble()
        val toRad = if (degrees) Math.PI / 180 else 1.0
        return when (name) {
            "sin" -> trig(Math.sin(d * toRad))
            "cos" -> trig(Math.cos(d * toRad))
            "tan" -> {
                if (degrees && x.remainder(BigDecimal(180)).abs().compareTo(BigDecimal(90)) == 0) throw ArithmeticException()
                trig(Math.tan(d * toRad))
            }
            "asin" -> if (d < -1 || d > 1) throw ArithmeticException() else trig(Math.asin(d) / toRad)
            "acos" -> if (d < -1 || d > 1) throw ArithmeticException() else trig(Math.acos(d) / toRad)
            "atan" -> trig(Math.atan(d) / toRad)
            "ln" -> if (x.signum() <= 0) throw ArithmeticException() else fromDouble(Math.log(d))
            "log" -> if (x.signum() <= 0) throw ArithmeticException() else fromDouble(Math.log10(d))
            "sqrt" -> if (x.signum() < 0) throw ArithmeticException() else fromDouble(Math.sqrt(d))
            "exp" -> fromDouble(Math.exp(d))
            "tenx" -> pow(BigDecimal.TEN, x)
            else -> throw IllegalArgumentException()
        }
    }

    /** Rounds away floating-point noise such as sin(180°) = 1.2E-16. */
    private fun trig(d: Double): BigDecimal = fromDouble(d).setScale(13, RoundingMode.HALF_EVEN)

    private fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN() || d.isInfinite()) throw ArithmeticException()
        return BigDecimal(d.toString()).round(MathContext(15, RoundingMode.HALF_EVEN))
    }

    companion object {
        const val MAX_DIGITS = 15
        private const val DISPLAY_DIGITS = 12
        private val MC = MathContext(34, RoundingMode.HALF_EVEN)
        private val HUNDRED = BigDecimal(100)
        private val PI = BigDecimal("3.141592653589793238462643383279503")
        private val E = BigDecimal("2.718281828459045235360287471352662")

        private val FN_LABELS = mapOf(
            "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
            "asin" to "sin⁻¹(", "acos" to "cos⁻¹(", "atan" to "tan⁻¹(",
            "ln" to "ln(", "log" to "log(", "sqrt" to "√(", "exp" to "e^(", "tenx" to "10^(",
        )

        private fun digitCount(s: String) = s.count { it.isDigit() }

        /** Adds thousands separators to the integer part of a typed number. */
        fun group(s: String): String {
            if (s.isEmpty()) return s
            val dot = s.indexOf('.')
            val int = if (dot < 0) s else s.substring(0, dot)
            val frac = if (dot < 0) "" else s.substring(dot)
            val sb = StringBuilder()
            for ((k, ch) in int.withIndex()) {
                if (k > 0 && (int.length - k) % 3 == 0) sb.append(',')
                sb.append(ch)
            }
            return sb.append(frac).toString()
        }

        fun format(v: BigDecimal): String {
            if (v.signum() == 0) return "0"
            val r = v.round(MathContext(DISPLAY_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
            if (r.signum() == 0) return "0"
            val exp = r.precision() - r.scale() - 1
            val sign = if (r.signum() < 0) "−" else ""
            val a = r.abs()
            return if (exp >= DISPLAY_DIGITS || exp < -8) {
                val mantissa = a.movePointLeft(exp).stripTrailingZeros().toPlainString()
                "$sign${mantissa}E$exp"
            } else {
                sign + group(a.toPlainString())
            }
        }
    }
}
