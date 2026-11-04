package com.historyvideo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardRulesTest {
    private val source = "朱元璋为什么要杀功臣？哼，有人说他天生多疑，心狠手辣。" +
        "放屁！他杀人是为了给太子铺路，不仅要清除骄兵悍将，在外还要稳住边疆。" +
        "洪武十三年，胡惟庸案爆发，牵连三万余人。此后大明再无丞相。"

    private fun shot(i: Int, text: String) =
        Shot(id = i, text = text, imagePrompt = "提示词$i", visual = "画面$i")

    private fun shotsFrom(cuts: List<String>) = cuts.mapIndexed { i, t -> shot(i + 1, t) }

    @Test
    fun openingUnitsAreFixed() {
        val units = StoryboardRules.sourceUnits(source)
        assertEquals(source, units.joinToString("") { it.text })
        assertTrue(units.first().fixedShot)
        val rebuttal = units.filter { it.text.contains("放屁") || it.text.contains("在外") }
        assertTrue(rebuttal.all { it.fixedShot })
    }

    @Test
    fun aiMergesRebuttalEndWithNextSentenceIsSplit() {
        val title = "朱元璋为什么要杀功臣？"
        val hook = "哼，有人说他天生多疑，心狠手辣。"
        val rebuttal = "放屁！他杀人是为了给太子铺路，不仅要清除骄兵悍将，在外还要稳住边疆。"
        val body1 = "洪武十三年，胡惟庸案爆发，牵连三万余人。"
        val body2 = "此后大明再无丞相。"
        // Model merged the rebuttal end with the next sentence.
        val merged = shotsFrom(listOf(title, hook, rebuttal + body1, body2))
        val aligned = StoryboardRules.alignOpening(source, merged)
        StoryboardRules.validate(source, aligned)
        assertEquals(source, aligned.joinToString("") { it.text })
        assertTrue(aligned.any { it.text == body1 })
        assertEquals((1..aligned.size).toList(), aligned.map { it.id })
    }

    @Test
    fun aiMergesTitleWithHookIsSplit() {
        val merged = shotsFrom(listOf(
            "朱元璋为什么要杀功臣？哼，有人说他天生多疑，心狠手辣。放屁！他杀人是为了给太子铺路，",
            "不仅要清除骄兵悍将，在外还要稳住边疆。洪武十三年，胡惟庸案爆发，牵连三万余人。此后大明再无丞相。"))
        val aligned = StoryboardRules.alignOpening(source, merged)
        StoryboardRules.validate(source, aligned)
        assertEquals("朱元璋为什么要杀功臣？", aligned.first().text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun alteredTextStillFails() {
        val bad = shotsFrom(listOf("朱元璋为什么要杀功臣？", "哼，改写了的内容。"))
        StoryboardRules.validate(source, StoryboardRules.alignOpening(source, bad))
    }
}
