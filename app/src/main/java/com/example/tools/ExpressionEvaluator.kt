package com.example.tools

import kotlin.math.pow

object ExpressionEvaluator {

    /**
     * Safely evaluates arithmetic expressions without eval() or runtime code execution.
     * Supports: +, -, *, /, %, ^, parentheses, negative numbers, floats.
     */
    fun evaluate(expression: String): Result<Double> {
        return try {
            val sanitized = expression.replace("\\s+".toRegex(), "")
            if (sanitized.isEmpty()) {
                return Result.failure(IllegalArgumentException("Expression is empty"))
            }
            val parser = Parser(sanitized)
            val result = parser.parseExpression()
            if (parser.hasNext()) {
                Result.failure(IllegalArgumentException("Unexpected character at end: '${parser.peek()}'"))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun hasNext(): Boolean = pos < src.length
        fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'
        private fun consume(): Char = src[pos++]

        fun parseExpression(): Double {
            var value = parseTerm()
            while (hasNext()) {
                when (peek()) {
                    '+' -> {
                        consume()
                        value += parseTerm()
                    }
                    '-' -> {
                        consume()
                        value -= parseTerm()
                    }
                    else -> break
                }
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (hasNext()) {
                when (peek()) {
                    '*' -> {
                        consume()
                        value *= parseFactor()
                    }
                    '/' -> {
                        consume()
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Division by zero")
                        value /= denom
                    }
                    '%' -> {
                        consume()
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Modulo by zero")
                        value %= denom
                    }
                    else -> break
                }
            }
            return value
        }

        private fun parseFactor(): Double {
            var value = parsePrimary()
            if (hasNext() && peek() == '^') {
                consume()
                val exponent = parseFactor() // Right associative for power
                value = value.pow(exponent)
            }
            return value
        }

        private fun parsePrimary(): Double {
            if (!hasNext()) throw IllegalArgumentException("Unexpected end of expression")

            // Handle unary plus/minus
            if (peek() == '+') {
                consume()
                return parsePrimary()
            }
            if (peek() == '-') {
                consume()
                return -parsePrimary()
            }

            // Handle parentheses
            if (peek() == '(') {
                consume()
                val inner = parseExpression()
                if (!hasNext() || consume() != ')') {
                    throw IllegalArgumentException("Missing closing parenthesis ')'")
                }
                return inner
            }

            // Parse number (integer or decimal)
            val start = pos
            var seenDecimal = false
            while (hasNext()) {
                val c = peek()
                if (c.isDigit()) {
                    consume()
                } else if (c == '.' && !seenDecimal) {
                    seenDecimal = true
                    consume()
                } else {
                    break
                }
            }

            if (start == pos) {
                throw IllegalArgumentException("Expected number or '(' at position $pos (found '${peek()}')")
            }

            val numStr = src.substring(start, pos)
            return numStr.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number format: $numStr")
        }
    }
}
