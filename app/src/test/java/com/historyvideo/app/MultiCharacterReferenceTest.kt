package com.historyvideo.app

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiCharacterReferenceTest {
    private val script = "盛长风是个三十岁的男子，方脸短须，青袍。叶青城是十八岁的女子，柳叶眉，罗裙。关武是络腮胡大汉，玄铁甲。"

    private fun storyShots() = listOf(
        Shot(id = 1, text = "盛长风推门。", imagePrompt = "盛长风，三十岁男子，方脸短须，青袍，推开木门。",
            visual = "盛长风推门", usesProtagonistReference = true),
        Shot(id = 2, text = "叶青城抬眼。", imagePrompt = "叶青城，十八岁女子，柳叶眉，罗裙，抬眼。",
            visual = "叶青城抬眼"),
        Shot(id = 3, text = "关武拔刀。", imagePrompt = "关武，络腮胡大汉，玄铁甲，拔刀。", visual = "关武拔刀"),
        Shot(id = 4, text = "盛长风与叶青城对坐。", imagePrompt = "盛长风与叶青城对坐饮茶。", visual = "两人对坐"),
        Shot(id = 5, text = "屋檐空镜。", imagePrompt = "空镜屋檐，没有人物。", visual = "屋檐")
    )

    @Test
    fun storyboardIdentifiesEveryNamedCharacterAndKeepsGender() {
        val parsed = parseStoryCharacters(JSONObject()
            .put("characters", JSONArray()
                .put(JSONObject().put("name", "盛长风").put("role", "protagonist").put("sex", "male")
                    .put("appearance", "三十岁男子，方脸短须，青袍"))
                .put(JSONObject().put("name", "叶青城").put("role", "supporting").put("sex", "male")
                    .put("appearance", "十八岁女子，柳叶眉，罗裙"))
                .put(JSONObject().put("name", "关武").put("role", "supporting").put("sex", "male")
                    .put("appearance", "络腮胡大汉，玄铁甲"))))
        val cast = identifyStoryCharacters(script, storyShots(), parsed)
        assertEquals(listOf("盛长风", "叶青城", "关武"), cast.map { it.name })
        assertEquals("protagonist", cast[0].role)
        assertEquals("supporting", cast[1].role)
        assertEquals("male", cast.first { it.name == "盛长风" }.sex)
        assertEquals("female", cast.first { it.name == "叶青城" }.sex)
        assertEquals("male", cast.first { it.name == "关武" }.sex)
        val woman = buildNamedCharacterPrompt("叶青城", cast.first { it.name == "叶青城" }.appearance)
        val man = buildNamedCharacterPrompt("盛长风", cast.first { it.name == "盛长风" }.appearance)
        assertTrue(woman.contains("叶青城是女性"))
        assertTrue(woman.contains("柳叶眉"))
        assertFalse(woman.contains("男性"))
        assertFalse(woman.contains("短须"))
        assertTrue(man.contains("盛长风是男性"))
        assertTrue(man.contains("短须"))
        assertFalse(cast.map { it.name }.contains("主角"))
    }

    @Test
    fun namesAreFoundFromShotsWhenTheStoryboardOnlyHasAProtagonist() {
        val cast = identifyStoryCharacters(script, storyShots(), listOf(
            StoryCharacter("主角", "三十岁男子，方脸短须，青袍", "protagonist")
        ))
        assertEquals(listOf("盛长风", "叶青城", "关武"), cast.map { it.name })
        assertEquals("female", cast.first { it.name == "叶青城" }.sex)
        assertEquals("male", cast.first { it.name == "关武" }.sex)
        assertFalse(cast.any { it.name == "柳叶眉" || it.name == "青袍" || it.name == "罗裙" })
    }

    @Test
    fun unspecifiedGenderIsNotForcedMale() {
        val script = "沈清禾站在廊下，青布袍。沈清禾又回到廊下。"
        val shots = listOf(Shot(id = 1, text = "沈清禾站在廊下。", imagePrompt = "沈清禾，青布袍，站在廊下。", visual = "廊下"))
        val cast = identifyStoryCharacters(script, shots)
        val found = cast.first { it.name == "沈清禾" }
        assertEquals("", found.sex)
        val prompt = buildNamedCharacterPrompt(found.name, found.appearance)
        assertTrue(prompt.contains("没有写明沈清禾的性别"))
        assertTrue(prompt.contains("不要默认画成男性"))
        assertFalse(prompt.contains("沈清禾是男性"))
        assertFalse(prompt.contains("沈清禾是女性"))
    }

    @Test
    fun characterReferencePromptIsPlainTextAndJsonIsNotShown() {
        val computed = buildNamedCharacterPrompt("叶青城", "十八岁女子，柳叶眉，罗裙")
        val json = """{"name":"叶青城","appearance":"十八岁女子","role":"supporting"}"""
        assertEquals(computed, editableReferencePrompt(json, computed))
        assertEquals(computed, editableReferencePrompt("  ", computed))
        assertTrue(characterReferencePromptIsPlainText(computed))
        assertFalse(computed.contains("{"))
        assertFalse(computed.contains("}"))
        assertFalse(computed.contains("\"appearance\""))
        assertFalse(computed.contains("\"image_prompt\""))
        assertFalse(looksLikeStructuredPrompt(computed))
        assertTrue(looksLikeStructuredPrompt(json))
        val reference = CharacterReference(name = "叶青城", appearance = "十八岁女子，柳叶眉，罗裙")
        val edited = "叶青城，十八岁女子，柳叶眉，月白罗裙，正面站立。"
        storeReferencePrompt(reference, edited)
        assertEquals(edited, reference.prompt)
        assertTrue(reference.promptCustom)
        val reloaded = CharacterReference.from(JSONObject(reference.json().toString()))
        assertEquals(edited, reloaded.prompt)
        assertTrue(reloaded.promptCustom)
        assertEquals("https://cdn.example.com/ye.png", CharacterReference.from(
            JSONObject(reloaded.json().put("remote_url", "https://cdn.example.com/ye.png").toString())
        ).remoteUrl)
        assertEquals(edited, editableReferencePrompt(reloaded.prompt, computed))
    }

    @Test
    fun imagePromptSettingIsSeparateFromCharacterPrompts() {
        assertEquals("image", ImagePromptSetting.type)
        assertEquals("生图提示词", ImagePromptSetting.title)
        assertTrue(AppSettings.types.contains(ImagePromptSetting.type))
        val memory = mutableMapOf<String, String>()
        assertEquals("默认生图提示词", ImagePromptSetting.read(memory[ImagePromptSetting.type], "默认生图提示词"))
        ImagePromptSetting.write(memory, "{\"template\":\"通用 JSON 生图提示词\"}")
        assertEquals("{\"template\":\"通用 JSON 生图提示词\"}", ImagePromptSetting.read(memory[ImagePromptSetting.type], "默认生图提示词"))
        val characterPrompt = buildNamedCharacterPrompt("关武", "络腮胡大汉，玄铁甲")
        assertNotEquals(memory.getValue(ImagePromptSetting.type), characterPrompt)
        assertFalse(characterPrompt.contains("{"))
        assertEquals("", ImagePromptSetting.read(null, ""))
    }

    @Test
    fun shotsAttachOnlyTheCharactersWhoAppearAndKeepOrder() {
        val cast = identifyStoryCharacters(script, storyShots())
        val references = syncCharacterReferences(cast, emptyList())
        references.forEach { it.remoteUrl = "https://cdn.example.com/${it.fileKey}.png"; it.status = "done" }
        val usable = { reference: CharacterReference -> isPublicHttpsImageUrl(reference.remoteUrl) }
        val pair = prepareShotReferences(storyShots()[3], cast, references, usable)
        assertEquals(listOf("盛长风", "叶青城"), pair.references.map { it.name })
        assertEquals("第一张参考图是盛长风，第二张参考图是叶青城。", pair.identityLine)
        val woman = prepareShotReferences(storyShots()[1], cast, references, usable)
        assertEquals(listOf("叶青城"), woman.references.map { it.name })
        assertTrue(woman.identityLine.contains("这张参考图是叶青城"))
        assertTrue(woman.identityLine.contains("叶青城"))
        val empty = prepareShotReferences(storyShots()[4], cast, references, usable)
        assertTrue(empty.references.isEmpty())
        assertEquals("", empty.identityLine)
        val legacyShot = Shot(id = 8, text = "甲。", imagePrompt = "街市", usesProtagonistReference = true)
        val legacyCast = listOf(StoryCharacter("主角", "方脸青袍", "protagonist", "male"))
        val legacyReference = CharacterReference("主角", "https://cdn.example.com/ref.png", "done", "方脸青袍", fileKey = "01")
        val legacy = prepareShotReferences(legacyShot, legacyCast, listOf(legacyReference), usable)
        assertEquals("主角", legacy.references.single().name)
        assertTrue(legacy.identityLine.contains("这张参考图是主角"))
        val scenery = Shot(id = 9, text = "屋檐。", imagePrompt = "空镜屋檐")
        assertTrue(prepareShotReferences(scenery, legacyCast, listOf(legacyReference), usable).references.isEmpty())
    }

    @Test
    fun missingReferenceIsSkippedWithoutReorderingTheRest() {
        val cast = identifyStoryCharacters(script, storyShots())
        val references = syncCharacterReferences(cast, emptyList())
        references.first { it.name == "盛长风" }.remoteUrl = "https://cdn.example.com/sheng.png"
        references.first { it.name == "关武" }.remoteUrl = "https://cdn.example.com/guan.png"
        val shot = Shot(id = 6, text = "盛长风、叶青城和关武同框。", imagePrompt = "盛长风、叶青城、关武同框。")
        val prepared = prepareShotReferences(shot, cast, references) { isPublicHttpsImageUrl(it.remoteUrl) }
        assertEquals(listOf("盛长风", "关武"), prepared.references.map { it.name })
        assertEquals("第一张参考图是盛长风，第二张参考图是关武。", prepared.identityLine)
        val prompt = buildShotImagePrompt("全局画风", "统一画风", "三人同框。", true, prepared.references.map { it.name })
        assertTrue(prompt.contains("第一张参考图是盛长风，第二张参考图是关武。"))
        assertTrue(prompt.indexOf("全局画风") < prompt.indexOf("第一张参考图是盛长风"))
        assertTrue(prompt.endsWith("三人同框。"))
        val plain = buildShotImagePrompt("全局画风", "统一画风", "空镜屋檐。", false, listOf("盛长风"))
        assertFalse(plain.contains("参考图是"))
    }

    @Test
    fun editPayloadKeepsReferenceOrderUpToNine() {
        val urls = (1..10).map { "https://cdn.example.com/$it.png" }
        val body = cangyuanEditRequestBody("gpt-image-2", "镜头", "1024x1024", urls)
        val images = body.getJSONArray("images")
        assertEquals(9, images.length())
        assertEquals("https://cdn.example.com/1.png", images.getString(0))
        assertEquals("https://cdn.example.com/2.png", images.getString(1))
        assertEquals("https://cdn.example.com/9.png", images.getString(8))
        assertEquals("image[]", multipartEditSpec("user-model", "镜头", "1024x1792", 3).fileField)
        assertEquals("image", referenceFormField(1))
    }

    @Test
    fun oldProtagonistReferenceMigratesAndCustomPlainPromptSurvives() {
        val legacy = CharacterReference.from(JSONObject()
            .put("name", "主角")
            .put("remote_url", "https://cdn.example.com/ref.png")
            .put("status", "done")
            .put("appearance", "三十岁方脸短须，青袍")
            .put("prompt", "已保存的盛长风参考图提示词"))
        val project = VideoProject(UUID.randomUUID().toString(), "旧项目")
        project.script = script
        project.shots = storyShots().toMutableList()
        project.characterReferences += legacy
        assertTrue(applyIdentifiedCast(project))
        val lead = project.characterReferences.first { it.name == "盛长风" }
        assertEquals("https://cdn.example.com/ref.png", lead.remoteUrl)
        assertEquals("已保存的盛长风参考图提示词", lead.prompt)
        assertEquals("01", lead.fileKey)
        assertTrue(lead.promptCustom)
        assertEquals("done", lead.status)
        val woman = project.characterReferences.first { it.name == "叶青城" }
        assertEquals("", woman.remoteUrl)
        assertEquals("02", woman.fileKey)
        assertTrue(characterReferencePromptIsPlainText(woman.prompt))
        assertTrue(woman.prompt.contains("叶青城是女性"))
        assertFalse(woman.prompt.contains("{"))
        val restored = VideoProject.from(JSONObject(project.json().toString()))
        assertEquals("已保存的盛长风参考图提示词", restored.characterReferences.first { it.name == "盛长风" }.prompt)
        assertEquals("https://cdn.example.com/ref.png", restored.characterReferences.first { it.name == "盛长风" }.remoteUrl)
        assertEquals(woman.prompt, restored.characterReferences.first { it.name == "叶青城" }.prompt)
        assertFalse(restored.shots.first { it.id == 5 }.usesProtagonistReference)
        assertTrue(restored.shots.first { it.id == 1 }.usesProtagonistReference)
        assertFalse(applyIdentifiedCast(restored))
        val untouched = VideoProject.from(JSONObject().put("id", UUID.randomUUID().toString()).put("title", "更旧")
            .put("shots", JSONArray().put(JSONObject().put("shot_id", 1).put("text", "甲。").put("image_prompt", "街市"))))
        assertTrue(untouched.characterReferences.isEmpty())
        assertTrue(untouched.characters.isEmpty())
        assertFalse(untouched.shots.single().usesProtagonistReference)
    }

    @Test
    fun referenceScreenReplacesTheShotCardAndSettingsHasImagePrompt() {
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "app/src/main/java/com/historyvideo/app/MainActivity.kt").isFile }
        val activity = java.io.File(root, "app/src/main/java/com/historyvideo/app/MainActivity.kt").readText()
        val shots = activity.substringAfter("private fun ShotsScreen").substringBefore("private fun ReferencesScreen")
        val references = activity.substringAfter("private fun ReferencesScreen").substringBefore("private fun ShotListItem")
        val settings = activity.substringAfter("private fun SettingsScreen").substringBefore("private fun AudioLibrarySettingsScreen")
        assertFalse(shots.contains("人物参考图"))
        assertFalse(shots.contains("参考图生图提示词"))
        assertTrue(references.contains("参考图生图提示词"))
        assertTrue(references.contains("保存提示词"))
        assertTrue(references.contains("生成全部参考图"))
        assertTrue(references.contains("不要粘贴 JSON"))
        assertTrue(settings.contains("生图提示词"))
        assertTrue(settings.contains("与参考图明文提示词分开保存"))
        assertTrue(activity.contains("第一张") || activity.contains("referenceIdentityLine"))
        val service = java.io.File(root, "app/src/main/java/com/historyvideo/app/GenerationService.kt").readText()
        val request = service.substringAfter("private fun requestShotImage").substringBefore("private suspend fun generateCharacterSheet")
        assertTrue(request.contains("prepareShotReferences"))
        assertTrue(request.contains("editImage"))
        assertFalse(request.contains("characterReferences.firstOrNull()?.remoteUrl"))
    }
}
