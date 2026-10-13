package com.historyvideo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object TopicIdeaChecks {
    fun run(context: Context) {
        val response = JSONObject().put("ideas", JSONArray().apply {
            for (index in 1..6) put(JSONObject().put("id", index)
                .put("title", "古代身份${index}到底有多爽？")
                .put("category", "赛道$index")
                .put("direction", "身份反差$index，依次经历三件事，结尾完成收扣。")
                .put("tags", JSONArray(listOf("身份$index", "反差", "逆袭")))
                .put("reason", "独立爆款逻辑$index"))
        })
        val parsed = TopicIdeas.parseResponse("```json\n$response\n```")
        check(parsed.size == 6 && parsed.map { it.key }.distinct().size == 6)
        check(parsed.map { it.id } == (1..6).toList())
        check(parsed[2].category == "赛道3" && parsed[2].direction.contains("身份反差3"))
        check(parsed[2].tags == listOf("身份3", "反差", "逆袭") && parsed[2].reason == "独立爆款逻辑3")
        val minimal = JSONObject().put("ideas", JSONArray().put(JSONObject()
            .put("title", "题目？").put("direction", "完整大纲").put("tags", JSONArray(listOf("甲", "乙", "丙")))))
        val noOptional = TopicIdeas.parseResponse(minimal.toString()).single()
        check(noOptional.id == null && noOptional.category.isEmpty() && noOptional.reason.isEmpty())
        for (missing in listOf("title", "direction", "tags")) {
            val wrong = JSONObject(response.toString())
            wrong.getJSONArray("ideas").getJSONObject(0).remove(missing)
            check(runCatching { TopicIdeas.parseResponse(wrong.toString()) }.isFailure)
        }
        val wrongTags = JSONObject(response.toString())
        wrongTags.getJSONArray("ideas").getJSONObject(0).put("tags", JSONArray(listOf("甲", 3, "丙")))
        check(runCatching { TopicIdeas.parseResponse(wrongTags.toString()) }.isFailure)
        val decimalId = JSONObject(response.toString())
        decimalId.getJSONArray("ideas").getJSONObject(0).put("id", 1.2)
        check(runCatching { TopicIdeas.parseResponse(decimalId.toString()) }.isFailure)
        check(runCatching { TopicIdeas.parseResponse("不是JSON") }.isFailure)
        check(runCatching { TopicIdeas.parseResponse("{\"ideas\":[]}") }.isFailure)
        check(TopicIdeas.parseTags("甲，乙、丙 丁;甲") == listOf("甲", "乙", "丙", "丁"))

        val store = ProjectStore(context)
        val project = store.create("选题保存验收")
        try {
            project.topicIdeas = parsed
            project.topicCandidates = parsed.map { it.title }.toMutableList()
            project.script = "用户原有正文。"
            project.storyboardScript = project.script
            project.renderRevision = UUID.randomUUID().toString()
            project.shots = mutableListOf(Shot(1, project.script, "原图片", duration = 2.0,
                imageStatus = "done", audioStatus = "done", cutoutEnabled = true))
            store.save(project)
            val image = store.image(project, project.shots[0]).apply { writeBytes(ByteArray(2048) { 7 }) }
            val audio = store.audio(project, project.shots[0]).apply { writeBytes(ByteArray(2048) { 8 }) }
            val oldOutput = store.output(project).apply { writeBytes(ByteArray(2048) { 9 }) }
            val ledger = ProjectCosts(store)
            ledger.add(project.id, CostEntry.image("已有图片", "0.017", shotId = 1))
            val costsBefore = ledger.read(project.id)
            val shotsBefore = project.shots.map { it.json().toString() }
            val revisionBefore = project.renderRevision
            val edited = parsed[2].copy(id = 9, title = "修改后的主题？", category = "修改赛道",
                direction = "修改后明确的三个递进事件与结尾。", tags = listOf("新身份", "新反差", "新逆袭"), reason = "修改后的推荐理由")
            TopicIdeas.saveAndSelect(project, edited)
            store.save(project)
            val restored = requireNotNull(store.get(project.id))
            check(restored.selectedTopic == edited && restored.title == edited.title)
            check(restored.topicIdeas.first { it.key == edited.key } == edited)
            check(restored.topicCandidates[2] == edited.title)
            check(restored.script == "用户原有正文。" && restored.storyboardScript == restored.script)
            check(restored.shots.map { it.json().toString() } == shotsBefore && restored.renderRevision == revisionBefore)
            check(store.image(restored, restored.shots[0]) == image && image.readBytes().all { it == 7.toByte() })
            check(store.audio(restored, restored.shots[0]) == audio && audio.readBytes().all { it == 8.toByte() })
            check(store.output(restored) == oldOutput && oldOutput.isFile && ledger.read(project.id) == costsBefore)
            val input = TopicIdeas.scriptInput(restored)
            check(input.contains(edited.title) && input.contains(edited.direction) && input.contains(edited.category))
            check(input.contains(edited.tags.joinToString("、")) && input.contains(edited.reason))
            check(!input.contains(parsed[2].direction))
            TopicIdeas.updateWritingIdea(restored, "成文中修改标题？", "成文页编辑的大纲")
            check(TopicIdeas.scriptInput(restored).contains("成文页编辑的大纲"))
            check(TopicIdeas.editorIdeas(restored).first { it.key == edited.key }.direction == "成文页编辑的大纲")
            val stale = VideoProject.from(restored.json())
            stale.topicIdeas[2] = parsed[2]
            check(TopicIdeas.editorIdeas(stale).first { it.key == edited.key }.direction == "成文页编辑的大纲")
            val legacy = restored.json().apply { remove("topic_ideas"); remove("selected_topic"); remove("topic_query") }
            val old = VideoProject.from(legacy)
            check(old.topicIdeas.map { it.title } == old.topicCandidates)
            check(old.selectedTopic == null && old.topicQuery.isEmpty())
            check(TopicIdeas.scriptInput(old).contains(old.title))
            val beforeInvalid = restored.json().toString()
            check(runCatching { TopicIdeas.saveAndSelect(restored, edited.copy(direction = "")) }.isFailure)
            check(runCatching { TopicIdeas.saveAndSelect(restored, edited.copy(tags = emptyList())) }.isFailure)
            check(restored.json().toString() == beforeInvalid)
            restored.status = "running"
            check(runCatching { TopicIdeas.saveAndSelect(restored, edited) }.isFailure)
        } finally {
            store.delete(project.id)
        }
    }
}
