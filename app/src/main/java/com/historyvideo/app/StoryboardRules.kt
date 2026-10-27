package com.historyvideo.app

internal object StoryboardRules {
    data class SourceUnit(val id: Int, val text: String, val fixedShot: Boolean)

    private val sentenceEnd = Regex("[。！？!?]")
    private val hookStart = Regex("^(?:他娘的|哼)(?:[，,。！!])?")
    private val rebuttalStart = Regex("放屁(?=[。！？!?，,；;\\s]|$)")
    private val punch = Regex("^放屁[。！!，,；;]?")
    private val clauses = Regex("[^，,；;：:。！？!?]+(?:[，,；;：:。！？!?]+|$)")
    private val sceneStarts = listOf("不仅", "在外", "在内", "而在", "到了")
    private val closingQuotes = "”’\"'」』）》】)]}〉〕〗〙〛"

    private data class OpeningPlan(
        val title: String,
        val rebuttalStart: Int? = null,
        val rebuttalEnd: Int? = null,
        val rebuttalParts: List<String> = emptyList()
    )

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

    private fun isCutPunctuation(codePoint: Int): Boolean =
        codePoint == '\n'.code || codePoint == '\r'.code ||
            codePoint.toChar() in "，,；;：:。！？!?、…—"

    private fun boundaryFollowsPunctuation(source: String, offset: Int): Boolean {
        var cursor = offset
        while (cursor > 0) {
            val codePoint = source.codePointBefore(cursor)
            if (isCutPunctuation(codePoint)) return true
            if (Character.isWhitespace(codePoint) || codePoint.toChar() in closingQuotes) {
                cursor -= Character.charCount(codePoint)
            } else {
                return false
            }
        }
        return false
    }

    fun endsAtPunctuation(text: String): Boolean {
        var cursor = text.length
        while (cursor > 0) {
            val codePoint = text.codePointBefore(cursor)
            if (isCutPunctuation(codePoint)) return true
            if (Character.isWhitespace(codePoint) || codePoint.toChar() in closingQuotes) {
                cursor -= Character.charCount(codePoint)
            } else {
                return false
            }
        }
        return false
    }

    private fun hasInternalCutPunctuation(text: String): Boolean {
        var cursor = 0
        while (cursor < text.length) {
            val codePoint = text.codePointAt(cursor)
            cursor += Character.charCount(codePoint)
            if (!isCutPunctuation(codePoint)) continue
            var next = cursor
            while (next < text.length) {
                val following = text.codePointAt(next)
                if (!Character.isWhitespace(following) && following.toChar() !in closingQuotes) break
                next += Character.charCount(following)
            }
            if (next < text.length &&
                text.substring(0, cursor).filter(Char::isLetterOrDigit) !in setOf("哼", "他娘的", "放屁")) return true
        }
        return false
    }

    private fun openingPlan(source: String): OpeningPlan? {
        val titleEnd = sentenceEnd.find(source)?.range?.last?.plus(1) ?: return null
        var hookOffset = titleEnd
        while (hookOffset < source.length && source[hookOffset].isWhitespace()) hookOffset++
        val title = source.substring(0, hookOffset)
        val afterTitle = source.substring(hookOffset)
        if (!hookStart.containsMatchIn(afterTitle)) return OpeningPlan(title)
        val start = rebuttalStart.find(afterTitle) ?: return OpeningPlan(title)
        if (afterTitle.substring(0, start.range.first).isBlank()) return OpeningPlan(title)
        val offset = hookOffset + start.range.first
        val afterHook = source.substring(offset)
        val punchEnd = punch.find(afterHook)?.range?.last?.plus(1) ?: return OpeningPlan(title)
        val end = sentenceEnd.find(afterHook, punchEnd)?.range?.last?.plus(1) ?: return OpeningPlan(title)
        val rebuttal = afterHook.substring(0, end)
        return OpeningPlan(title, offset, offset + end, splitRebuttal(rebuttal))
    }

    // The topic stays fixed as the first shot; the hook remains flexible and uses the selected granularity.
    fun opening(source: String): List<String> = openingPlan(source)?.let { listOf(it.title) }.orEmpty()

