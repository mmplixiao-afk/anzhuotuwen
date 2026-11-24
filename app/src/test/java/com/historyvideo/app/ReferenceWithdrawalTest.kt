package com.historyvideo.app

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceWithdrawalTest {
    @Test
    fun plainShotPromptDoesNotAttachReferenceImages() {
        val prompt = buildShotImagePrompt("全局画风", "统一画风", "驿站灯下放下茶盏。", false, listOf("盛长风", "叶青城"))
        assertFalse(prompt.contains("参考图"))
        assertFalse(prompt.contains("第一张"))
        assertTrue(prompt.contains("全局画风"))
        assertTrue(prompt.endsWith("驿站灯下放下茶盏。"))
        val body = cangyuanGenerationRequestBody("gpt-image-2", prompt, "1024x1024")
        assertFalse(body.has("images"))
    }

    @Test
    fun storyboardInstructionDoesNotRequireCharacterIds() {
        val instruction = StoryboardRules.instruction("主题句。", listOf(StoryboardRules.SourceUnit(1, "主题句。", true)))
        assertFalse(instruction.contains("character_ids"))
        assertTrue(instruction.contains("image_prompt"))
        val shots = java.io.File(sourceRoot(), "app/src/main/assets/prompts/shots.md").readText()
        assertFalse(shots.contains("uses_protagonist_reference"))
        assertFalse(shots.contains("character_ids"))
        assertTrue(shots.contains("image_prompt"))
    }

    @Test
    fun interfaceHasNoReferenceEntryAndShotRequestsStayTextOnly() {
        val activity = java.io.File(sourceRoot(), "app/src/main/java/com/historyvideo/app/MainActivity.kt").readText()
        val shots = activity.substringAfter("private fun ShotsScreen").substringBefore("private fun ShotListItem")
        val project = activity.substringAfter("private fun ProjectOverviewScreen").substringBefore("private fun CompletedResultScreen")
        assertFalse(activity.contains("private fun ReferencesScreen"))
        assertFalse(activity.contains("navigate(\"references\")"))
        assertFalse(shots.contains("参考图"))
        assertFalse(project.contains("参考图"))
        assertTrue(activity.contains("生图提示词"))
        val service = java.io.File(sourceRoot(), "app/src/main/java/com/historyvideo/app/GenerationService.kt").readText()
        val request = service.substringAfter("private fun requestShotImage")
            .substringBefore("private suspend fun optimizeAndRegenerateImage")
        assertTrue(request.contains("api.image("))
        assertFalse(request.contains("editImage"))
        assertFalse(request.contains("prepareShotReferences"))
        assertFalse(request.contains("generateCharacterSheet"))
        assertFalse(service.contains("private suspend fun generateCharacterSheet"))
    }

    @Test
    fun oldProjectDataStillLoadsAndPolyphonicSpeechStays() {
        val legacy = VideoProject.from(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("title", "旧项目")
            .put("script", "多领几石(=担)俸禄。")
            .put("character_references", JSONArray().put(JSONObject()
                .put("name", "主角")
                .put("remote_url", "https://cdn.example.com/ref.png")
                .put("status", "done")
                .put("prompt", "已保存的参考图提示词")))
            .put("shots", JSONArray().put(JSONObject()
                .put("shot_id", 1)
                .put("text", "多领几石(=担)俸禄。")
                .put("image_prompt", "街市")
                .put("uses_protagonist_reference", true)
                .put("character_ids", JSONArray().put("c1")))))
        assertEquals("https://cdn.example.com/ref.png", legacy.characterReferences.single().remoteUrl)
        assertEquals("已保存的参考图提示词", legacy.characterReferences.single().prompt)
        assertEquals(listOf("c1"), legacy.shots.single().characterIds)
        assertTrue(legacy.shots.single().usesProtagonistReference)
        val spoken = PronunciationMarkup.clean("多领几石(=担)俸禄")
        val shown = PronunciationMarkup.display("多领几石(=担)俸禄")
        assertFalse(spoken.contains("(=担)"))
        assertTrue(spoken.contains("石"))
        assertEquals("多领几石俸禄", shown)
        assertTrue(PronunciationMarkup.parse("多领几石(=担)俸禄").rules.isNotEmpty())
    }

    private fun sourceRoot() = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
        .first { java.io.File(it, "app/src/main/java/com/historyvideo/app/MainActivity.kt").isFile }
}
