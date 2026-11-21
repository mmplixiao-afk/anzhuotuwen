package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Inline polyphonic pronunciation for narration text.
 *
 * Authors write 银行(yin2hang2) or 银(yin2)行(hang2). The editable script keeps
 * those marks. TTS receives the clean sentence plus Volcano's pronunciation_dict;
 * subtitles and other spoken displays use the clean sentence only.
 *
 * Syllable count selects the word by walking backward from the parenthesis:
 * 银行(yin2hang2) marks 银行, and a single syllable marks only the character
 * immediately before the parenthesis. Adjacent per-character marks merge into
 * one dictionary entry so the engine's longest match stays on that word.
 */
internal object PronunciationMarkup {
    const val HINT =
        "多音字写在配音正文里，只影响朗读；字幕和列表仍显示去掉标注后的原文。整词写银行(yin2hang2)，或逐字写银(yin2)行(hang2)。从括号往前数，几个音节就标注几个字。声调用 1–4，轻声用 5。括号要紧挨汉字，中英文括号都可以。"

    private const val MAX_ENTRIES = 5000
    private const val MAX_WORD_CHARS = 9
    private const val TONE_MARKS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹḿ"

    private val SYLLABLES: Set<String> = """
        a ai an ang ao e ei en eng er o ou
        ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
        pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
        ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
        fa fan fang fei fen feng fiao fo fou fu
        da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
        ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
        na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nv nuan nve nuo
        la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu lv luan lve lun luo
        ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
        ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
        ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
        ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
        qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
        xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
        zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo
        cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo
        sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo
        ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
        za zai zan zang zao ze zei zen zeng zi zong zou zu zuan zui zun zuo
        ca cai can cang cao ce cen ceng ci cong cou cu cuan cui cun cuo
        sa sai san sang sao se sen seng si song sou su suan sui sun suo
        ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
        wa wai wan wang wei wen weng wo wu
        n ng m
    """.trim().split(Regex("\\s+")).toSet()

    internal data class Syllable(val pinyin: String, val tone: Int)

    internal data class Mark(
        val start: Int,
        val end: Int,
        val word: String,
        val syllables: List<Syllable>,
        val annotations: List<IntRange>
    )

    internal data class Parsed(
        val cleanText: String,
        val marks: List<Mark>,
        val rules: List<String>
    )

    fun parse(text: String): Parsed {
        val found = mutableListOf<Mark>()
        var index = 0
        while (index < text.length) {
            val open = text.indexOfAny(charArrayOf('(', '（'), index)
            if (open < 0) break
            val close = text.indexOfAny(charArrayOf(')', '）'), open + 1)
            if (close < 0) {
                val rest = text.substring(open + 1)
                if (isUnclosedPronunciation(rest)) {
                    throw IllegalArgumentException("读音标注缺少右括号：「${snippet(text, open)}」")
                }
                break
            }
            val body = text.substring(open + 1, close)
            val syllables = classify(body, snippet(text, open))
            if (syllables == null) {
                index = open + 1
                continue
            }
            if (syllables.size > MAX_WORD_CHARS) {
                throw IllegalArgumentException("一次最多标注 $MAX_WORD_CHARS 个字：「${snippet(text, open)}」")
            }
            val wordStart = wordStartBefore(text, open, syllables.size, snippet(text, open))
            found += Mark(
                start = wordStart,
                end = close + 1,
                word = text.substring(wordStart, open),
                syllables = syllables,
                annotations = listOf(open until close + 1)
            )
            index = close + 1
        }
        val merged = mergeAdjacent(found)
        return Parsed(stripAnnotations(text, found), merged, rulesOf(merged))
    }

    fun clean(text: String): String = parse(text).cleanText

