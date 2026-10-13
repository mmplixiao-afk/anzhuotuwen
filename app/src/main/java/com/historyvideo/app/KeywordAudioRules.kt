package com.historyvideo.app

/** Android counterpart of the PC keyword sound-effect trigger rules. */
internal object KeywordAudioRules {
    const val VERSION = 1
    const val FRAME_RATE = 30

    data class Trigger(val keyword: String, val shotIndex: Int, val charIndex: Int)
    data class Event(val keyword: String, val offset: Double, val duration: Double)

    private const val SENTENCE_END = "。？！!?；;\n\r"
    private const val CLOSING_QUOTES = "”’\"'」』）》】)]}〉〕〗〙〛"
    private const val OPENING_QUOTES = "“‘\"'「『([{（【"

    /** First fart word in the full narration, plus at most one opening interjection. */
    fun triggers(shotTexts: List<String>): List<Trigger> {
        val result = mutableListOf<Trigger>()
        var fartAdded = false
        shotTexts.forEachIndexed { shotIndex, text ->
            if (!fartAdded) {
                val firstFart = text.indexOf("放屁")
                if (firstFart >= 0) {
                    result += Trigger("放屁", shotIndex, firstFart)
                    fartAdded = true
                }
            }
        }
        openingInterjection(shotTexts)?.let(result::add)
        return result.sortedWith(compareBy<Trigger> { it.shotIndex }.thenBy { it.charIndex })
    }

    fun bgmStart(events: List<Event>): Double = events
        .filter { it.keyword == "放屁" }
        .minByOrNull { it.offset }
        ?.offset?.coerceAtLeast(0.0)
        ?: 0.0
    fun semanticCount(value: String): Int {
        var count = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val type = Character.getType(codePoint)
            if (Character.isLetter(codePoint) || type == Character.DECIMAL_DIGIT_NUMBER.toInt() ||
                type == Character.LETTER_NUMBER.toInt() || type == Character.OTHER_NUMBER.toInt()) count++
            index += Character.charCount(codePoint)
        }
        return count
    }

    private fun openingInterjection(shotTexts: List<String>): Trigger? {
        val spans = mutableListOf<Triple<Int, Int, Int>>()
        var cursor = 0
        shotTexts.forEachIndexed { index, text ->
            if (text.isNotEmpty()) {
                spans += Triple(cursor, cursor + text.length, index)
                cursor += text.length
            }
        }
        val narration = shotTexts.joinToString(separator = "")
        val sentences = sentenceRanges(narration)
        if (sentences.size < 2) return null
        val (bodyStart, bodyEnd) = sentences[1]
        val body = narration.substring(bodyStart, bodyEnd)
        val keyword = when {
            body.startsWith("他娘的") -> "他娘的"
            body.startsWith("哼") -> "哼"
            else -> return null
        }
        val span = spans.firstOrNull { bodyStart >= it.first && bodyStart < it.second } ?: return null
        return Trigger(keyword, span.third, bodyStart - span.first)
    }

    private fun sentenceRanges(value: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var index = 0
        while (index < value.length) {
            if (value[index] !in SENTENCE_END) {
                index++
                continue
            }
            val end = index
            while (index < value.length && value[index] in SENTENCE_END) index++
            while (index < value.length && value[index] in CLOSING_QUOTES) index++
            var contentStart = start
            while (contentStart < end && (value[contentStart].isWhitespace() || value[contentStart] in OPENING_QUOTES)) contentStart++
            if (contentStart < end) ranges += contentStart to end
            start = index
        }
        return ranges
    }
}