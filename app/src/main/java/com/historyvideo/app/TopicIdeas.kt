package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.json.JSONException
import java.util.UUID

/** Complete topic metadata is retained from the API through the document-generation input. */
data class TopicIdea(
    val key: String = UUID.randomUUID().toString(),
    val id: Int? = null,
    val title: String = "",
    val category: String = "",
    val direction: String = "",
    val tags: List<String> = emptyList(),
    val reason: String = ""
) {
    fun json() = JSONObject().apply {
        put("idea_key", key); put("id", id ?: JSONObject.NULL)
        put("title", title); put("category", category); put("direction", direction)
        put("tags", JSONArray(tags)); put("reason", reason)
    }

    companion object {
        fun from(value: JSONObject, fallbackKey: String): TopicIdea = TopicIdea(
            key = value.optString("idea_key").ifBlank { fallbackKey },
            id = (value.opt("id") as? Number)?.toInt(),
            title = value.optString("title"), category = value.optString("category"),
            direction = value.optString("direction"),
            tags = value.optJSONArray("tags")?.let { array ->
                (0 until array.length()).mapNotNull { array.opt(it) as? String }
            } ?: emptyList(),
            reason = value.optString("reason")
        )
    }
}

internal object TopicIdeas {
    const val OUTPUT_INSTRUCTION = """【选题返回格式】本次结果用于题目下拉框，提示词未指定数量时默认 6 条。
请返回严格 JSON：{"ideas":[{"title":"完整题目1"},{"title":"完整题目2"}]}。
title 必须是非空字符串，作为主题及口播第一句；题材、文风和内容要求以用户设置的选题提示词为准。
只需要题目即可。如果设置中的提示词还要求 id、category、direction、tags、reason，可保留这些字段作为隐藏参考，但它们不是必填字段。不要输出 Markdown 或解释文字。"""

    fun parseResponse(raw: String): MutableList<TopicIdea> {
        // Locate the root value before parsing: an array of objects starts with '[' rather than '{'.
        val start = raw.indexOfFirst { it == '{' || it == '[' }
        require(start >= 0) { "选题返回内容不是 JSON 对象或数组" }
        val result = try {
            JSONTokener(raw.substring(start)).nextValue()
        } catch (error: JSONException) {
            throw IllegalArgumentException("选题返回 JSON 不完整或格式无效", error)
        }
        val array = when (result) {
            is JSONArray -> result
            is JSONObject -> {
                // Saved prompts from older versions may still ask for topics instead of ideas.
                val field = listOf("ideas", "topics", "candidates", "titles")
                    .firstOrNull { result.optJSONArray(it) != null }
                require(field != null) {
                    val fields = result.keys().asSequence().toList().joinToString("、").take(120).ifBlank { "无" }
                    "选题返回未找到题目列表，支持 ideas/topics/candidates/titles 数组；实际字段：$fields"
                }
                result.getJSONArray(field)
            }
            else -> error("选题返回内容不是 JSON 对象或数组")
        }
        require(array.length() in 1..12) { "选题数量应为 1–12 条，默认 6 条" }
        return (0 until array.length()).map { index ->
            val value = array.opt(index)
            val item = value as? JSONObject
            val title = when (value) {
                is String -> value.trim()
                is JSONObject -> (value.opt("title") as? String)?.trim().orEmpty()
                else -> ""
            }
            require(title.isNotBlank()) { "第 ${index + 1} 条选题缺少非空字符串 title" }
            fun optionalText(field: String): String {
                if (item == null || item.isNull(field)) return ""
                val text = item.opt(field)
                require(text is String) { "第 ${index + 1} 条选题的 $field 应为字符串" }
                return text.trim()
            }
            val tags = if (item == null || item.isNull("tags")) emptyList() else {
                val tagsArray = item.optJSONArray("tags") ?: error("第 ${index + 1} 条选题的 tags 应为数组")
                require(tagsArray.length() <= 5) { "第 ${index + 1} 条选题最多包含 5 个标签" }
                (0 until tagsArray.length()).map { tagIndex ->
                    val tag = (tagsArray.opt(tagIndex) as? String)?.trim().orEmpty()
                    require(tag.isNotBlank()) { "第 ${index + 1} 条选题的标签应为非空字符串" }
                    tag
                }.distinct()
            }
            val id = when {
                item == null -> index + 1
                item.isNull("id") -> null
                else -> {
                    val number = item.opt("id") as? Number ?: error("选题 id 应为整数")
                    require(number.toDouble() == number.toInt().toDouble()) { "选题 id 应为整数" }
                    number.toInt()
                }
            }
            TopicIdea(id = id, title = title, category = optionalText("category"),
                direction = optionalText("direction"), tags = tags, reason = optionalText("reason"))
        }.toMutableList()
    }

