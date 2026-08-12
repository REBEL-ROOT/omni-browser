/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.ai.models

import kotlin.math.floor

/**
 * Tiny, dependency-free JSON reader/writer used for the model catalog and the
 * installed-model sidecar files.
 *
 * This avoids relying on `org.json` (which is only a stub on the JVM unit-test
 * classpath) so the model platform is fully unit-testable. It intentionally
 * covers only the subset of JSON we emit/consume (objects, arrays, strings,
 * numbers, booleans, null) and is not a general-purpose parser.
 */
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    object Null : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
}

object Json {
    fun parse(text: String): JsonValue = Parser(text).parse()

    fun write(value: JsonValue): String = Writer().write(value)

    // ── Builder helpers ──────────────────────────────────────────────────────
    fun obj(vararg pairs: Pair<String, JsonValue?>): JsonValue.Obj =
        JsonValue.Obj(pairs.mapNotNull { (k, v) -> if (v == null) null else k to v }.toMap())

    fun str(v: String) = JsonValue.Str(v)
    fun num(v: Long) = JsonValue.Num(v.toDouble())
    fun num(v: Double) = JsonValue.Num(v)
    fun bool(v: Boolean) = JsonValue.Bool(v)
    fun nil() = JsonValue.Null
    fun arr(items: List<JsonValue>) = JsonValue.Arr(items)
}

// ── Accessors (top-level extensions so `obj.str("x")` reads naturally) ───────
fun JsonValue.Obj.str(field: String): String? =
    (fields[field] as? JsonValue.Str)?.value

fun JsonValue.Obj.strOr(field: String, default: String): String =
    str(field) ?: default

fun JsonValue.Obj.bool(field: String): Boolean? =
    (fields[field] as? JsonValue.Bool)?.value

fun JsonValue.Obj.long(field: String): Long? =
    (fields[field] as? JsonValue.Num)?.value?.let { if (floor(it) == it) it.toLong() else null }

fun JsonValue.Obj.longOr(field: String, default: Long): Long =
    long(field) ?: default

fun JsonValue.Obj.obj(field: String): JsonValue.Obj? =
    fields[field] as? JsonValue.Obj

fun JsonValue.Obj.array(field: String): List<JsonValue>? =
    (fields[field] as? JsonValue.Arr)?.items

private class Parser(private val s: String) {
    private var i = 0

    fun parse(): JsonValue {
        skipWs()
        val v = readValue()
        skipWs()
        if (i != s.length) throw IllegalArgumentException("Trailing characters at $i")
        return v
    }

    private fun skipWs() {
        while (i < s.length) {
            val c = s[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++ else break
        }
    }

    private fun readValue(): JsonValue {
        skipWs()
        if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (val c = s[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonValue.Str(readString())
            't', 'f' -> readBoolean()
            'n' -> readNull()
            else -> readNumber()
        }
    }

    private fun readObject(): JsonValue.Obj {
        expect('{')
        val fields = LinkedHashMap<String, JsonValue>()
        skipWs()
        if (peek() == '}') { i++; return JsonValue.Obj(fields) }
        while (true) {
            skipWs()
            if (peek() != '"') throw IllegalArgumentException("Expected key string at $i")
            val key = readString()
            skipWs()
            expect(':')
            val value = readValue()
            fields[key] = value
            skipWs()
            when (peek()) {
                ',' -> { i++; continue }
                '}' -> { i++; break }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $i")
            }
        }
        return JsonValue.Obj(fields)
    }

    private fun readArray(): JsonValue.Arr {
        expect('[')
        val items = mutableListOf<JsonValue>()
        skipWs()
        if (peek() == ']') { i++; return JsonValue.Arr(items) }
        while (true) {
            items.add(readValue())
            skipWs()
            when (peek()) {
                ',' -> { i++; continue }
                ']' -> { i++; break }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $i")
            }
        }
        return JsonValue.Arr(items)
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (i >= s.length) throw IllegalArgumentException("Unterminated escape")
                    val e = s[i++]
                    sb.append(
                        when (e) {
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Bad unicode escape")
                                val code = s.substring(i, i + 4).toInt(16)
                                i += 4
                                code.toChar()
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    )
                }
                c.code < 0x20 -> throw IllegalArgumentException("Control char in string")
                else -> sb.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun readBoolean(): JsonValue {
        if (s.startsWith("true", i)) { i += 4; return JsonValue.Bool(true) }
        if (s.startsWith("false", i)) { i += 5; return JsonValue.Bool(false) }
        throw IllegalArgumentException("Invalid literal at $i")
    }

    private fun readNull(): JsonValue {
        if (s.startsWith("null", i)) { i += 4; return JsonValue.Null }
        throw IllegalArgumentException("Invalid literal at $i")
    }

    private fun readNumber(): JsonValue.Num {
        val start = i
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9' || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++
            else break
        }
        if (i == start) throw IllegalArgumentException("Invalid number at $i")
        val d = s.substring(start, i).toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number '${s.substring(start, i)}'")
        return JsonValue.Num(d)
    }

    private fun peek() = if (i < s.length) s[i] else '\u0000'
    private fun expect(c: Char) {
        if (i >= s.length || s[i] != c) throw IllegalArgumentException("Expected '$c' at $i")
        i++
    }
}

private class Writer {
    private val sb = StringBuilder()
    fun write(v: JsonValue): String {
        append(v)
        return sb.toString()
    }
    private fun append(v: JsonValue) {
        when (v) {
            is JsonValue.Str -> { sb.append('"'); escape(v.value); sb.append('"') }
            is JsonValue.Num -> sb.append(v.value.toString())
            is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
            is JsonValue.Null -> sb.append("null")
            is JsonValue.Arr -> {
                sb.append('[')
                v.items.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(',')
                    append(item)
                }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                v.fields.entries.forEachIndexed { idx, (k, item) ->
                    if (idx > 0) sb.append(',')
                    sb.append('"'); escape(k); sb.append('"'); sb.append(':')
                    append(item)
                }
                sb.append('}')
            }
        }
    }
    private fun escape(s: String) {
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> sb.append(c)
            }
        }
    }
}
