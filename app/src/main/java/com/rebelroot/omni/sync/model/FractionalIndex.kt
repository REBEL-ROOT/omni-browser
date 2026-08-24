package com.rebelroot.omni.sync.model

object FractionalIndex {
    private const val BASE_DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun fromDensePosition(position: Long): String {
        require(position >= 0) { "Position must be non-negative: $position" }
        val prefixIndex = (position / BASE_DIGITS.length).toInt()
        val suffixIndex = (position % BASE_DIGITS.length).toInt()
        
        val prefixChar = if (prefixIndex < 26) {
            ('a'.code + prefixIndex).toChar()
        } else {
            ('A'.code + (prefixIndex - 26)).toChar()
        }
        val suffixChar = BASE_DIGITS[suffixIndex]
        return "$prefixChar$suffixChar"
    }

    fun generateBetween(prev: String?, next: String?): String {
        if (prev == null && next == null) return "a0"
        if (prev == null) {
            val nonNullNext = next!!
            return if (nonNullNext > "a0") {
                "a0"
            } else {
                generateBefore(nonNullNext)
            }
        }
        if (next == null) {
            return generateAfter(prev)
        }

        require(prev < next) { "prev ($prev) must be strictly less than next ($next)" }

        var i = 0
        while (i < prev.length && i < next.length && prev[i] == next[i]) {
            i++
        }

        val prefix = prev.substring(0, i)
        val pChar = if (i < prev.length) prev[i] else '0'
        val nChar = if (i < next.length) next[i] else ('z' + 1)

        val pVal = charToVal(pChar)
        val nVal = charToVal(nChar)

        return if (nVal - pVal > 1) {
            val midVal = pVal + (nVal - pVal) / 2
            prefix + valToChar(midVal)
        } else {
            if (i < prev.length) {
                prev + "V"
            } else {
                prefix + pChar + "V"
            }
        }
    }

    private fun generateBefore(key: String): String {
        val firstChar = key[0]
        return if (firstChar > 'A') {
            (firstChar.code - 1).toChar() + "V"
        } else {
            "0" + key
        }
    }

    private fun generateAfter(key: String): String {
        val lastChar = key.last()
        val valIndex = charToVal(lastChar)
        return if (valIndex < BASE_DIGITS.length - 1) {
            key.substring(0, key.length - 1) + valToChar(valIndex + 1)
        } else {
            key + "V"
        }
    }

    private fun charToVal(c: Char): Int {
        val idx = BASE_DIGITS.indexOf(c)
        return if (idx >= 0) idx else 0
    }

    private fun valToChar(v: Int): Char {
        val clamped = v.coerceIn(0, BASE_DIGITS.length - 1)
        return BASE_DIGITS[clamped]
    }
}
