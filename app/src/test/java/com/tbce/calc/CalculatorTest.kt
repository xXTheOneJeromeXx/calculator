package com.tbce.calc

import com.tbce.calc.calc.Calculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The brief's calculator test script (section 12): 40+ key sequences and the expected display.
 *
 * Key codes: digits, . + - * / ^ ( ) =, % percent, ! factorial, q square, r reciprocal,
 * n plus/minus, b backspace, c clear, d toggle deg/rad, S sin C cos T tan, L ln, G log,
 * R sqrt, P pi, E e.
 */
class CalculatorTest {

    private fun run(keys: String): Calculator {
        val c = Calculator()
        for (k in keys) when (k) {
            in '0'..'9' -> c.digit(k - '0')
            '.' -> c.decimal()
            '+' -> c.operator('+')
            '-' -> c.operator('-')
            '*' -> c.operator('×')
            '/' -> c.operator('÷')
            '^' -> c.operator('^')
            '(' -> c.openParen()
            ')' -> c.closeParen()
            '=' -> c.equals()
            '%' -> c.postfix('%')
            '!' -> c.postfix('!')
            'q' -> c.postfix('²')
            'r' -> c.postfix('i')
            'n' -> c.negate()
            'b' -> c.backspace()
            'c' -> c.clear()
            'd' -> c.toggleAngle()
            'S' -> c.function("sin")
            'C' -> c.function("cos")
            'T' -> c.function("tan")
            'L' -> c.function("ln")
            'G' -> c.function("log")
            'R' -> c.function("sqrt")
            'P' -> c.constant("pi")
            'E' -> c.constant("e")
            ' ' -> {}
            else -> error("bad key $k")
        }
        return c
    }

    private fun check(keys: String, expected: String) = assertEquals(keys, expected, run(keys).display)

    @Test fun basics() {
        check("", "0")
        check("2+3=", "5")
        check("7-10=", "−3")
        check("6*7=", "42")
        check("8/2=", "4")
        check("1/3=", "0.333333333333")
        check("2/3=", "0.666666666667")
        check("1/3*3=", "1")
    }

    @Test fun precision() {
        check(".1+.2=", "0.3")
        check("0.1*3=", "0.3")
        check("1.1*1.1=", "1.21")
        check("1-0.9=", "0.1")
        check("100*1.15=", "115")
    }

    @Test fun precedence() {
        check("2+3*4=", "14")
        check("2*3+4=", "10")
        check("10-4/2=", "8")
        check("(2+3)*4=", "20")
        check("2(3+4)=", "14")
        check("2^3^2=", "512")
        check("((2+3)=", "5")
    }

    @Test fun chainingAndRepeat() {
        check("2+3==", "8")
        check("2+3===", "11")
        check("10-2==", "6")
        check("3*2==", "12")
        check("2+3=*4=", "20")
        check("5=", "5")
        check("2+3=7", "7")
        check("2+*3=", "6")
    }

    @Test fun percent() {
        check("50%", "50%")
        check("50%=", "0.5")
        check("200+10%=", "220")
        check("200-10%=", "180")
        check("200*10%=", "20")
        check("50/10%=", "500")
    }

    @Test fun signsAndEditing() {
        check("5n", "−5")
        check("5nn", "5")
        check("3*n4=", "−12")
        check("3*-4=", "−12")
        check("2+3=n", "−5")
        check("123b", "12")
        check("123bbb", "0")
        check("12+c", "0")
        check("1..5", "1.5")
        check("007", "7")
        check("1234567", "1,234,567")
        check("1234567890123456789", "123,456,789,012,345")
    }

    @Test fun errors() {
        check("5/0=", "Can't divide by 0")
        check("5/0=7", "7")
        check("0/0=", "Can't divide by 0")
        check("R1n=", "Error")
        check("L0=", "Error")
        check("3.5!=", "Error")
        check("0r=", "Can't divide by 0")
    }

    @Test fun formatting() {
        check("99999999*99999999=", "9.9999998E15")
        check("1/1000000000=", "1E−9".replace("−", "-"))
        check("123456789012*10=", "1.23456789012E12")
        check("2^0.5=", "1.41421356237")
    }

    @Test fun scientific() {
        check("S30=", "0.5")
        check("S180=", "0")
        check("C60=", "0.5")
        check("T45=", "1")
        check("T90=", "Error")
        check("dSP=", "0")
        check("G1000=", "3")
        check("LE=", "1")
        check("R16=", "4")
        check("5!=", "120")
        check("0!=", "1")
        check("4q=", "16")
        check("4r=", "0.25")
        check("2P=", "6.28318530718")
    }

    @Test fun preview() {
        val c = run("2+3")
        assertEquals("5", c.preview)
        assertEquals("", run("2+").preview)
        assertEquals("", run("23").preview)
    }
}
