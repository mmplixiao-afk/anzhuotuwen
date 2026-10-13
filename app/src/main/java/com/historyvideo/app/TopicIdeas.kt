package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
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
    const val OUTPUT_INSTRUCTION = """【选题返回格式】只返回严格 JSON 对象，包含 ideas 数组；提示词未指定数量时默认 6 条。
每条字段：id（可选整数）、title（非空字符串，完整主题及口播第一句）、category（可选赛道分类）、direction（非空剧情推进大纲，约120–200字）、tags（3–5个非空短标签字符串）、reason（可选推荐理由）。
剧情大纲用于后续生成正文，请交代人物身份反差、三个递进事件和结尾收扣。题材、文风和内容要求以用户设置的选题提示词为准，不输出 Markdown。"""

    fun parseResponse(raw: String): MutableList<TopicIdea> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "选题返回内容不是 JSON 对象" }
        val json = JSONObject(raw.substring(start, end + 1))
        val array = json.optJSONArray("ideas") ?: error("选题 JSON 缺少 ideas 数组")
        require(array.length() in 1..12) { "选题数量应为 1–12 条，默认 6 条" }
        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: error("第 ${index + 1} 条选题应为对象")
            fun requiredText(field: String): String {
                val text = (item.opt(field) as? String)?.trim().orEmpty()
                require(text.isNotBlank()) { "第 ${index + 1} 条选题缺少 $field" }
                return text
            }
            val title = requiredText("title")
            val direction = requiredText("direction")
            val tagsArray = item.optJSONArray("tags") ?: error("第 ${index + 1} 条选题缺少 tags 数组")
            val tags = (0 until tagsArray.length()).map { tagIndex ->
                val tag = (tagsArray.opt(tagIndex) as? String)?.trim().orEmpty()
                require(tag.isNotBlank()) { "第 ${index + 1} 条选题的标签应为非空字符串" }
                tag
            }
            require(tags.size in 3..5) { "第 ${index + 1} 条选题应包含 3–5 个标签" }
            val id = if (item.isNull("id")) null else {
                val number = item.opt("id") as? Number ?: error("选题 id 应为整数")
                require(number.toDouble() == number.toInt().toDouble()) { "选题 id 应为整数" }
                number.toInt()
            }
            TopicIdea(id = id, title = title, category = (item.opt("category") as? String).orEmpty().trim(),
                direction = direction, tags = tags, reason = (item.opt("reason") as? String).orEmpty().trim())
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
