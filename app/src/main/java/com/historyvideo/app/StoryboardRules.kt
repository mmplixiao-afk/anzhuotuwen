package com.historyvideo.app

internal object StoryboardRules {
    data class SourceUnit(val id: Int, val text: String, val fixedShot: Boolean)

    private val sentenceEnd = Regex("[。！？!?]")
    private val hookStart = Regex("^(?:他娘的|哼)(?:[，,。！!])?")
    private val rebuttalStart = Regex("放屁(?=[。！？!?，,；;\\s]|$)")
    private val punch = Regex("^放屁[。！!，,；;]?")
    private val clauses = Regex("[^，,；;：:。！？!?]+(?:[，,；;：:。！？!?]+|$)")
    private val sceneStarts = listOf("不仅", "在外", "在内", "而在", "到了")

    private fun spokenCount(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) count++
            index += Character.charCount(codePoint)
        }
        return count
    }

    // Port of the PC adapter's _opening_shots; retain original whitespace as well.
    fun opening(source: String): List<String> {
        val titleEnd = sentenceEnd.find(source)?.range?.last?.plus(1) ?: return emptyList()
        var hookOffset = titleEnd
        while (hookOffset < source.length && source[hookOffset].isWhitespace()) hookOffset++
        val title = source.substring(0, hookOffset)
        val afterTitle = source.substring(hookOffset)
        if (!hookStart.containsMatchIn(afterTitle)) return listOf(title)
        val start = rebuttalStart.find(afterTitle) ?: return listOf(title)
        if (afterTitle.substring(0, start.range.first).isBlank()) return listOf(title)
        val offset = hookOffset + start.range.first
        val afterHook = source.substring(offset)
        val punchEnd = punch.find(afterHook)?.range?.last?.plus(1) ?: return listOf(title)
        val end = sentenceEnd.find(afterHook, punchEnd)?.range?.last?.plus(1) ?: return listOf(title)
        val rebuttal = afterHook.substring(0, end)
        val prefix = listOf(title, source.substring(hookOffset, offset))
        if (spokenCount(rebuttal) <= 32) return prefix + rebuttal

        val parts = mutableListOf<String>()
        var current = ""
        for (match in clauses.findAll(rebuttal)) {
            var clause = match.value
            val changesScene = sceneStarts.any { clause.trimStart().startsWith(it) }
            if (current.isNotEmpty() && (spokenCount(current + clause) > 32 ||
                    (changesScene && spokenCount(current) > 2))) {
                parts += current
                current = ""
            }
            while (spokenCount(clause) > 32) {
                if (current.isNotEmpty()) {
                    parts += current
                    current = ""
                }
                parts += clause.take(28)
                clause = clause.drop(28)
            }
            current += clause
        }
        if (current.isNotEmpty()) {
            if (parts.isNotEmpty() && spokenCount(current) < 8 &&
                spokenCount(parts.last() + current) <= 40) {
                parts[parts.lastIndex] += current
            } else parts += current
        }
        if (parts.size > 1 && spokenCount(parts.first()) <= 2) {
            parts[1] = parts[0] + parts[1]
            parts.removeAt(0)
        }
        return prefix + parts
    }

    fun sourceUnits(source: String, granularity: String = "balanced"): List<SourceUnit> {
        val maxUnitLength = when (granularity) {
            "coarse" -> 24
            "fine" -> 12
            else -> 16
        }
        val fixedOpening = opening(source)
        val prefix = fixedOpening.joinToString("").takeIf { source.startsWith(it) }.orEmpty()
        val pieces = mutableListOf<Pair<String, Boolean>>()
        fixedOpening.forEach { pieces += it to true }
        var start = prefix.length
        var spoken = 0
        var index = start
        val closingQuotes = """”’"'」』）》】)]}〉〕〗〙〛"""
        fun flush(end: Int) {
            if (end > start) pieces += source.substring(start, end) to false
            start = end
            spoken = 0
        }
        while (index < source.length) {
            val codePoint = source.codePointAt(index)
            val end = index + Character.charCount(codePoint)
            if (Character.isLetterOrDigit(codePoint)) spoken++
            val punctuation = codePoint == '\n'.code || codePoint == '\r'.code ||
                codePoint.toChar() in "，,；;：:。！？!?"
            if (punctuation) {
                var boundary = end
                while (boundary < source.length && source[boundary] in closingQuotes) boundary++
                flush(boundary)
                index = boundary
                continue
            }
            if (spoken >= maxUnitLength) {
                var boundary = end
                while (boundary < source.length && source[boundary] in closingQuotes) boundary++
                flush(boundary)
                index = boundary
                continue
            }
            index = end
        }
        flush(source.length)
        require(pieces.joinToString("") { it.first } == source) { "原文分段未能完整覆盖输入" }
        return pieces.mapIndexed { index, (text, fixed) -> SourceUnit(index + 1, text, fixed) }
    }

    fun instruction(source: String, units: List<SourceUnit>, granularity: String = "balanced"): String = buildString {
        val rules = when (granularity) {
            "coarse" -> "粗粒度：每镜约40–65个有效口播字符，可覆盖同场景连续2–3句；不合并不同场景。"
            "fine" -> "细粒度：每镜约12–22个有效口播字符，原则上不超过28字；动作、物件对比、情绪和场景变化可分别成镜。"
            else -> "平衡粒度：每镜约20–35个有效口播字符，原则上不超过40字；完整语义优先。"
        }
        append("\n\n【移动端分镜约束，优先于前文冲突规则】\n")
        append("普通正文使用$rules 短反应句可更短；不要按固定镜头数量拆分。\n")
        append("下面提供的source_units是原文唯一权威来源。只返回相邻、连续的source unit编号分组，不要复述或改写口播文字。每个编号必须且只能出现一次，按升序、连续覆盖全部编号；不能跳号、重复、调序。程序会从原文按编号还原voiceover。\n")
        append("固定镜头单元fixed_shot=true必须独立成镜，不能与其他编号合并或拆开。第1镜是口播第一句。若主题后以他娘的或哼开头且后面出现放屁，钩子和反驳边界按固定单元执行；放屁不可独占镜头。长反驳按PC规则切分。\n")
        append("每镜必须有对应的visual、punch、image_prompt、video_prompt。visual用中文描述时代、地点、主体、动作和情绪；image_prompt是独立可生图的写实古装提示词，约100–180个中文字符，人物外观保持一致。前两镜须有清晰可辨人物。\n")
        append("subtitle_chunks也由程序从原文还原。你只需为每镜返回subtitle_unit_groups：每个子数组是一个字幕自然停顿对应的source unit编号组；按原顺序分完本镜source_unit_ids中的所有编号，不漏、不重、不调序。style_preset填写全局画风。不得输出voiceover或字幕文字副本。\n")
        append("""只输出严格JSON，结构为：{"title":"原主题","style_preset":"统一全局画风","shots":[{"id":1,"source_unit_ids":[1],"subtitle_unit_groups":[[1]],"visual":"","punch":"2到6字","image_prompt":"","video_prompt":""}]}""")
    }

    fun reconstruct(units: List<SourceUnit>, groups: List<List<Int>>): List<String> {
        require(groups.isNotEmpty()) { "分镜结果没有镜头" }
        val byId = units.associateBy { it.id }
        var expectedId = 1
        return groups.mapIndexed { shotIndex, ids ->
            require(ids.isNotEmpty()) { "第 ${shotIndex + 1} 镜没有source_unit_ids" }
            require(ids.all { it == expectedId++ }) { "source_unit_ids必须连续、按序且不重复" }
            val group = ids.map { byId[it] ?: throw IllegalArgumentException("不存在的source unit编号：$it") }
            require(group.none { it.fixedShot } || group.size == 1) { "固定开头镜头不能与相邻文本合并" }
            group.joinToString("") { it.text }
        }.also {
            require(expectedId == units.size + 1) { "分镜source_unit_ids未覆盖完整原文" }
        }
    }

    fun reconstructVoiceoverSegments(source: String, modelVoiceovers: List<String>): List<String> {
        require(modelVoiceovers.isNotEmpty()) { "分镜没有voiceover片段" }
        fun spoken(value: String): String = buildString {
            var offset = 0
            while (offset < value.length) {
                val codePoint = value.codePointAt(offset)
                if (Character.isLetterOrDigit(codePoint)) appendCodePoint(codePoint)
                offset += Character.charCount(codePoint)
            }
        }
        require(spoken(modelVoiceovers.joinToString("")) == spoken(source)) {
            "模型voiceover存在漏字、重复或改写，无法映射回原文"
        }
        val segments = mutableListOf<String>()
        var cursor = 0
        modelVoiceovers.forEachIndexed { index, modelText ->
            val targetCount = spoken(modelText).codePointCount(0, spoken(modelText).length)
            require(targetCount > 0) { "第 ${index + 1} 镜voiceover没有有效口播字符" }
            val start = cursor
            var count = 0
            while (cursor < source.length && count < targetCount) {
                val codePoint = source.codePointAt(cursor)
                if (Character.isLetterOrDigit(codePoint)) count++
                cursor += Character.charCount(codePoint)
            }
            require(count == targetCount) { "第 ${index + 1} 镜voiceover无法从原文定位" }
            if (index == modelVoiceovers.lastIndex) {
                cursor = source.length
            } else {
                while (cursor < source.length && !Character.isLetterOrDigit(source.codePointAt(cursor))) {
                    cursor += Character.charCount(source.codePointAt(cursor))
                }
            }
            segments += source.substring(start, cursor)
        }
        require(segments.joinToString("") == source) { "按模型边界回取原文后未覆盖全文" }
        return segments
    }

    fun reconstructSubtitleChunks(units: List<SourceUnit>, shotUnitIds: List<Int>,
                                  subtitleGroups: List<List<Int>>): List<String> {
        require(subtitleGroups.isNotEmpty()) { "字幕source unit分组为空" }
        require(subtitleGroups.flatten() == shotUnitIds) {
            "subtitle_unit_groups必须按顺序完整覆盖本镜source_unit_ids"
        }
        val byId = units.associateBy { it.id }
        return subtitleGroups.mapIndexed { index, ids ->
            require(ids.isNotEmpty()) { "第 ${index + 1} 条字幕没有source unit" }
            ids.map { id -> byId[id]?.text ?: throw IllegalArgumentException("字幕引用了不存在的source unit：$id") }
                .joinToString("")
        }
    }

    fun validate(source: String, shots: List<Shot>) {
        require(shots.joinToString("") { it.text } == source) {
            "分镜口播与原文不一致，存在漏字、重复或标点改动"
        }
        val expected = opening(source)
        require(shots.take(expected.size).map { it.text } == expected) {
            "开头反驳未按原文短镜边界拆分"
        }
        require(shots.none { it.text.filter(Char::isLetterOrDigit) == "放屁" }) {
            "放屁不能单独占一镜，必须接上第一拍反驳"
        }
        shots.forEach { shot ->
            require(shot.visual.isNotBlank() && shot.imagePrompt.isNotBlank()) {
                "第${shot.id}镜缺少独立的visual或image_prompt"
            }
            if (shot.subtitleChunks.joinToString("") != shot.text) {
                shot.subtitleChunks = mutableListOf(shot.text)
            }
        }
        val rebuttalShots = if (expected.size >= 3) shots.subList(2, expected.size) else emptyList()
        for (values in listOf(rebuttalShots.map { it.visual }, rebuttalShots.map { it.imagePrompt })) {
            val keys = values.map { it.filterNot(Char::isWhitespace) }
            require(keys.distinct().size == keys.size) {
                "反驳短镜重复使用同一画面描述或生图提示词"
            }
        }
    }
}