    private fun splitRebuttal(rebuttal: String): List<String> {
        if (spokenCount(rebuttal) <= 32) return listOf(rebuttal)
        val parts = mutableListOf<String>()
        var current = ""
        for (match in clauses.findAll(rebuttal)) {
            val clause = match.value
            val changesScene = sceneStarts.any { clause.trimStart().startsWith(it) }
            val keepsPunchWithFirstBeat = parts.isEmpty() && current.startsWith("放屁") &&
                spokenCount(current) <= 2
            if (current.isNotEmpty() && !keepsPunchWithFirstBeat &&
                (spokenCount(current + clause) > 32 ||
                    (changesScene && spokenCount(current) > 2))) {
                parts += current
                current = ""
            }
            current += clause
        }
        if (current.isNotEmpty()) {
            if (parts.isNotEmpty() && spokenCount(current) < 8) {
                parts[parts.lastIndex] += current
            } else parts += current
        }
        if (parts.size > 1 && spokenCount(parts.first()) <= 2) {
            parts[1] = parts[0] + parts[1]
            parts.removeAt(0)
        }
        return parts
    }

    fun sourceUnits(source: String, granularity: String = "balanced"): List<SourceUnit> {
        val maxUnitLength = when (granularity) {
            "coarse" -> 24
            "fine" -> 12
            else -> 16
        }
        val pieces = mutableListOf<Pair<String, Boolean>>()
        val plan = openingPlan(source)
        var cursor = 0
        if (plan != null) {
            pieces += plan.title to true
            cursor = plan.title.length
        }
        fun appendFlexibleRange(rangeStart: Int, rangeEnd: Int) {
            var start = rangeStart
            var spoken = 0
            var index = rangeStart
            fun flush(end: Int) {
                if (end > start) pieces += source.substring(start, end) to false
                start = end
                spoken = 0
            }
            while (index < rangeEnd) {
                val codePoint = source.codePointAt(index)
                val end = index + Character.charCount(codePoint)
                if (Character.isLetterOrDigit(codePoint)) spoken++
                val punctuation = codePoint == '\n'.code || codePoint == '\r'.code ||
                    codePoint.toChar() in "，,；;：:。！？!?、…—"
                if (punctuation) {
                    var boundary = end
                    while (boundary < rangeEnd && source[boundary] in closingQuotes) boundary++
                    flush(boundary)
                    index = boundary
                    continue
                }
                if (spoken >= maxUnitLength) {
                    var boundary = end
                    while (boundary < rangeEnd && source[boundary] in closingQuotes) boundary++
                    flush(boundary)
                    index = boundary
                    continue
                }
                index = end
            }
            flush(rangeEnd)
        }

        val rebuttalStart = plan?.rebuttalStart
        val rebuttalEnd = plan?.rebuttalEnd
        if (rebuttalStart != null && rebuttalEnd != null && plan.rebuttalParts.isNotEmpty()) {
            appendFlexibleRange(cursor, rebuttalStart)
            plan.rebuttalParts.forEachIndexed { index, part -> pieces += part to (index == 0) }
            cursor = rebuttalEnd
        }
        appendFlexibleRange(cursor, source.length)
        require(pieces.joinToString("") { it.first } == source) { "原文分段未能完整覆盖输入" }
        return pieces.mapIndexed { index, (text, fixed) -> SourceUnit(index + 1, text, fixed) }
    }

