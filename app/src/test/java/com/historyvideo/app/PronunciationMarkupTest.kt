package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationMarkupTest {
    @Test
    fun wordMarkBecomesOneToneRuleAndCleanSentence() {
        val parsed = PronunciationMarkup.parse("我去银行(yin2hang2)取钱")
        assertEquals("我去银行取钱", parsed.cleanText)
        assertEquals(listOf("银行/(yin2)(hang2)"), parsed.rules)
        assertFalse(parsed.cleanText.contains("yin2"))
    }

    @Test
    fun perCharacterMarksMergeIntoTheSameRule() {
        val word = PronunciationMarkup.parse("银行(yin2 hang2)")
        val chars = PronunciationMarkup.parse("银(yin2)行(hang2)")
        assertEquals(word.cleanText, chars.cleanText)
        assertEquals(listOf("银行/(yin2)(hang2)"), chars.rules)
        assertEquals("银行", chars.cleanText)
    }

    @Test
    fun separatorsCaseAndFullwidthParentheses() {
        assertEquals(
            listOf("银行/(yin2)(hang2)"),
            PronunciationMarkup.parse("银行(yin2-hang2)").rules
        )
        assertEquals(
            listOf("银行/(yin2)(hang2)"),
            PronunciationMarkup.parse("银行(YIN2'HANG2)").rules
        )
        assertEquals(
            listOf("银行/(yin2)(hang2)"),
            PronunciationMarkup.parse("银行（yin2hang2）").rules
        )
        assertEquals(
            "我去银行取钱",
            PronunciationMarkup.clean("我去银行（ＹＩＮ２　ＨＡＮＧ２）取钱")
        )
    }

    @Test
    fun lightToneAndUmlautSyllables() {
        assertEquals(listOf("了/(le5)"), PronunciationMarkup.parse("好了(le5)").rules)
        assertEquals(listOf("女/(nv3)"), PronunciationMarkup.parse("女(nü3)").rules)
        assertEquals(listOf("女/(nv3)"), PronunciationMarkup.parse("女(nv3)").rules)
        assertEquals(listOf("略/(lve4)"), PronunciationMarkup.parse("略(lüe4)").rules)
        assertEquals(listOf("略/(lve4)"), PronunciationMarkup.parse("略(lue4)").rules)
    }

    @Test
    fun ordinaryParenthesesStayInTheSentence() {
        val aside = "我去银行（就是存钱的地方）取钱"
        val parsed = PronunciationMarkup.parse(aside)
        assertEquals(aside, parsed.cleanText)
        assertTrue(parsed.rules.isEmpty())
        assertEquals("见(OK)和(3)", PronunciationMarkup.clean("见(OK)和(3)"))
        val mixed = PronunciationMarkup.parse("我去银行(yin2hang2)取钱（注意安全）")
        assertEquals("我去银行取钱（注意安全）", mixed.cleanText)
        assertEquals(listOf("银行/(yin2)(hang2)"), mixed.rules)
    }

    @Test
    fun syllableCountWalksBackwardFromTheParenthesis() {
        val parsed = PronunciationMarkup.parse("我去银行(yin2hang2)取钱")
        assertEquals(listOf("银行/(yin2)(hang2)"), parsed.rules)
        assertEquals(listOf("行/(hang2)"), PronunciationMarkup.parse("银行(hang2)").rules)
        assertEquals("银行", PronunciationMarkup.clean("银行(hang2)"))
    }

    @Test
    fun punctuationBlocksMergingAndSameReadingIsDeduped() {
        val separate = PronunciationMarkup.parse("银(yin2)，行(hang2)")
        assertEquals("银，行", separate.cleanText)
        assertEquals(listOf("银/(yin2)", "行/(hang2)"), separate.rules)
        val repeated = PronunciationMarkup.parse("银行(yin2hang2)，还有银行(yin2hang2)")
        assertEquals(listOf("银行/(yin2)(hang2)"), repeated.rules)
        assertEquals("银行，还有银行", repeated.cleanText)
    }

    @Test
    fun longerWordsStayDistinctKeys() {
        val parsed = PronunciationMarkup.parse("银行(yin2hang2)的行走(xing2zou3)")
        assertEquals(listOf("银行/(yin2)(hang2)", "行走/(xing2)(zou3)"), parsed.rules)
        assertEquals("银行的行走", parsed.cleanText)
    }

    @Test
    fun missingToneFailsInsteadOfBeingSent() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("我去银行(yinhang)取钱")
        }
        assertTrue(error.message!!.contains("声调"))
    }

    @Test
    fun toneMarkLettersFailWithANumberExample() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("银行(yín2hang2)")
        }
        assertTrue(error.message!!.contains("yin2"))
    }

    @Test
    fun invalidSyllableFails() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("银行(yin2hangg2)")
        }
        assertTrue(error.message!!.contains("hangg"))
        assertTrue(error.message!!.contains("不是合法拼音"))
    }

    @Test
    fun syllableCountMustMatchTheCharactersInFront() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("行(yin2hang2)")
        }
        assertTrue(error.message!!.contains("1 个汉字"))
        val missing = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("(yin2hang2)")
        }
        assertTrue(missing.message!!.contains("没有汉字"))
    }

    @Test
    fun unclosedMarkFails() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("我去银行(yin2hang2取钱")
        }
        assertTrue(error.message!!.contains("右括号"))
        assertEquals("银行(yin2", PronunciationMarkup.display("银行(yin2"))
    }

    @Test
    fun conflictingReadingsFail() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("银行(yin2hang2)，银行(yin4hang2)")
        }
        assertTrue(error.message!!.contains("两种读音"))
        assertTrue(error.message!!.contains("行走"))
    }

    @Test
    fun wordCannotExceedNineCharacters() {
        val nine = "一二三四五六七八九"
        val parsed = PronunciationMarkup.parse("$nine(${List(9) { "a1" }.joinToString("")})")
        assertEquals(listOf("$nine/${List(9) { "(a1)" }.joinToString("")}"), parsed.rules)
        val ten = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("一二三四五六七八九十(${List(10) { "a1" }.joinToString("")})")
        }
        assertTrue(ten.message!!.contains("9"))
        val merged = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse(buildString {
                repeat(10) { index ->
                    appendCodePoint(0x4E00 + index)
                    append("(a1)")
                }
            })
        }
        assertTrue(merged.message!!.contains("9"))
    }

    @Test
    fun moreThanFiveThousandEntriesFails() {
        val text = buildString {
            repeat(5001) { index ->
                appendCodePoint(0x4E00 + index)
                append("(a1)，")
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse(text)
        }
        assertTrue(error.message!!.contains("5000"))
    }

    @Test
    fun hintDocumentsBothForms() {
        assertTrue(PronunciationMarkup.HINT.contains("银行(yin2hang2)"))
        assertTrue(PronunciationMarkup.HINT.contains("银(yin2)行(hang2)"))
        assertTrue(PronunciationMarkup.HINT.contains("5"))
    }

    @Test
    fun subtitleChunksStripMarksAndRejectASplitMark() {
        val marked = "我去银行(yin2hang2)取钱"
        val spoken = PronunciationMarkup.clean(marked)
        assertEquals(
            listOf("我去", "银行取钱"),
            PronunciationMarkup.cleanChunks(listOf("我去", "银行(yin2hang2)取钱"), spoken)
        )
        assertEquals(
            emptyList<String>(),
            PronunciationMarkup.cleanChunks(listOf("我去银行", "(yin2hang2)取钱"), spoken)
        )
    }

    @Test
    fun mergeKeepsOtherAdditionKeysAndSkipsSsml() {
        val additions = JSONObject()
            .put("disable_markdown_filter", true)
            .put("tone_fidelity", true)
            .put("cache_config", JSONObject().put("text_type", 1).put("use_cache", true))
        val clean = PronunciationMarkup.mergeIntoAdditions(additions, "我去银行(yin2hang2)取钱")
        assertEquals("我去银行取钱", clean)
        assertEquals(true, additions.getBoolean("tone_fidelity"))
        assertEquals(true, additions.getBoolean("disable_markdown_filter"))
        assertEquals(1, additions.getJSONObject("cache_config").getInt("text_type"))
        assertEquals(true, additions.getJSONObject("cache_config").getBoolean("use_cache"))
        val tone = additions.getJSONObject("pronunciation_dict").getJSONArray("tone")
        assertEquals(1, tone.length())
        assertEquals("银行/(yin2)(hang2)", tone.getString(0))
        assertFalse(additions.toString().contains("phoneme"))
        assertFalse(additions.toString().contains("<speak"))
        assertFalse(clean.contains("("))
    }

    @Test
    fun mergeAppendsToExistingToneEntries() {
        val additions = JSONObject().put(
            "pronunciation_dict",
            JSONObject().put("tone", JSONArray().put("北京/(bei3)(jing1)")).put("keep", true)
        )
        val clean = PronunciationMarkup.mergeIntoAdditions(additions, "我在北京(bei3jing1)的银行(yin2hang2)")
        assertEquals("我在北京的银行", clean)
        val dict = additions.getJSONObject("pronunciation_dict")
        assertEquals(true, dict.getBoolean("keep"))
        val tone = dict.getJSONArray("tone")
        assertEquals(2, tone.length())
        assertEquals("北京/(bei3)(jing1)", tone.getString(0))
        assertEquals("银行/(yin2)(hang2)", tone.getString(1))
    }

    @Test
    fun mergeRejectsAConflictingExistingRuleWithoutReplacingIt() {
        val additions = JSONObject().put(
            "pronunciation_dict",
            JSONObject().put("tone", JSONArray().put("银行/(yin4)(hang2)")).put("keep", 1)
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.mergeIntoAdditions(additions, "去银行(yin2hang2)")
        }
        assertTrue(error.message!!.contains("两种读音"))
        val tone = additions.getJSONObject("pronunciation_dict").getJSONArray("tone")
        assertEquals(1, tone.length())
        assertEquals("银行/(yin4)(hang2)", tone.getString(0))
        assertEquals(1, additions.getJSONObject("pronunciation_dict").getInt("keep"))
    }

    @Test
    fun noMarksLeaveAdditionsUntouched() {
        val additions = JSONObject().put("tone_fidelity", true)
        val clean = PronunciationMarkup.mergeIntoAdditions(additions, "我去银行取钱（注意）")
        assertEquals("我去银行取钱（注意）", clean)
        assertFalse(additions.has("pronunciation_dict"))
        assertEquals(true, additions.getBoolean("tone_fidelity"))
    }

    @Test
    fun invalidMarkDoesNotWriteTheDictionary() {
        val additions = JSONObject().put("tone_fidelity", true)
        assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.mergeIntoAdditions(additions, "银行(hangg2)")
        }
        assertFalse(additions.has("pronunciation_dict"))
        assertEquals(true, additions.getBoolean("tone_fidelity"))
    }

    @Test
    fun volcanoRequestUsesCleanTextAndPronunciationDict() {
        val api = CloudApi()
        val clone = api.doubaoRequest(
            ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-icl-2.0", "key"),
            "voice", 1f, "我去银行(yin2hang2)取钱"
        )
        val cloneParams = clone.getJSONObject("req_params")
        assertEquals("我去银行取钱", cloneParams.getString("text"))
        assertEquals("seed-tts-2.0-expressive", cloneParams.getString("model"))
        val cloneAdditions = JSONObject(cloneParams.getString("additions"))
        assertEquals(true, cloneAdditions.getBoolean("tone_fidelity"))
        assertEquals(true, cloneAdditions.getBoolean("disable_markdown_filter"))
        assertEquals(
            "银行/(yin2)(hang2)",
            cloneAdditions.getJSONObject("pronunciation_dict").getJSONArray("tone").getString(0)
        )
        assertFalse(cloneAdditions.getJSONObject("cache_config").getBoolean("use_cache"))
        assertFalse(cloneParams.getString("additions").contains("phoneme"))

        val generic = api.doubaoRequest(
            ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-tts-2.0", "key"),
            "voice", 1f, "银(yin2)行(hang2)"
        )
        val genericParams = generic.getJSONObject("req_params")
        assertEquals("银行", genericParams.getString("text"))
        assertFalse(genericParams.has("model"))
        val genericAdditions = JSONObject(genericParams.getString("additions"))
        assertFalse(genericAdditions.has("tone_fidelity"))
        assertEquals(
            "银行/(yin2)(hang2)",
            genericAdditions.getJSONObject("pronunciation_dict").getJSONArray("tone").getString(0)
        )

        val plain = api.doubaoRequest(
            ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-icl-2.0", "key"),
            "voice", 1f, "我去银行取钱"
        )
        val plainAdditions = JSONObject(plain.getJSONObject("req_params").getString("additions"))
        assertEquals("我去银行取钱", plain.getJSONObject("req_params").getString("text"))
        assertEquals(true, plainAdditions.getBoolean("tone_fidelity"))
        assertFalse(plainAdditions.has("pronunciation_dict"))
        assertTrue(plainAdditions.getJSONObject("cache_config").getBoolean("use_cache"))
    }

    @Test
    fun grainMeasureDanSendsAnIclDictionaryAndSkipsTheTextCache() {
        val marked = "哼，你以为考上状元也就是多领几石(dan4)俸禄、"
        val parsed = PronunciationMarkup.parse(marked)
        assertEquals("哼，你以为考上状元也就是多领几石俸禄、", parsed.cleanText)
        assertEquals(listOf("石/(dan4)"), parsed.rules)
        val units = StoryboardRules.sourceUnits(marked)
        assertEquals(marked, units.joinToString("") { it.text })
        assertTrue(units.any { "石(dan4)" in it.text })
        assertTrue(units.none { "(dan4)" in it.text && "石(dan4)" !in it.text })

        val body = CloudApi().doubaoRequest(
            ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-icl-2.0", "key"),
            "S_voice", 1f, marked
        )
        val params = body.getJSONObject("req_params")
        assertEquals("哼，你以为考上状元也就是多领几石俸禄、", params.getString("text"))
        assertEquals("seed-tts-2.0-expressive", params.getString("model"))
        assertFalse(params.has("ssml"))
        val additions = JSONObject(params.getString("additions"))
        assertEquals(true, additions.getBoolean("tone_fidelity"))
        assertFalse(additions.getJSONObject("cache_config").getBoolean("use_cache"))
        assertEquals(1, additions.getJSONObject("cache_config").getInt("text_type"))
        val tone = additions.getJSONObject("pronunciation_dict").getJSONArray("tone")
        assertEquals(1, tone.length())
        assertEquals("石/(dan4)", tone.getString(0))
        assertFalse(params.getString("text").contains("dan4"))
        assertFalse(params.getString("additions").contains("phoneme"))
        assertFalse(params.getString("additions").contains("<speak"))
    }

    @Test
    fun reportedSentenceKeepsDistinctWholeWordReadings() {
        val marked = "银行(yin2hang2)的行长(hang2zhang3)去了重庆(chong2qing4)。他提醒我，重要(zhong4yao4)的合同要重新(chong2xin1)核对，别再落下(la4xia4)行李。"
        val parsed = PronunciationMarkup.parse(marked)
        assertEquals(
            "银行的行长去了重庆。他提醒我，重要的合同要重新核对，别再落下行李。",
            parsed.cleanText
        )
        assertEquals(
            listOf(
                "银行/(yin2)(hang2)",
                "行长/(hang2)(zhang3)",
                "重庆/(chong2)(qing4)",
                "重要/(zhong4)(yao4)",
                "重新/(chong2)(xin1)",
                "落下/(la4)(xia4)"
            ),
            parsed.rules
        )
        val additions = JSONObject(
            CloudApi().doubaoRequest(
                ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-icl-2.0", "key"),
                "S_voice", 1f, marked
            ).getJSONObject("req_params").getString("additions")
        )
        assertFalse(additions.getJSONObject("cache_config").getBoolean("use_cache"))
        val tone = additions.getJSONObject("pronunciation_dict").getJSONArray("tone")
        assertEquals(parsed.rules, (0 until tone.length()).map { tone.getString(it) })
    }

    @Test
    fun provenRewriteSendsShiAsDanAndKeepsShiOnSubtitles() {
        val marked = "哼，你以为考上状元也就是多领几石(=担)俸禄、"
        val subtitle = "哼，你以为考上状元也就是多领几石俸禄、"
        val parsed = PronunciationMarkup.parse(marked)
        assertEquals(subtitle, parsed.cleanText)
        assertEquals(subtitle, PronunciationMarkup.subtitleText(marked))
        assertEquals(listOf("石/担"), parsed.rules)
        assertFalse(parsed.cleanText.contains("担"))
        assertEquals(
            listOf("哼，你以为考上状元也就是多领几", "石俸禄、"),
            PronunciationMarkup.cleanChunks(
                listOf("哼，你以为考上状元也就是多领几", "石(=担)俸禄、"),
                subtitle
            )
        )
        val server = "哼，你以为考上状元也就是多领几担俸禄、"
        assertEquals(subtitle, PronunciationMarkup.restoreServerTranscript(server, marked))
        assertEquals(subtitle, PronunciationMarkup.restoreServerTranscript(subtitle, marked))

        val params = CloudApi().doubaoRequest(
            ProviderConfig("tts", "https://openspeech.bytedance.com/api/v3/tts/unidirectional", "seed-icl-2.0", "key"),
            "S_voice", 1f, marked
        ).getJSONObject("req_params")
        assertEquals(subtitle, params.getString("text"))
        assertEquals("seed-tts-2.0-expressive", params.getString("model"))
        assertFalse(params.has("ssml"))
        val additions = JSONObject(params.getString("additions"))
        assertEquals(true, additions.getBoolean("tone_fidelity"))
        assertFalse(additions.getJSONObject("cache_config").getBoolean("use_cache"))
        val tone = additions.getJSONObject("pronunciation_dict").getJSONArray("tone")
        assertEquals(1, tone.length())
        assertEquals("石/担", tone.getString(0))
        assertFalse(params.getString("additions").contains("phoneme"))

        val units = StoryboardRules.sourceUnits(marked, "fine")
        assertEquals(marked, units.joinToString("") { it.text })
        assertTrue(units.any { "石(=担)" in it.text })
        assertTrue(units.none { "(=担)" in it.text && "石(=担)" !in it.text })
    }

    @Test
    fun phraseRewriteAndPinyinStayDistinct() {
        val phrase = PronunciationMarkup.parse("领几石(=几担)俸禄")
        assertEquals("领几石俸禄", phrase.cleanText)
        assertEquals(listOf("几石/几担"), phrase.rules)
        assertEquals(
            "领几石俸禄",
            PronunciationMarkup.restoreServerTranscript("领几担俸禄", "领几石(=几担)俸禄")
        )
        val pinyin = PronunciationMarkup.parse("多领几石(dan4)俸禄")
        assertEquals(listOf("石/(dan4)"), pinyin.rules)
        assertFalse(pinyin.rules.contains("石/担"))
        val mixed = PronunciationMarkup.parse("银行(yin2hang2)的石(=担)")
        assertEquals("银行的石", mixed.cleanText)
        assertEquals(listOf("银行/(yin2)(hang2)", "石/担"), mixed.rules)
        assertEquals(
            listOf("石/担", "头/投"),
            PronunciationMarkup.parse("石(=担)头(=投)").rules
        )
    }

    @Test
    fun rewriteConflictsAndOrdinaryParentheses() {
        val clash = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("几石(=担)，又一块石(=旦)")
        }
        assertTrue(clash.message!!.contains("两种读音"))
        val withPinyin = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("石(=担)和石(dan4)")
        }
        assertTrue(withPinyin.message!!.contains("两种读音"))
        val letters = assertThrows(IllegalArgumentException::class.java) {
            PronunciationMarkup.parse("石(=dan4)")
        }
        assertTrue(letters.message!!.contains("汉字"))
        assertEquals("几石（注意）俸禄(OK)", PronunciationMarkup.clean("几石（注意）俸禄(OK)"))
        assertTrue(PronunciationMarkup.parse("几石（注意）俸禄(OK)").rules.isEmpty())
        assertEquals("石(=担", PronunciationMarkup.display("石(=担"))
    }

    @Test
    fun narrationEditRetiresThePreviousVoiceFile() {
        val shot = Shot(id = 3, text = "多领几石俸禄", imagePrompt = "提示", audioStatus = "done", audioRevision = "11111111-1111-1111-1111-111111111111")
        shot.text = "多领几石(dan4)俸禄"
        shot.retireVoiceIfNarrationChanged("多领几石俸禄")
        assertEquals("pending", shot.audioStatus)
        assertFalse(shot.audioRevision == "11111111-1111-1111-1111-111111111111")
        assertTrue(shot.audioRevision.matches(Regex("[a-f0-9-]{36}")))
        val unchanged = shot.audioRevision
        shot.retireVoiceIfNarrationChanged(shot.text)
        assertEquals(unchanged, shot.audioRevision)
    }

    @Test
    fun storyboardKeepsAMarkIntactAndDoesNotCountItsPinyin() {
        val source = "开场白。一二三四五六七八九银行(yin2hang2)。"
        val units = StoryboardRules.sourceUnits(source, "fine")
        assertEquals(source, units.joinToString("") { it.text })
        assertTrue(units.any { it.text == "一二三四五六七八九银行(yin2hang2)。" })
        val cut = "开场白。一二三四五六七八九十一银行(yin2hang2)取钱。"
        val cutUnits = StoryboardRules.sourceUnits(cut, "fine")
        assertEquals(cut, cutUnits.joinToString("") { it.text })
        assertTrue(cutUnits.any { it.text.endsWith("银行(yin2hang2)") })
        assertTrue(cutUnits.all { "银行(yin2hang2)" in it.text || "(yin" !in it.text })
        val perChar = "开场白。一二三四五六七八九十一银(yin2)行(hang2)取。"
        val perCharUnits = StoryboardRules.sourceUnits(perChar, "fine")
        assertEquals(perChar, perCharUnits.joinToString("") { it.text })
        assertTrue(perCharUnits.any { "银(yin2)行(hang2)" in it.text })
    }

    @Test
    fun voiceoverAlignmentKeepsTheMarkAttachedToItsWord() {
        val source = "我去银行(yin2hang2)取钱。"
        assertEquals(
            listOf("我去银行(yin2hang2)", "取钱。"),
            StoryboardRules.reconstructVoiceoverSegments(source, listOf("我去银行", "取钱"))
        )
    }
}
