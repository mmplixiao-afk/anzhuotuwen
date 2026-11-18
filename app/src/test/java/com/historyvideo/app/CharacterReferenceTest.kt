package com.historyvideo.app

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterReferenceTest {
    @Test
    fun oldShotsAreNotTreatedAsTheProtagonist() {
        val legacy = JSONObject()
            .put("shot_type", "character_closeup")
            .put("has_person", true)
            .put("image_prompt", "一位年轻书生")
        assertFalse(usesProtagonistReference(legacy))
        assertFalse(usesProtagonistReference(JSONObject().put("subject", "配角")))
        assertFalse(usesProtagonistReference(JSONObject().put("character_ref", "none")))
        assertTrue(usesProtagonistReference(JSONObject().put("uses_protagonist_reference", true)))
        assertTrue(usesProtagonistReference(JSONObject().put("subject", "主角")))
        val loaded = Shot.from(JSONObject().put("shot_id", 3).put("text", "屋檐。").put("image_prompt", "空镜屋檐"))
        assertFalse(loaded.usesProtagonistReference)
    }

    @Test
    fun protagonistAppearanceDoesNotStealSupportingCharacters() {
        assertEquals("", protagonistAppearance(emptyList()))
        assertEquals("方脸青袍", protagonistAppearance(listOf(
            StoryCharacter("掌柜", "花白胡须", "other"),
            StoryCharacter("主角", "方脸青袍", "protagonist")
        )))
        assertEquals("", protagonistAppearance(listOf(
            StoryCharacter("掌柜", "花白胡须", "other"),
            StoryCharacter("旅人", "青衣", "other")
        )))
        assertEquals("短须布袍", protagonistAppearance(listOf(StoryCharacter("人物", "短须布袍"))))
        assertEquals("", protagonistAppearance(listOf(StoryCharacter("配角", "圆脸", "other"))))
    }

    @Test
    fun batchGeneratesOneSheetThenEditsOnlyProtagonistShots() {
        val missing = planImageBatch(listOf(1 to false, 2 to true, 3 to true), referenceReady = false)
        assertTrue(missing.generateSheet)
        assertEquals(listOf(ShotImageRoute.TEXT, ShotImageRoute.EDIT, ShotImageRoute.EDIT), missing.shots.map { it.route })
        val ready = planImageBatch(listOf(1 to false, 2 to true), referenceReady = true)
        assertFalse(ready.generateSheet)
        assertEquals(ShotImageRoute.EDIT, ready.shots[1].route)
        val scenery = planImageBatch(listOf(4 to false, 5 to false), referenceReady = false)
        assertFalse(scenery.generateSheet)
        assertTrue(scenery.shots.all { it.route == ShotImageRoute.TEXT })
        assertEquals(ShotImageRoute.BLOCKED, shotImageRoute(usesProtagonist = true, referenceReady = false))
        assertEquals(ShotImageRoute.TEXT, shotImageRoute(usesProtagonist = false, referenceReady = false))
    }

    @Test
    fun referencePromptDoesNotCopyTheSheetAndPlainShotsStayPlain() {
        val edited = buildShotImagePrompt("全局画风", "统一画风", "驿站灯下放下茶盏。", true)
        assertTrue(edited.indexOf("全局画风") < edited.indexOf("统一画风"))
        assertTrue(edited.indexOf("统一画风") < edited.indexOf("输入图片只作为人物"))
        assertTrue(edited.contains("脸型、发式、服装和年龄"))
        assertTrue(edited.contains("没有胡须就不要加胡须"))
        assertTrue(edited.contains("不要复制它的构图或背景"))
        assertTrue(edited.contains("全新的写实古装场景"))
        assertTrue(edited.endsWith("驿站灯下放下茶盏。"))
        val plain = buildShotImagePrompt("全局画风", "统一画风", "空镜屋檐。", false)
        assertFalse(plain.contains("不要复制"))
        assertFalse(plain.contains("人物参考"))
        val sheet = buildCharacterSheetPrompt("全局画风", "写实古装", "三十岁方脸短须")
        assertTrue(sheet.contains("正面或三分之四侧面"))
        assertTrue(sheet.contains("不要Q版"))
        assertTrue(sheet.contains("三十岁方脸短须"))
    }

    @Test
    fun cangyuanEditPayloadPollsTheEditTaskAndDropsBadUrls() {
        val body = cangyuanEditRequestBody(
            "gpt-image-2.5",
            "镜头",
            "864x1536",
            listOf(" https://cdn.example.com/a.png ", "/tmp/a.jpg", "data:image/png;base64,aaaa", "https://cdn.example.com/b.png")
        )
        assertEquals(
            setOf("model", "prompt", "n", "size", "response_format", "async", "images"),
            body.keys().asSequence().toSet()
        )
        assertEquals("gpt-image-2.5", body.getString("model"))
        assertEquals(1, body.getInt("n"))
        assertEquals("url", body.getString("response_format"))
        assertTrue(body.getBoolean("async"))
        assertEquals("864x1536", body.getString("size"))
        assertFalse(body.has("quality"))
        assertFalse(body.has("mask"))
        val images = body.getJSONArray("images")
        assertEquals(2, images.length())
        assertEquals(
            "https://direct-api.cangyuansuanli.cn/v1/images/edits/task_9",
            cangyuanPollUrl("https://direct-api.cangyuansuanli.cn/v1/images/edits", "task_9")
        )
        assertFalse(cangyuanPollUrl("https://host/v1/images/edits", "abc").contains("/generations/"))
        val generation = cangyuanGenerationRequestBody("gpt-image-2", "参考图", "1024x1024")
        assertFalse(generation.has("images"))
        assertFalse(imageErrorCanResubmit(imagePollTimeout("task_9")))
        assertFalse(imageErrorCanResubmit("人物参考图没有返回可公开访问的 HTTPS 地址"))
        assertTrue(isCangyuanImageHost("https://www.ai.cangyuansuanli.cn/v1"))
        assertFalse(isCangyuanImageHost("https://api.openai.com/v1"))
    }

    @Test
    fun nonCangyuanEditsUseMultipartWithoutAsync() {
        val spec = multipartEditSpec("user-model", "镜头", "1024x1792", 1)
        assertEquals("b64_json", spec.fields.getValue("response_format"))
        assertEquals("image", spec.fileField)
        assertFalse(spec.fields.containsKey("async"))
        assertFalse(spec.fields.containsKey("quality"))
        assertEquals("image[]", multipartEditSpec("user-model", "镜头", "1024x1792", 2).fileField)
        assertEquals("", persistedImageUrl("data:image/png;base64,aaaa"))
        assertEquals("", persistedImageUrl("http://cdn.example.com/a.png"))
        assertFalse(referenceAssetUsable(20_000, "", requiresPublicUrl = true))
        assertTrue(referenceAssetUsable(20_000, "", requiresPublicUrl = false))
        assertTrue(referenceAssetUsable(20_000, "https://cdn.example.com/ref.png", requiresPublicUrl = true))
    }

    @Test
    fun concurrentCallsGenerateTheReferenceOnceAndShareFailure() = runBlocking {
        val gate = ReferenceGenerationGate()
        val calls = AtomicInteger()
        var ready = false
        val jobs = (1..6).map {
            async {
                gate.ensure(needed = true, ready = { ready }, cancelled = { false }) {
                    calls.incrementAndGet()
                    delay(30)
                    ready = true
                }
            }
        }
        jobs.forEach { it.await() }
        assertEquals(1, calls.get())
        val failed = ReferenceGenerationGate()
        val failures = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                failed.ensure(true, { false }, { false }) {
                    failures.incrementAndGet()
                    error("没有 HTTPS 地址")
                }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { failed.ensure(true, { false }, { false }) { failures.incrementAndGet() } }
        }
        assertEquals(1, failures.get())
        assertThrows(CancellationException::class.java) {
            runBlocking { failed.ensure(true, { false }, { true }) { failures.incrementAndGet() } }
        }
        assertEquals(1, failures.get())
    }

    @Test
    fun oldProjectJsonRoundTripsWithoutForcingAReference() {
        val id = UUID.randomUUID().toString()
        val legacy = JSONObject().put("id", id).put("title", "旧项目").put("shots", JSONArray().put(
            JSONObject().put("shot_id", 1).put("text", "甲。").put("image_prompt", "街市")
        ))
        val loaded = VideoProject.from(legacy)
        assertTrue(loaded.characters.isEmpty())
        assertTrue(loaded.characterReferences.isEmpty())
        assertFalse(loaded.shots.single().usesProtagonistReference)
        loaded.duration = 0.0
        loaded.characters += StoryCharacter("主角", "方脸青袍", "protagonist")
        loaded.characterReferences += CharacterReference("主角", "https://cdn.example.com/ref.png", "done", "方脸青袍")
        loaded.shots[0].usesProtagonistReference = true
        loaded.shots[0].imageUrl = "https://cdn.example.com/shot.png"
        loaded.characterReferences.single().prompt = "已保存的女性主角提示词"
        val restored = VideoProject.from(JSONObject(loaded.json().toString()))
        assertEquals("https://cdn.example.com/ref.png", restored.characterReferences.single().remoteUrl)
        assertEquals("已保存的女性主角提示词", restored.characterReferences.single().prompt)
        assertEquals("方脸青袍", restored.characters.single().appearance)
        assertTrue(restored.shots.single().usesProtagonistReference)
        assertEquals("https://cdn.example.com/shot.png", restored.shots.single().imageUrl)
        val tainted = loaded.json()
        tainted.getJSONArray("character_references").getJSONObject(0).put("remote_url", "data:image/png;base64,aaaa")
        assertEquals("", VideoProject.from(tainted).characterReferences.single().remoteUrl)
        assertEquals("已保存的女性主角提示词", VideoProject.from(tainted).characterReferences.single().prompt)
        assertTrue(parseStoryCharacters(JSONObject()).isEmpty())
    }

    @Test
    fun referencePromptFollowsWrittenSexAndDoesNotDefaultToAMan() {
        val woman = buildCharacterSheetPrompt("全局", "写实", "二十岁女子，柳叶眉，乌发")
        assertEquals(ProtagonistSex.FEMALE, protagonistSex("二十岁女子，柳叶眉，乌发"))
        assertTrue(woman.contains("主角是女性"))
        assertTrue(woman.contains("柳叶眉"))
        assertFalse(woman.contains("主角是男性"))
        assertFalse(woman.contains("发髻、胡须"))
        val contaminated = buildCharacterSheetPrompt("", "", "年轻女子，短须，青衫")
        assertEquals(ProtagonistSex.FEMALE, protagonistSex("年轻女子，短须，青衫"))
        assertTrue(contaminated.contains("主角是女性"))
        assertFalse(contaminated.contains("短须"))
        val man = buildCharacterSheetPrompt("全局", "写实", "三十岁方脸短须")
        assertEquals(ProtagonistSex.MALE, protagonistSex("三十岁方脸短须"))
        assertTrue(man.contains("主角是男性"))
        assertTrue(man.contains("三十岁方脸短须"))
        assertFalse(man.contains("主角是女性"))
        val unspecified = buildCharacterSheetPrompt("全局", "写实", "方脸，青布袍")
        assertEquals(ProtagonistSex.UNSPECIFIED, protagonistSex("方脸，青布袍"))
        assertTrue(unspecified.contains("没有写明主角性别"))
        assertTrue(unspecified.contains("方脸，青布袍"))
        assertFalse(unspecified.contains("主角是男性"))
        assertFalse(unspecified.contains("发髻、胡须"))
        val blank = buildCharacterSheetPrompt("", "", "")
        assertFalse(blank.contains("发髻、胡须"))
        assertTrue(blank.contains("没有写明主角性别"))
        assertEquals(ProtagonistSex.UNSPECIFIED, protagonistSex("其他路人"))
        assertEquals(ProtagonistSex.UNSPECIFIED, protagonistSex("书生，夫人在侧"))
        assertEquals(ProtagonistSex.FEMALE, protagonistSex("她是皇后"))
    }

    @Test
    fun savedPromptIsWhatTheNextRequestUsesAndOldProjectsKeepTheirImage() {
        val computed = buildCharacterSheetPrompt("全局", "写实", "三十岁方脸短须")
        val saved = "自定义：二十岁女性主角，柳叶眉，不要胡须"
        assertEquals(saved, characterSheetRequestPrompt("  $saved  ", computed))
        assertEquals(computed, characterSheetRequestPrompt("  ", computed))
        val legacy = CharacterReference.from(JSONObject()
            .put("name", "主角")
            .put("remote_url", "https://cdn.example.com/old.png")
            .put("status", "done")
            .put("appearance", "三十岁方脸短须"))
        assertEquals("", legacy.prompt)
        assertEquals("https://cdn.example.com/old.png", legacy.remoteUrl)
        assertEquals("done", legacy.status)
        storeReferencePrompt(legacy, saved)
        assertEquals(saved, legacy.prompt)
        assertEquals("https://cdn.example.com/old.png", legacy.remoteUrl)
        assertEquals("done", legacy.status)
        val reloaded = CharacterReference.from(JSONObject(legacy.json().toString()))
        assertEquals(saved, reloaded.prompt)
        assertEquals("https://cdn.example.com/old.png", reloaded.remoteUrl)
        val root = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .first { java.io.File(it, "app/src/main/java/com/historyvideo/app/GenerationService.kt").isFile }
        val source = java.io.File(root, "app/src/main/java/com/historyvideo/app/GenerationService.kt").readText()
        val sheet = source.substringAfter("private suspend fun generateCharacterSheet")
            .substringBefore("private suspend fun optimizeAndRegenerateImage")
        assertTrue(sheet.contains("characterSheetRequestPrompt"))
        assertTrue(sheet.contains("reference.remoteUrl = image.remoteUrl"))
        assertTrue(sheet.contains("reference.prompt = prompt"))
        assertFalse(sheet.contains("imageStatus = \"pending\""))
        val activity = java.io.File(root, "app/src/main/java/com/historyvideo/app/MainActivity.kt").readText()
        assertTrue(activity.contains("import kotlinx.coroutines.isActive"))
        assertTrue(activity.contains("保存提示词"))
        assertTrue(activity.contains("参考图生图提示词"))
    }
}