    fun storedIdeas(value: JSONObject): MutableList<TopicIdea> {
        val array = value.optJSONArray("topic_ideas")
        if (array != null && array.length() > 0) return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { TopicIdea.from(it, "stored-topic-$index") }
        }.toMutableList()
        val legacy = value.optJSONArray("topic_candidates") ?: return mutableListOf()
        return (0 until legacy.length()).mapNotNull { index ->
            (legacy.opt(index) as? String)?.takeIf { it.isNotBlank() }?.let {
                TopicIdea(key = "legacy-topic-$index", id = index + 1, title = it)
            }
        }.toMutableList()
    }

    fun parseTags(value: String): List<String> = value.split(Regex("[、，,；;\\s]+"))
        .map(String::trim).filter(String::isNotBlank).distinct()

    fun saveAndSelect(project: VideoProject, idea: TopicIdea) {
        require(project.status != "running") { "请等待当前任务完成后再选择题目" }
        val tags = idea.tags.map(String::trim).filter(String::isNotBlank).distinct()
        require(idea.title.isNotBlank()) { "请输入选题标题" }
        require(idea.direction.isNotBlank()) { "请输入剧情推进大纲" }
        require(tags.size in 3..5) { "请填写 3–5 个标签，用逗号分隔" }
        val edited = idea.copy(title = idea.title.trim(), category = idea.category.trim(),
            direction = idea.direction.trim(), tags = tags, reason = idea.reason.trim())
        val index = project.topicIdeas.indexOfFirst { it.key == edited.key }
        if (index < 0) project.topicIdeas.add(edited) else project.topicIdeas[index] = edited
        project.topicCandidates = project.topicIdeas.map { it.title }.toMutableList()
        project.selectedTopic = edited
        project.title = edited.title
        project.stage = "script"; project.status = "review"
        project.detail = "选题已保存，可根据剧情大纲生成或编辑正文"
        project.completed = 0; project.total = 0; project.taskStartedAt = 0L
    }

    fun editorIdeas(project: VideoProject): List<TopicIdea> {
        val selected = project.selectedTopic
        val candidates = project.topicIdeas.map { if (selected != null && it.key == selected.key) selected else it }
        return (candidates + listOfNotNull(selected)).distinctBy { it.key }
    }

    fun updateWritingIdea(project: VideoProject, title: String, direction: String) {
        project.title = title.trim()
        val selected = project.selectedTopic ?: return
        val edited = selected.copy(title = project.title, direction = direction.trim())
        project.selectedTopic = edited
        val index = project.topicIdeas.indexOfFirst { it.key == edited.key }
        if (index >= 0) project.topicIdeas[index] = edited
        project.topicCandidates = project.topicIdeas.map { it.title }.toMutableList()
    }

    /** Selecting a title needs no outline or tags; manual replacements must detach stale metadata. */
    fun updateWritingTitle(project: VideoProject, title: String, idea: TopicIdea? = null) {
        require(!CurrentProjectRules.inProgress(project)) { "请等待当前任务完成后再修改题目" }
        val trimmed = title.trim()
        if (idea != null) require(trimmed.isNotBlank() && idea.title.trim() == trimmed) { "选题标题无效" }
        project.title = trimmed
        project.selectedTopic = idea?.copy(title = trimmed)
            ?: project.selectedTopic?.takeIf { it.title.trim() == trimmed }
        if (trimmed.isNotBlank() && project.stage == "topic") {
            project.stage = "script"; project.status = "review"
            project.detail = "题目已保存，可生成或编辑正文"
            project.completed = 0; project.total = 0; project.taskStartedAt = 0L
        }
    }

    fun scriptInput(project: VideoProject): String = buildString {
        append("主题 / 口播第一句：").append(project.title.trim())
        project.selectedTopic?.let { idea ->
            if (idea.direction.isNotBlank()) append("\n\n剧情推进大纲：\n").append(idea.direction)
            if (idea.category.isNotBlank()) append("\n\n选题赛道：").append(idea.category)
            if (idea.tags.isNotEmpty()) append("\n\n标签：").append(idea.tags.joinToString("、"))
            if (idea.reason.isNotBlank()) append("\n\n推荐理由：").append(idea.reason)
        }
    }
}
