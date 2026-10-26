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
    fun testVideoTimelineRules() = VideoTimelineChecks.run()
    fun testAudioAssetAndShotSettings() = KeywordAudioChecks.run(targetContext)
    fun testProjectEditingRules() = ProjectEditChecks.run(targetContext)
    fun testShotMotionRules() = ShotMotionChecks.run()
    fun testTopicIdeaRules() = TopicIdeaChecks.run(targetContext)
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

    private fun groupAtPunctuation(units: List<StoryboardRules.SourceUnit>): List<List<StoryboardRules.SourceUnit>> {
        val groups = mutableListOf<List<StoryboardRules.SourceUnit>>()
        var current = mutableListOf<StoryboardRules.SourceUnit>()
        for (unit in units) {
            current += unit
            if (StoryboardRules.endsAtPunctuation(unit.text)) {
                groups += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) groups += current
        return groups
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
        assertEquals(listOf(theme), StoryboardRules.opening(source))
        val result = shots(expected + "后来她去了京城。")
        StoryboardRules.validate(source, result)
        val before = result.map { it.json().toString() }
        StoryboardRules.validate(source, result)
        assertEquals(before, result.map { it.json().toString() })
        assertEquals(source, result.joinToString("") { it.text })
    }

    fun testSourceUnitsPreserveExactCopyAndOnlyFixTopicAndRebuttal() {
        val source = "古代做县令到底有多难？哼，你以为只要升堂断案、清查账册、安抚百姓就行吗？放屁，先得应付师爷，再得安抚百姓。第一件事，你打开粮仓。第二件事，发现账册被改。"
        for (granularity in listOf("coarse", "balanced", "fine")) {
            val units = StoryboardRules.sourceUnits(source, granularity)
            assertEquals(source, units.joinToString("") { it.text })
            val fixed = units.filter { it.fixedShot }.map { it.text }
            assertEquals(StoryboardRules.opening(source).first(), fixed.first())
            val hookUnits = units.drop(1).takeWhile { !it.fixedShot }
            assertTrue(hookUnits.size > 1)
            assertTrue(hookUnits.all { !it.fixedShot })
            assertTrue(fixed.drop(1).first().startsWith("放屁"))
            val grouped = units.map { listOf(it.id) }
            assertEquals(units.map { it.text }, StoryboardRules.reconstruct(units, grouped))
        }
        val units = StoryboardRules.sourceUnits(source)
        expectInvalid { StoryboardRules.reconstruct(units, listOf(listOf(1, 2)) + units.drop(2).map { listOf(it.id) }) }
        expectInvalid { StoryboardRules.reconstruct(units, units.drop(1).map { listOf(it.id) }) }
        expectInvalid { StoryboardRules.reconstruct(units, units.map { listOf(it.id) } + listOf(listOf(999))) }
    }

    fun testLongOpeningHookCanBeGroupedIntoMultipleShots() {
        val source = "主题句。哼，你以为她只是个摇手帕招客的俗气女人？她手里还有整座城的权贵隐私，能让达官贵人把银子奉上，还得恭恭敬敬喊她妈妈！放屁，先摆出她的手段。"
        val units = StoryboardRules.sourceUnits(source)
        val hookUnits = units.drop(1).takeWhile { !it.fixedShot }
        assertTrue(hookUnits.size > 1)
        val rebuttalUnits = units.drop(1 + hookUnits.size)
        val hookText = hookUnits.joinToString("") { it.text }
        expectInvalid {
            StoryboardRules.validate(source,
                shots(listOf(units.first().text, hookText) + rebuttalUnits.map { it.text }))
        }
        val hookGroups = groupAtPunctuation(hookUnits).toMutableList()
        if (hookGroups.size > 1 && hookGroups.first().joinToString("") == "哼，") {
            hookGroups[0] = hookGroups[0] + hookGroups.removeAt(1)
        }
        val groups = mutableListOf(listOf(units.first().id))
        hookGroups.forEach { group -> groups += group.map { it.id } }
        rebuttalUnits.forEach { groups += listOf(it.id) }
        val groupedShots = StoryboardRules.reconstruct(units, groups)
        assertTrue(groupedShots.size > 3)
        assertEquals(source, groupedShots.joinToString(""))
        StoryboardRules.validate(source, shots(groupedShots))

        val fixedRebuttalIndex = units.indexOfFirst { it.fixedShot && it.text.startsWith("放屁") }
        val oversized = listOf(units.first().text, hookText) +
            units.drop(fixedRebuttalIndex).map { it.text }
        val correctlySplit = listOf(units.first().text) + hookGroups.map { group ->
            group.joinToString("") { it.text }
        } + units.drop(fixedRebuttalIndex).map { it.text }
        var attempts = 0
        val retried = CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) {
            attempts++
            response(if (attempts == 1) oversized else correctlySplit)
        }
        assertEquals(2, attempts)
        assertEquals(correctlySplit, retried.shots.map { it.text })
    }

    fun testLegacyVoiceoverBoundariesRestorePunctuationFromSource() {
        val source = "主题句。第一段口播，第二段口播！"
        val restored = StoryboardRules.reconstructVoiceoverSegments(source,
            listOf("主题句", "第一段口播", "第二段口播"))
        assertEquals(listOf("主题句。", "第一段口播，", "第二段口播！"), restored)
        assertEquals(source, restored.joinToString(""))
        expectInvalid {
            StoryboardRules.reconstructVoiceoverSegments(source,
                listOf("主题句", "第一段旁白", "第二段口播"))
        }
    }

    fun testLegacyModelVoiceoverResponseIsRebuiltFromOriginalCopy() {
        val source = "主题句。第一段口播，第二段口播！"
        val legacy = JSONObject().put("title", "主题句。")
            .put("style_preset", "统一画风")
            .put("shots", JSONArray().apply {
                listOf("主题句", "第一段口播", "第二段口播").forEachIndexed { index, text ->
                    put(JSONObject().put("id", index + 1).put("voiceover", text)
                        .put("visual", "画面$index").put("image_prompt", "提示词$index"))
                }
            }).toString()
        val units = StoryboardRules.sourceUnits(source)
        val result = CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) { legacy }
        assertEquals(source, result.shots.joinToString("") { it.text })
        assertEquals(listOf("主题句。", "第一段口播，", "第二段口播！"), result.shots.map { it.text })
    }

    fun testShortRebuttalRemainsOneShotWithItsFirstBeat() {
        val expected = listOf("清末闯关东到底有多苦。", "哼，你真以为都能发财？", "放屁。风雪路上尽是饥寒。")
        val source = expected.joinToString("")
        assertEquals(listOf(expected.first()), StoryboardRules.opening(source))
        val units = StoryboardRules.sourceUnits(source)
        assertFalse(units[1].fixedShot)
        assertTrue(units.first { it.text.startsWith("放屁") }.fixedShot)
    }

    fun testOpeningPreservesWhitespaceAndLeavesOtherStylesAlone() {
        val expected = listOf("  $theme\n\n", "$hook\n", "放屁！她过得舒心。")
        val source = expected.joinToString("") + "\n后来去了京城。"
        assertEquals(listOf(expected[0]), StoryboardRules.opening(source))
        StoryboardRules.validate(source, shots(expected + "\n后来去了京城。"))
        assertEquals(listOf(theme), StoryboardRules.opening(theme + "别以为她只会受气。"))
    }

    fun testLongClauseAndSmallTailDoNotCreateAnIsolatedPunch() {
        val source = theme + hook + "放屁，" + "富贵人家衣食无忧".repeat(8) + "，真好。"
        val fixed = StoryboardRules.sourceUnits(source).filter { it.fixedShot }.map { it.text }
        val rebuttalParts = fixed.drop(1)
        assertTrue(rebuttalParts.first().startsWith("放屁，富贵"))
        assertEquals(1, rebuttalParts.size)
        assertTrue(rebuttalParts.single().endsWith("真好。"))
        assertTrue(rebuttalParts.single().count(Char::isLetterOrDigit) > 40)
    }

    fun testShotBoundariesMustFollowPunctuation() {
        val source = "主题句。这个句子完整结束，下一句话也完整结束。"
        val splitMidSentence = shots(listOf("主题句。", "这个句子完整结", "束，下一句话也完整结束。"))
        expectInvalid { StoryboardRules.validate(source, splitMidSentence) }
        val punctuationAligned = shots(listOf("主题句。", "这个句子完整结束，", "下一句话也完整结束。"))
        StoryboardRules.validate(source, punctuationAligned)
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

    fun testLegacyShotFieldNamesAreAccepted() {
        val parts = listOf(theme, hook) + rebuttal
        val legacy = JSONObject().put("shots", JSONArray().apply {
            parts.forEachIndexed { index, text ->
                put(JSONObject().put("shot_id", index + 1).put("text", text)
                    .put("visual", "画面$index").put("image_prompt", "提示词$index"))
            }
        }).toString()
        val result = CloudApi().checkedShots(parts.joinToString("")) { legacy }
        assertEquals(parts, result.map { it.text })
        assertEquals(parts.indices.map { it + 1 }, result.map { it.id })
    }

    fun testSourceUnitResponsesRebuildVoiceoverFromOriginalText() {
        val source = "主题句。哼，完整钩子？放屁，这才是反驳。正文有前后两句。"
        val units = StoryboardRules.sourceUnits(source)
        val hookUnits = units.drop(1).takeWhile { !it.fixedShot }
        val rebuttalStart = 1 + hookUnits.size
        val groups = listOf(listOf(units.first()), hookUnits) + units.drop(rebuttalStart).map { listOf(it) }
        val expectedShots = groups.map { group -> group.joinToString("") { it.text } }
        val response = JSONObject().put("title", "主题句。")
            .put("style_preset", "中国古代真人影视剧照质感")
            .put("shots", JSONArray().apply {
            groups.forEachIndexed { index, group ->
                put(JSONObject().put("id", index + 1)
                    .put("source_unit_ids", JSONArray().apply { group.forEach { put(it.id) } })
                    .put("subtitle_unit_groups", JSONArray().put(JSONArray().apply { group.forEach { put(it.id) } }))
                    .put("visual", "独立画面$index").put("punch", "重点$index")
                    .put("image_prompt", "独立生图提示词$index").put("video_prompt", "轻微推近"))
            }
        }).toString()
        val result = CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) { response }
        assertEquals(source, result.shots.joinToString("") { it.text })
        assertEquals(expectedShots, result.shots.map { it.text })
        assertEquals(expectedShots.map { listOf(it) }, result.shots.map { it.subtitleChunks })
        assertEquals(groups.indices.map { "重点$it" }, result.shots.map { it.punch })
        assertEquals("中国古代真人影视剧照质感", result.stylePreset)
        check(runCatching {
            CloudApi().checkedStoryboard(source, units, enforceOriginalCopy = true) {
                JSONObject().put("shots", JSONArray().put(JSONObject().put("id", 1)
                    .put("source_unit_ids", JSONArray().put(1).put(1)).put("visual", "画面")
                    .put("image_prompt", "提示词"))).toString()
            }
        }.isFailure)
    }

    fun testMalformedStoryboardReportsTheMissingField() {
        val parts = listOf(theme, hook) + rebuttal
        val invalid = JSONObject().put("shots", JSONArray().put(JSONObject()
            .put("id", 1).put("voiceover", theme).put("visual", "主题画面"))).toString()
        try {
            CloudApi().checkedShots(parts.joinToString("")) { invalid }
            fail("Expected missing image_prompt to be rejected")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("缺少 image_prompt 字段"))
        }
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
