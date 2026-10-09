package com.historyvideo.app

internal object StoryboardRules {
    private val sentenceEnd = Regex("[。！？!?]")
    private val hookStart = Regex("^(?:他娘的|哼)(?:[，,。！!])?")
    private val rebuttalStart = Regex("放屁(?=[。！？!?，,；;\\s]|$)")
    private val punch = Regex("^放屁[。！!，,；;]?")
    private val clauses = Regex("[^，,；;：:。！？!?]+(?:[，,；;：:。！？!?]+|$)")
    private val sceneStarts = listOf("不仅", "在外", "在内", "而在", "到了")

    private fun spokenCount(text: String) = text.count { it.isLetterOrDigit() || it == '_' }

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

    fun instruction(source: String): String = buildString {
        append("\n\n【拆镜输出约束，优先于前文旧的开头和粒度规则】\n")
        append("只拆分本次完整口播文案，不生成或改写正文。每镜 voiceover 必须是连续原文片段；按序拼接必须逐字等于原文，保留所有标点、空格和换行。\n")
        append("第1镜是第一个以。！？!?结束的主题句。主题后以他娘的或哼开头且后面有放屁时，第2镜为放屁之前的完整钩子。其他开头沿用原有拆镜规则。\n")
        append("反驳从放屁开始，止于其后第一个。！？!?；不超过32个口播字符时保持一镜。长反驳优先在逗号、分号、冒号、句末切分，合并相邻短分句，通常每镜不超过32字。\n")
        append("不仅、在外、在内、而在、到了开头的分句优先另起一镜；无停顿的超长分句按约28字拆分；不足8字的末尾尽量并入上一镜。放屁不得独占一镜，必须接上第一拍反驳。\n")
        append("后续正文按自然停顿和场景变化拆镜。每镜分别生成贴合本镜口播的 visual 和 image_prompt，不得让反驳多镜共用同一画面描述。保留设置提示词中的人物与画面风格要求。\n")
        val expected = opening(source)
        if (expected.isNotEmpty()) {
            append("以下开头边界由原文按上述规则确定，必须逐条使用；引号内是JSON编码的原文，解码后作为voiceover，不能再拆碎或合并：\n")
            expected.forEachIndexed { index, text ->
                append("镜头${index + 1}：${org.json.JSONObject.quote(text)}\n")
            }
        }
        append("只输出严格JSON对象，包含shots数组。每镜字段为id（从1连续递增）、voiceover、visual、image_prompt、subtitle_chunks（原文字幕分段数组，拼接等于本镜voiceover）。不得返回旧素材状态或省略独立生图提示词。")
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