    fun instruction(source: String, units: List<SourceUnit>, granularity: String = "balanced",
                    firstBatch: Boolean = true): String = buildString {
        val rules = when (granularity) {
            "coarse" -> "粗粒度：每镜约40–65个有效口播字符，可覆盖同场景连续2–3句；不合并不同场景。"
            "fine" -> "细粒度：每镜约12–22个有效口播字符，原则上不超过28字；动作、物件对比、情绪和场景变化可分别成镜。"
            else -> "平衡粒度：每镜约20–35个有效口播字符，原则上不超过40字；完整语义优先。"
        }
        append("\n\n【移动端分镜约束，优先于前文冲突规则】\n")
        append("普通正文使用$rules 短反应句可更短，字数是目标范围；所有镜头边界必须落在标点之后，不得在句子中间截断。没有自然标点可切的长句保持完整。不要按固定镜头数量拆分。\n")
        append("下面提供的source_units是原文唯一权威来源。只返回相邻、连续的source unit编号分组，不要复述或改写口播文字。每个编号必须且只能出现一次，按升序、连续覆盖本次提供的全部编号；不能跳号、重复、调序。程序会从原文按编号还原voiceover。\n")
        append("固定边界单元fixed_shot=true必须独立成镜，不能与其他编号合并或拆开。")
        if (firstBatch) append("第1镜是口播第一句。若主题后以他娘的或哼开头且后面出现放屁，放屁前的钩子按所选粒度、自然语义和画面变化拆分，切分点必须在标点之后；“哼/他娘的”不能单独成镜。反驳第一镜必须从“放屁”开始，不能把它并入上一镜；其后的反驳镜头按标点边界灵活分组。放屁不能单独占一镜，须接上第一拍反驳。")
        append("\n")
        append("每镜必须有对应的visual、punch、image_prompt、video_prompt。visual用中文描述时代、地点、主体、动作和情绪；image_prompt是独立可生图的写实古装提示词，约100–180个中文字符，人物外观保持一致。")
        if (firstBatch) append("前两镜须有清晰可辨人物。")
        append("\n")
        append("subtitle_chunks也由程序从原文还原。你只需为每镜返回subtitle_unit_groups：每个子数组是一个字幕自然停顿对应的source unit编号组；按原顺序分完本镜source_unit_ids中的所有编号，不漏、不重、不调序。style_preset填写全局画风。不得输出voiceover或字幕文字副本。\n")
        append("JSON 必须紧凑（不要换行缩进），并且必须完整闭合；输出被截断会导致本次分镜失败。\n")
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
            require(group.none { it.fixedShot } || group.size == 1) { "固定边界单元不能与相邻文本合并" }
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

    fun validate(source: String, shots: List<Shot>, granularity: String = "balanced") {
        require(shots.joinToString("") { it.text } == source) {
            "分镜口播与原文不一致，存在漏字、重复或标点改动"
        }
        var boundaryOffset = 0
        shots.dropLast(1).forEachIndexed { index, shot ->
            boundaryOffset += shot.text.length
            require(boundaryFollowsPunctuation(source, boundaryOffset)) {
                "第${index + 1}镜与第${index + 2}镜之间不能从句子中间切分，镜头边界必须落在标点之后"
            }
        }
        val plan = openingPlan(source)
        val expectedTitle = plan?.title
        if (expectedTitle != null) require(shots.firstOrNull()?.text == expectedTitle) {
            "第1镜必须只包含原文主题句"
        }
        val maxHookLength = when (granularity) {
            "coarse" -> 65
            "fine" -> 28
            else -> 40
        }
        var rebuttalStartShot: Int? = null
        var rebuttalEndShot: Int? = null
        if (plan?.rebuttalStart != null && plan.rebuttalEnd != null && plan.rebuttalParts.isNotEmpty()) {
            var offset = 0
            for (index in 0..shots.size) {
                if (offset == plan.rebuttalStart) {
                    rebuttalStartShot = index
                }
                if (offset == plan.rebuttalEnd) {
                    rebuttalEndShot = index
                    break
                }
                if (index < shots.size) offset += shots[index].text.length
            }
            val startIndex = rebuttalStartShot
                ?: throw IllegalArgumentException("放屁反驳段未从独立镜头边界开始")
            val endIndex = rebuttalEndShot
                ?: throw IllegalArgumentException("开头反驳必须在原文首个句末标点处结束")
            require(startIndex > 0 && endIndex > startIndex &&
                shots.take(startIndex).joinToString("") == source.substring(0, plan.rebuttalStart)) {
                "开头反驳必须从“放屁”处开始"
            }
            val hookShots = shots.subList(1, startIndex)
            require(hookShots.none { it.text.filter(Char::isLetterOrDigit) in setOf("哼", "他娘的") }) {
                "开头钩子的语气词不能单独占一镜"
            }
            require(hookShots.all {
                spokenCount(it.text) <= maxHookLength || !hasInternalCutPunctuation(it.text)
            }) {
                "开头钩子超过所选粒度上限且含可切分标点，必须按标点拆成多个镜头"
            }
            val rebuttalText = source.substring(plan.rebuttalStart, plan.rebuttalEnd)
            val rebuttalShots = shots.subList(startIndex, endIndex)
            val expectedRebuttalParts = splitRebuttal(rebuttalText)
            if (spokenCount(rebuttalText) <= 32) {
                require(rebuttalShots.size == 1) { "不超过32个有效字符的反驳段应保持为一镜" }
            } else if (expectedRebuttalParts.size > 1) {
                require(rebuttalShots.size > 1) { "较长反驳段含有自然停顿，应按标点拆分成多镜" }
            }
            require(rebuttalShots.all {
                spokenCount(it.text) <= 32 || expectedRebuttalParts.size == 1 ||
                    !hasInternalCutPunctuation(it.text)
            }) { "较长反驳镜头含可用标点却未拆分" }
            rebuttalStartShot = startIndex
            rebuttalEndShot = endIndex
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
        val rebuttalShots = rebuttalStartShot?.let { start ->
            shots.subList(start, rebuttalEndShot ?: start)
        }.orEmpty()
        for (values in listOf(rebuttalShots.map { it.visual }, rebuttalShots.map { it.imagePrompt })) {
            val keys = values.map { it.filterNot(Char::isWhitespace) }
            require(keys.distinct().size == keys.size) {
                "反驳短镜重复使用同一画面描述或生图提示词"
            }
        }
    }
}
