package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardTruncationTest {
    @Test
    fun detectsLengthFinishReasonAndUnterminatedJson() {
        assertTrue(isStoryboardTruncation(null, "length"))
        assertTrue(isStoryboardTruncation("分镜 JSON 格式错误：Unterminated array at character 4479 of {\"title\":..."))
        assertTrue(isStoryboardTruncation("分镜输出被截断：something"))
        assertFalse(isStoryboardTruncation("第 1 镜缺少 visual 字段", "stop"))
    }

    @Test
    fun salvagesCompleteShotsFromTruncatedArray() {
        val truncated = """{"title":"古代的镖局总镖头到底有多爽","style_preset":"写实古装国风","shots":[{"id":1,"source_unit_ids":[1],"subtitle_unit_groups":[[1]],"visual":"镖局大厅","punch":"总镖头","image_prompt":"古装镖局大厅写实","video_prompt":"缓慢推进","shot_type":"environment","duration":3},{"id":2,"source_unit_ids":[2],"subtitle_unit_groups":[[2]],"visual":"夜路","punch":"驰骋","image_prompt":"月下快马""""
        val salvaged = salvageTruncatedStoryboardJson(truncated)
        assertNotNull(salvaged)
        val root = JSONObject(salvaged!!)
        assertEquals("古代的镖局总镖头到底有多爽", root.getString("title"))
        assertEquals(1, root.getJSONArray("shots").length())
        assertEquals(1, root.getJSONArray("shots").getJSONObject(0).getInt("id"))
    }

    @Test
    fun salvageReturnsNullWhenNoCompleteShot() {
        val truncated = """{"title":"主题","style_preset":"画风","shots":[{"id":1,"source_unit_ids":[1],"visual":""""
        assertNull(salvageTruncatedStoryboardJson(truncated))
    }

    @Test
    fun splitUnitsPrefersPunctuationBoundary() {
        val units = listOf(
            StoryboardRules.SourceUnit(1, "甲句。", false),
            StoryboardRules.SourceUnit(2, "乙半", false),
            StoryboardRules.SourceUnit(3, "乙尾。", false),
            StoryboardRules.SourceUnit(4, "丙句。", false)
        )
        val (left, right) = splitStoryboardUnits(units)
        assertEquals(listOf(1), left.map { it.id })
        assertEquals("甲句。", left.single().text)
        assertEquals(listOf(1, 2, 3), right.map { it.id })
        assertEquals("乙半", right[0].text)
    }

    @Test
    fun checkedStoryboardSalvagesTruncatedButCompleteCoverage() {
        val source = "主题句。正文一句。"
        val units = StoryboardRules.sourceUnits(source)
        val complete = JSONObject()
            .put("title", "主题句。")
            .put("style_preset", "统一画风")
            .put("shots", JSONArray().apply {
                units.forEachIndexed { index, unit ->
                    put(JSONObject()
                        .put("id", index + 1)
                        .put("source_unit_ids", JSONArray().put(unit.id))
                        .put("subtitle_unit_groups", JSONArray().put(JSONArray().put(unit.id)))
                        .put("visual", "画面$index")
                        .put("punch", "要点$index".take(6))
                        .put("image_prompt", "提示词$index")
                        .put("video_prompt", "轻微推近")
                        .put("shot_type", "environment")
                        .put("duration", 3.0))
                }
            }).toString()
        // Drop the final closing braces so the raw payload is truncated, while each shot object is intact.
        val truncated = complete.removeSuffix("]}").trimEnd()
        assertTrue(truncated.startsWith("{"))
        assertTrue("root object still open", !truncated.endsWith("]}"))
        assertTrue(truncated.contains("\"shots\":["))
        val result = CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) { truncated }
        assertEquals(source, result.shots.joinToString("") { it.text })
        assertEquals(units.size, result.shots.size)
    }

    @Test(expected = StoryboardTruncatedException::class)
    fun checkedStoryboardMarksPartialSalvageAsTruncation() {
        val source = "主题句。正文一句。再来一句。"
        val units = StoryboardRules.sourceUnits(source)
        // Only the first shot is complete; the rest of the array is cut off.
        val truncated = """{"title":"主题句。","style_preset":"统一画风","shots":[{"id":1,"source_unit_ids":[1],"subtitle_unit_groups":[[1]],"visual":"画面0","punch":"要点0","image_prompt":"提示词0","video_prompt":"轻微推近","shot_type":"environment","duration":3},{"id":2,"source_unit_ids":[2],"visual":""""
        CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) { truncated }
    }
}
