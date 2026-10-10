package com.historyvideo.app

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketException
import java.util.UUID

class StoryboardRulesTest : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val failures = mutableListOf<String>()
        val tests = javaClass.declaredMethods.filter { it.name.startsWith("test") }.sortedBy { it.name }
        tests.forEach { method ->
            runCatching { method.invoke(this) }.onFailure { error ->
                failures += "${method.name}: ${error.cause?.message ?: error.message}"
            }
        }
        finish(if (failures.isEmpty()) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putInt("test_count", tests.size)
                putInt("failure_count", failures.size)
                putString("stream", if (failures.isEmpty()) "All ${tests.size} storyboard checks passed"
                    else failures.joinToString("\n"))
            })
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        check(expected == actual) { "Expected <$expected>, got <$actual>" }
    }
    private fun assertTrue(value: Boolean) = check(value)
    private fun assertFalse(value: Boolean) = check(!value)
    private fun fail(message: String): Nothing = error(message)

    fun testCompletedCoverRules() = CompletedCoverChecks.run(targetContext)
    fun testProjectCostRules() = ProjectCostChecks.run(targetContext)

    private val theme = "在古代当通房丫鬟到底有多爽？"
    private val hook = "哼，你以为她只会受气？"
    private val rebuttal = listOf(
        "放屁，一个顶级富二代公子哥身边的通房丫鬟，",
        "那才是神仙下凡都换不走的终极极乐，",
        "不仅衣食住行把你伺候得舒舒服服，",
        "在外是全天下男人眼红的绝色尤物，",
        "在内更是随你肆意宠溺、百依百顺的温香软玉。"
    )

    private fun shots(parts: List<String>) = parts.mapIndexed { index, text ->
        Shot(index + 1, text, "独立生图提示词$index", visual = "独立画面$index",
            subtitleChunks = mutableListOf(text))
    }

    private fun response(parts: List<String>, duplicate: Boolean = false) = JSONObject().put(
        "shots", JSONArray().apply {
            parts.forEachIndexed { index, text ->
                put(JSONObject().put("id", index + 1).put("voiceover", text)
                    .put("visual", if (duplicate) "同一画面" else "画面$index")
                    .put("image_prompt", if (duplicate) "同一提示词" else "提示词$index")
                    .put("subtitle_chunks", JSONArray().put(text)))
            }
        }
    ).toString()

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
            fail("Expected invalid storyboard to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    fun testLongRebuttalMatchesFiveBeatsAndIsIdempotent() {
        val expected = listOf(theme, hook) + rebuttal
        val source = expected.joinToString("") + "后来她去了京城。"
        assertEquals(expected, StoryboardRules.opening(source))
        val result = shots(expected + "后来她去了京城。")
        StoryboardRules.validate(source, result)
        val before = result.map { it.json().toString() }
        StoryboardRules.validate(source, result)
        assertEquals(before, result.map { it.json().toString() })
        assertEquals(source, result.joinToString("") { it.text })
    }

    fun testShortRebuttalRemainsOneShotWithItsFirstBeat() {
        val expected = listOf("清末闯关东到底有多苦。", "哼，你真以为都能发财？", "放屁。风雪路上尽是饥寒。")
        assertEquals(expected, StoryboardRules.opening(expected.joinToString("")))
    }

    fun testOpeningPreservesWhitespaceAndLeavesOtherStylesAlone() {
        val expected = listOf("  $theme\n\n", "$hook\n", "放屁！她过得舒心。")
        val source = expected.joinToString("") + "\n后来去了京城。"
        assertEquals(expected, StoryboardRules.opening(source))
        StoryboardRules.validate(source, shots(expected + "\n后来去了京城。"))
        assertEquals(listOf(theme), StoryboardRules.opening(theme + "别以为她只会受气。"))
    }

    fun testLongClauseAndSmallTailDoNotCreateAnIsolatedPunch() {
        val source = theme + hook + "放屁，" + "富贵人家衣食无忧".repeat(8) + "，真好。"
        val parts = StoryboardRules.opening(source)
        assertEquals(source, parts.joinToString(""))
        assertTrue(parts[2].startsWith("放屁，富贵"))
        assertTrue(parts.drop(2).all { it.count(Char::isLetterOrDigit) <= 40 })
        assertTrue(parts.last().count(Char::isLetterOrDigit) >= 8)
    }

    fun testWrongPunctuationAndDuplicatePicturesAreRejected() {
        val parts = listOf(theme, hook) + rebuttal
        val source = parts.joinToString("")
        val changed = shots(parts)
        changed[2].text = changed[2].text.replace('，', ',')
        expectInvalid { StoryboardRules.validate(source, changed) }
        val duplicate = shots(parts)
        duplicate[3].visual = duplicate[2].visual
        expectInvalid { StoryboardRules.validate(source, duplicate) }
        duplicate[3].visual = "另一个画面"
        duplicate[3].imagePrompt = duplicate[2].imagePrompt
        expectInvalid { StoryboardRules.validate(source, duplicate) }
    }

    fun testStaleSubtitleChunksUseTheCurrentVoiceover() {
        val parts = listOf(theme, hook) + rebuttal
        val result = shots(parts)
        result[2].subtitleChunks = mutableListOf("旧口播字幕")
        StoryboardRules.validate(parts.joinToString(""), result)
        assertEquals(listOf(rebuttal[0]), result[2].subtitleChunks)
    }

    fun testBadModelOutputRetriesOnceAndGoodOutputIsUsed() {
        val parts = listOf(theme, hook) + rebuttal
        var calls = 0
        var retries = 0
        val result = CloudApi().checkedShots(parts.joinToString(""), onRetry = { retries++ }) { correction ->
            calls++
            if (calls == 2) assertTrue(correction.contains("上次分镜不合格"))
            response(parts, duplicate = calls == 1)
        }
        assertEquals(2, calls)
        assertEquals(1, retries)
        assertEquals(parts, result.map { it.text })
        var validCalls = 0
        CloudApi().checkedShots(parts.joinToString("")) { validCalls++; response(parts) }
        assertEquals(1, validCalls)
    }

    fun testTwoBadResponsesAreRejectedAndNetworkAbortDoesNotRetry() {
        val parts = listOf(theme, hook) + rebuttal
        var calls = 0
        try {
            CloudApi().checkedShots(parts.joinToString("")) { calls++; response(parts, duplicate = true) }
            fail("Expected failure after the second invalid response")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("未保存错误分镜"))
        }
        assertEquals(2, calls)
        calls = 0
        try {
            CloudApi().checkedShots(parts.joinToString("")) {
                calls++
                throw SocketException("Software caused connection abort")
            }
            fail("Expected transport failure")
        } catch (_: SocketException) {
        }
        assertEquals(1, calls)
    }

    fun testChangedBoundariesCannotReuseOldNumberedFiles() {
        val store = ProjectStore(targetContext)
        val project = VideoProject(UUID.randomUUID().toString(), "验收项目")
        val previous = shots(listOf(theme, hook, rebuttal.joinToString("")))
        previous.forEach { it.audioStatus = "done"; it.imageStatus = "done" }
        project.shots = previous.toMutableList()
        project.script = (listOf(theme, hook) + rebuttal).joinToString("")
        val originalImage = store.image(project, previous[2])
        val originalAudio = store.audio(project, previous[2])
        val originalOutput = store.output(project)
        store.applyStoryboard(project, shots(listOf(theme, hook) + rebuttal))
        assertEquals(store.audio(project, previous[0]), store.audio(project, project.shots[0]))
        assertFalse(originalImage == store.image(project, project.shots[2]))
        assertFalse(originalAudio == store.audio(project, project.shots[2]))
        assertFalse(originalOutput == store.output(project))
        assertEquals("pending", project.shots[2].audioStatus)
        assertEquals(project.script, project.storyboardScript)
        val restored = VideoProject.from(project.json())
        assertEquals(store.audio(project, project.shots[2]), store.audio(restored, restored.shots[2]))
        val revisions = project.shots.map { it.imageRevision to it.audioRevision }
        store.applyStoryboard(project, shots(listOf(theme, hook) + rebuttal))
        assertEquals(revisions, project.shots.map { it.imageRevision to it.audioRevision })
        assertEquals(rebuttal, project.shots.drop(2).map { it.text })
    }

    fun testLegacyProjectLoadsWithoutReorderingOrChangingPaths() {
        val store = ProjectStore(targetContext)
        val source = theme + hook + rebuttal.joinToString("")
        val json = JSONObject().put("id", UUID.randomUUID().toString()).put("title", "旧项目")
            .put("script", source).put("shots", JSONArray().put(shots(listOf(source)).first().json()))
        val project = VideoProject.from(json)
        assertEquals(1, project.shots.size)
        assertEquals(source, project.shots[0].text)
        assertEquals(source, project.storyboardScript)
        assertEquals("001.jpg", store.image(project, project.shots[0]).name)
        assertEquals("001.mp3", store.audio(project, project.shots[0]).name)
        assertEquals("final.mp4", store.output(project).name)
    }
}
