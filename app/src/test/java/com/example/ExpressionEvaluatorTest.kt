package com.example

import com.example.tools.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEvaluatorTest {

    @Test
    fun testBasicArithmetic() {
        val res = ExpressionEvaluator.evaluate("2 + 3 * 4").getOrThrow()
        assertEquals(14.0, res, 0.001)
    }

    @Test
    fun testParenthesesAndPrecedence() {
        val res = ExpressionEvaluator.evaluate("(2 + 3) * 4").getOrThrow()
        assertEquals(20.0, res, 0.001)
    }

    @Test
    fun testPowerAndDecimals() {
        val res = ExpressionEvaluator.evaluate("2 ^ 3 + 1.5").getOrThrow()
        assertEquals(9.5, res, 0.001)
    }

    @Test
    fun testNegativeNumbers() {
        val res = ExpressionEvaluator.evaluate("-5 + 10").getOrThrow()
        assertEquals(5.0, res, 0.001)
    }

    @Test
    fun testDivisionByZero() {
        val res = ExpressionEvaluator.evaluate("10 / 0")
        assertTrue(res.isFailure)
    }
}