    /**
     * Cleans subtitle slices of a marked sentence. A slice that breaks a mark, or
     * slices that no longer join back into the clean sentence, are dropped so the
     * caller can re-split the clean sentence instead of painting pinyin.
     */
    fun cleanChunks(chunks: List<String>, cleanSpoken: String): List<String> {
        if (chunks.isEmpty()) return emptyList()
        val cleaned = ArrayList<String>(chunks.size)
        for (chunk in chunks) {
            val clean = try {
                clean(chunk)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
            cleaned += clean
        }
        return if (cleaned.joinToString("") == cleanSpoken) cleaned else emptyList()
    }

    /** Clean text when the marks are valid; the original text when they are not, so editors stay visible. */
    fun display(text: String): String = try {
        clean(text)
    } catch (_: IllegalArgumentException) {
        text
    }

    /**
     * Puts pronunciation_dict.tone into an existing additions object.
     * Other additions keys, and any other pronunciation_dict keys, stay in place.
     * Returns the clean sentence that must be sent as req_params.text.
     */
    fun mergeIntoAdditions(additions: JSONObject, markedText: String): String {
        val parsed = parse(markedText)
        if (parsed.rules.isEmpty()) return parsed.cleanText
        val dict = additions.optJSONObject("pronunciation_dict") ?: JSONObject()
        if (dict.has("tone") && dict.opt("tone") !is JSONArray) {
            throw IllegalArgumentException("已有发音词典 tone 不是数组，无法合并读音标注")
        }
        val existing = dict.optJSONArray("tone") ?: JSONArray()
        val opaque = mutableListOf<String>()
        val byWord = linkedMapOf<String, String>()
        for (entry in 0 until existing.length()) {
            val rule = existing.optString(entry, "")
            if (rule.isEmpty()) continue
            val slash = rule.indexOf('/')
            if (slash <= 0) {
                opaque += rule
                continue
            }
            putRule(byWord, rule.substring(0, slash), rule.substring(slash + 1), existing = true)
        }
        for (rule in parsed.rules) {
            val slash = rule.indexOf('/')
            putRule(byWord, rule.substring(0, slash), rule.substring(slash + 1), existing = false)
        }
        if (opaque.size + byWord.size > MAX_ENTRIES) {
            throw IllegalArgumentException("发音词典最多 $MAX_ENTRIES 条，这段配音里的读音标注太多了")
        }
        val tone = JSONArray()
        opaque.forEach { tone.put(it) }
        byWord.forEach { (word, reading) -> tone.put("$word/$reading") }
        dict.put("tone", tone)
        additions.put("pronunciation_dict", dict)
        return parsed.cleanText
    }

    private fun putRule(byWord: LinkedHashMap<String, String>, word: String, reading: String, existing: Boolean) {
        val previous = byWord[word]
        if (previous == null) {
            byWord[word] = reading
        } else if (previous != reading) {
            val source = if (existing) "已有发音规则里" else "这段配音里"
            throw IllegalArgumentException(
                "$source「$word」有两种读音：$previous 和 $reading。同一个词只能标一种；要区分就标更长的词，例如银行(yin2hang2)和行走(xing2zou3)"
            )
        }
    }

    private fun mergeAdjacent(found: List<Mark>): List<Mark> {
        val merged = mutableListOf<Mark>()
        for (mark in found) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.end == mark.start) {
                val word = previous.word + mark.word
                if (word.codePointCount(0, word.length) > MAX_WORD_CHARS) {
                    throw IllegalArgumentException("「$word」超过 $MAX_WORD_CHARS 个字，发音词典不接受更长的词。请拆成更短的词")
                }
                merged[merged.lastIndex] = previous.copy(
                    end = mark.end,
                    word = word,
                    syllables = previous.syllables + mark.syllables,
                    annotations = previous.annotations + mark.annotations
                )
            } else merged += mark
        }
        return merged
    }

    private fun rulesOf(marks: List<Mark>): List<String> {
        val byWord = linkedMapOf<String, String>()
        for (mark in marks) {
            require(mark.word.isNotEmpty() && mark.word.codePointCount(0, mark.word.length) <= MAX_WORD_CHARS) {
                "读音词「${mark.word}」无效"
            }
            require(!mark.word.any { it.isWhitespace() || it == '/' }) { "读音词「${mark.word}」不能包含空格或斜杠" }
            val reading = mark.syllables.joinToString("") { "(${it.pinyin}${it.tone})" }
            putRule(byWord, mark.word, reading, existing = false)
        }
        if (byWord.size > MAX_ENTRIES) {
            throw IllegalArgumentException("发音词典最多 $MAX_ENTRIES 条，这段配音里的读音标注太多了")
        }
        return byWord.map { (word, reading) -> "$word/$reading" }
    }

    private fun stripAnnotations(text: String, marks: List<Mark>): String {
        if (marks.isEmpty()) return text
        val ranges = marks.flatMap { it.annotations }.sortedBy { it.first }
        return buildString {
            var cursor = 0
            for (range in ranges) {
                append(text, cursor, range.first)
                cursor = range.last + 1
            }
            append(text, cursor, text.length)
        }
    }

    private fun wordStartBefore(text: String, open: Int, syllableCount: Int, snippet: String): Int {
        var cursor = open
        var taken = 0
        while (taken < syllableCount && cursor > 0) {
            val codePoint = text.codePointBefore(cursor)
            if (!isCjk(codePoint)) break
            cursor -= Character.charCount(codePoint)
            taken++
        }
        if (taken == syllableCount) return cursor
        if (taken == 0) {
            throw IllegalArgumentException("读音「$snippet」前面没有汉字。请紧挨着要标注的词，例如银行(yin2hang2)")
        }
        throw IllegalArgumentException(
            "「$snippet」有 $syllableCount 个音节，括号前只有 $taken 个汉字。几个汉字就写几个音节，例如银行(yin2hang2)或银(yin2)行(hang2)"
        )
    }

    /** @return syllables for a pronunciation mark, or null when the parentheses are an ordinary aside. */
    private fun classify(body: String, snippet: String): List<Syllable>? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { !isPronunciationChar(it) }) return null
        if (trimmed.none { isPinyinLetter(it) }) return null
        if (trimmed.any { it in TONE_MARKS }) {
            throw IllegalArgumentException("「$snippet」请用数字声调，例如 yin2，不要写成 yín。轻声用 5")
        }
        val canonical = normalizeBody(trimmed)
        if (canonical.any { it.isDigit() }) return parseSyllables(canonical, snippet)
        if (isAllSyllables(canonical)) {
            throw IllegalArgumentException("「$snippet」每个音节都要写声调数字 1–4 或轻声 5，例如 yin2hang2")
        }
        return null
    }

    private fun parseSyllables(canonical: String, snippet: String): List<Syllable> {
        val syllables = mutableListOf<Syllable>()
        var index = 0
        while (index < canonical.length) {
            val start = index
            while (index < canonical.length && canonical[index] in 'a'..'z') index++
            val letters = canonical.substring(start, index)
            if (letters.isEmpty() || index >= canonical.length || canonical[index] !in '1'..'5') {
                throw IllegalArgumentException("「$snippet」无法识别。每个音节写成拼音加声调，例如 yin2、hang2，轻声用 5")
            }
            val tone = canonical[index] - '0'
            index++
            val pinyin = when (letters) {
                "lue" -> "lve"
                "nue" -> "nve"
                else -> letters
            }
            if (pinyin !in SYLLABLES) {
                throw IllegalArgumentException("「$snippet」里的「$letters」不是合法拼音音节")
            }
            syllables += Syllable(pinyin, tone)
        }
        if (syllables.isEmpty()) {
            throw IllegalArgumentException("「$snippet」没有读出音节")
        }
        return syllables
    }

    private fun isAllSyllables(letters: String): Boolean {
        if (letters.isEmpty()) return false
        var index = 0
        while (index < letters.length) {
            var matched = false
            val max = minOf(6, letters.length - index)
            for (length in max downTo 1) {
                val piece = when (val raw = letters.substring(index, index + length)) {
                    "lue" -> "lve"
                    "nue" -> "nve"
                    else -> raw
                }
                if (piece in SYLLABLES) {
                    index += length
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }
        return true
    }

    private fun normalizeBody(body: String): String = buildString {
        body.forEach { character ->
            when {
                character in '０'..'９' -> append('0' + (character - '０'))
                character in 'Ａ'..'Ｚ' -> append('a' + (character - 'Ａ'))
                character in 'ａ'..'ｚ' -> append('a' + (character - 'ａ'))
                character == 'ü' || character == 'Ü' -> append('v')
                isSeparator(character) -> Unit
                character in 'A'..'Z' -> append(character.lowercaseChar())
                else -> append(character)
            }
        }
    }

    private fun isUnclosedPronunciation(rest: String): Boolean {
        val line = rest.substringBefore('\n').substringBefore('\r')
        val prefix = line.takeWhile { isPronunciationChar(it) }.trim()
        if (prefix.isEmpty() || prefix.none { isPinyinLetter(it) }) return false
        if (prefix.any { it in TONE_MARKS }) return true
        val canonical = normalizeBody(prefix)
        return canonical.any { it.isDigit() } || isAllSyllables(canonical)
    }

    private fun isPronunciationChar(character: Char): Boolean =
        isPinyinLetter(character) || isDigitChar(character) || isSeparator(character) || character in TONE_MARKS

    private fun isPinyinLetter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' ||
            character in 'ａ'..'ｚ' || character in 'Ａ'..'Ｚ' ||
            character == 'ü' || character == 'Ü' || character == 'v' || character == 'V'

    private fun isDigitChar(character: Char): Boolean =
        character in '0'..'9' || character in '０'..'９'

    private fun isSeparator(character: Char): Boolean = character == ' ' || character == '\t' ||
        character == '\n' || character == '\r' || character == '\'' || character == '’' ||
        character == '‘' || character == '-' || character == '‐' || character == '‑' ||
        character == '·' || character == '‧' || character == '　'

    private fun isCjk(codePoint: Int): Boolean = when (codePoint) {
        in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF,
        in 0x20000..0x2A6DF, in 0x2A700..0x2B73F, in 0x2B740..0x2B81F,
        in 0x2B820..0x2CEAF, in 0x2CEB0..0x2EBEF, in 0x30000..0x3134F -> true
        else -> false
    }

    private fun snippet(text: String, open: Int): String {
        val start = (open - 8).coerceAtLeast(0)
        val end = (open + 18).coerceAtMost(text.length)
        return text.substring(start, end).replace("\n", "")
    }
}
