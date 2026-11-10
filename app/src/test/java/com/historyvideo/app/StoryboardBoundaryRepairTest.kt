package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StoryboardBoundaryRepairTest {
    private val opening = "朱元璋为什么要杀功臣？哼，有人说他天生多疑，心狠手辣。" +
        "放屁！他杀人是为了给太子铺路，不仅要清除骄兵悍将，在外还要稳住边疆。"
    // A sentence with no internal punctuation longer than maxUnitLength: sourceUnits cuts it into
    // several units that do not end at punctuation.
    private val longSentence = "洪武二十六年蓝玉案爆发之后京城内外人人自危朝堂上下再也没有人敢于直言进谏。"

    private fun longScript(): String = buildString {
        append(opening)
        for (i in 1..50) append("第${i}段正文讲述大明往事，")
        append(longSentence)
        for (i in 51..60) append("后记${i}说完了。")
    }

    // One unit per shot, except the "哼，" interjection which must join the next unit.
    private fun perUnitGroups(units: List<StoryboardRules.SourceUnit>): List<List<Int>> {
        val groups = mutableListOf<MutableList<Int>>()
        var carry = false
        units.forEach { u ->
            if (carry) groups.last() += u.id else groups += mutableListOf(u.id)
            carry = u.text.filter(Char::isLetterOrDigit) in setOf("哼", "他娘的")
        }
        return groups
    }

    private fun shot(i: Int, text: String) =
        Shot(id = i, text = text, imagePrompt = "提示词$i", visual = "画面$i", subtitleChunks = mutableListOf(text))

    private fun boundaryOk(source: String, shots: List<Shot>) {
        var offset = 0
        shots.dropLast(1).forEach { s ->
            offset += s.text.length
            assertTrue("bad boundary at $offset: ${s.text}",
                StoryboardRules.endsAtPunctuation(source.substring(0, offset)))
        }
    }

    @Test
    fun modelCutsLongSentenceUnitsLateInScriptIsRepaired() {
        val source = longScript()
        val units = StoryboardRules.sourceUnits(source)
        assertTrue(units.count { !StoryboardRules.endsAtPunctuation(it.text) } >= 2)
        // Model puts every unit in its own shot, so shots end in the middle of the long sentence.
        val texts = StoryboardRules.reconstruct(units, perUnitGroups(units))
        val raw = texts.mapIndexed { i, t -> shot(i + 1, t) }
        try {
            StoryboardRules.validate(source, raw)
            fail("raw grouping should violate the punctuation rule")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("标点之后"))
        }
        val repaired = StoryboardRules.alignOpening(source, raw)
        StoryboardRules.validate(source, repaired)
        assertEquals(source, repaired.joinToString("") { it.text })
        assertTrue("long sentence kept whole", repaired.any { it.text == longSentence })
        assertEquals((1..repaired.size).toList(), repaired.map { it.id })
        repaired.forEach { assertEquals(it.text, it.subtitleChunks.joinToString("")) }
        assertEquals("朱元璋为什么要杀功臣？", repaired.first().text)
    }

    @Test
    fun boundaryMovedToNearestPunctuation() {
        val source = opening + "洪武十三年，胡惟庸案爆发，牵连三万余人。此后大明再无丞相。"
        val head = listOf("朱元璋为什么要杀功臣？", "哼，有人说他天生多疑，心狠手辣。",
            "放屁！他杀人是为了给太子铺路，不仅要清除骄兵悍将，在外还要稳住边疆。")
        val raw = (head + listOf("洪武十三年，胡惟庸", "案爆发，牵连三万余人。此后大明再无丞相。"))
            .mapIndexed { i, t -> shot(i + 1, t) }
        val repaired = StoryboardRules.alignOpening(source, raw)
        StoryboardRules.validate(source, repaired)
        boundaryOk(source, repaired)
        assertEquals(raw.size, repaired.size)
        assertEquals("洪武十三年，", repaired[3].text)
        assertEquals("画面5", repaired[4].visual)
    }

    @Test
    fun checkedStoryboardRepairsInsteadOfFailing() {
        val source = longScript()
        val units = StoryboardRules.sourceUnits(source)
        val json = JSONObject().put("title", "主题").put("style_preset", "画风")
            .put("shots", JSONArray().apply {
                perUnitGroups(units).forEachIndexed { index, ids ->
                    put(JSONObject().put("id", index + 1)
                        .put("source_unit_ids", JSONArray(ids))
                        .put("subtitle_unit_groups", JSONArray().put(JSONArray(ids)))
                        .put("visual", "画面$index").put("punch", "要点要点")
                        .put("image_prompt", "提示词$index").put("video_prompt", "推近")
                        .put("shot_type", "environment").put("duration", 3.0))
                }
            }).toString()
        var calls = 0
        val result = CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) { calls++; json }
        assertEquals(1, calls)
        assertEquals(source, result.shots.joinToString("") { it.text })
        boundaryOk(source, result.shots)
    }
}
