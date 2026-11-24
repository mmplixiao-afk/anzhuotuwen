package com.historyvideo.app

internal object ImagePromptSetting {
    const val type = "image"
    const val title = "生图提示词"

    /** Settings keeps the shared image prompt. Blank stored text stays blank; a missing value uses the default. */
    fun read(stored: String?, defaultValue: String): String = stored ?: defaultValue

    fun write(stored: MutableMap<String, String>, value: String) {
        stored[type] = value
    }
}

internal fun characterReferenceFileName(fileKey: String): String {
    val key = fileKey.trim().ifBlank { "01" }
    require(key.matches(Regex("[0-9]{2}"))) { "参考图编号无效" }
    return "character_ref_$key.jpg"
}

internal fun genericCharacterName(name: String): Boolean {
    val text = name.trim()
    return text.isEmpty() || text in GENERIC_CHARACTER_NAMES
}

internal fun looksLikeStructuredPrompt(value: String): Boolean {
    val text = value.trim()
    if (text.isEmpty()) return false
    if (text.startsWith("{") || text.startsWith("[")) return true
    val compact = text.replace(" ", "")
    if (compact.contains("\"name\"") && (compact.contains("\"appearance\"") || compact.contains("\"role\""))) return true
    if (compact.contains("\"image_prompt\"") || compact.contains("\"characters\"")) return true
    return false
}

internal fun editableReferencePrompt(saved: String, computed: String): String {
    val text = saved.trim()
    if (text.isEmpty() || looksLikeStructuredPrompt(text)) return computed.trim()
    return text
}

internal fun parseSexLabel(value: String): ProtagonistSex = when (value.trim().lowercase()) {
    "female", "女", "女性", "f" -> ProtagonistSex.FEMALE
    "male", "男", "男性", "m" -> ProtagonistSex.MALE
    else -> ProtagonistSex.UNSPECIFIED
}

internal fun sexStorageLabel(sex: ProtagonistSex): String = when (sex) {
    ProtagonistSex.FEMALE -> "female"
    ProtagonistSex.MALE -> "male"
    ProtagonistSex.UNSPECIFIED -> ""
}

internal fun characterSexCaption(sex: String): String = when (parseSexLabel(sex)) {
    ProtagonistSex.FEMALE -> "女性"
    ProtagonistSex.MALE -> "男性"
    ProtagonistSex.UNSPECIFIED -> "性别未说明"
}

private val referenceOrdinals = listOf("第一张", "第二张", "第三张", "第四张", "第五张", "第六张", "第七张", "第八张", "第九张")

internal fun referenceIdentityLine(names: List<String>): String {
    val clean = names.map { it.trim() }.filter { it.isNotBlank() }.take(9)
    if (clean.isEmpty()) return ""
    if (clean.size == 1) return "这张参考图是${clean[0]}，画面里的这个人就是${clean[0]}。"
    return clean.mapIndexed { index, name ->
        val ordinal = referenceOrdinals.getOrElse(index) { "第${index + 1}张" }
        "${ordinal}参考图是$name"
    }.joinToString("，") + "。"
}

/** Plain-text character sheet. The shared JSON/template image prompt stays in Settings. */
internal fun buildNamedCharacterPrompt(name: String, appearance: String): String {
    val who = name.trim().ifBlank { "这个人物" }
    val sex = protagonistSex(appearance)
    val described = appearanceForSheet(appearance, sex).ifBlank {
        "只使用文中已经写明的年龄、脸型、发式和服装"
    }
    val gender = when (sex) {
        ProtagonistSex.FEMALE -> "${who}是女性，按女性的脸、发式、体态和服装来画。"
        ProtagonistSex.MALE -> "${who}是男性，按男性的脸、发式、体态和服装来画。"
        ProtagonistSex.UNSPECIFIED -> "这段描述没有写明${who}的性别。只画已经写明的特征，不要默认画成男性，不要自行添加胡须。"
    }
    return "为${who}生成一张人物参考图。$gender 光线清楚，正面或三分之四侧面，中景或全身，服装统一。外观：$described。" +
        "写实中国古代影视剧照，不要Q版、漫画或图集拼贴。画面不要文字、字幕、Logo或水印。背景简洁，不要复杂剧情场景。"
}

internal data class PreparedShotReferences(
    val references: List<CharacterReference>,
    val identityLine: String
)

internal fun findCharacterReference(character: StoryCharacter, references: List<CharacterReference>): CharacterReference? {
    references.firstOrNull { it.name == character.name }?.let { return it }
    if (character.role.equals("protagonist", true) || character.name == "主角") {
        references.firstOrNull { it.name == "主角" }?.let { return it }
        references.firstOrNull { it.role.equals("protagonist", true) }?.let { return it }
    }
    return null
}

internal fun charactersForShot(shot: Shot, cast: List<StoryCharacter>): List<StoryCharacter> {
    val blob = shotCharacterBlob(shot)
    val mentioned = cast.filter { !genericCharacterName(it.name) && it.name.isNotBlank() && blob.contains(it.name) }
        .sortedBy { blob.indexOf(it.name) }
    if (mentioned.isNotEmpty()) {
        if (shot.usesProtagonistReference) {
            val protagonist = cast.firstOrNull { it.role.equals("protagonist", true) && !genericCharacterName(it.name) }
            if (protagonist != null && mentioned.none { it.name == protagonist.name }) {
                return (listOf(protagonist) + mentioned).distinctBy { it.name }.take(9)
            }
        }
        return mentioned.distinctBy { it.name }.take(9)
    }
    if (shot.usesProtagonistReference) {
        val protagonist = cast.firstOrNull { it.role.equals("protagonist", true) || it.name == "主角" }
        if (protagonist != null) return listOf(protagonist)
    }
    return emptyList()
}

internal fun prepareShotReferences(
    shot: Shot,
    cast: List<StoryCharacter>,
    references: List<CharacterReference>,
    usable: (CharacterReference) -> Boolean
): PreparedShotReferences {
    val attached = charactersForShot(shot, cast).mapNotNull { character ->
        val reference = findCharacterReference(character, references) ?: return@mapNotNull null
        if (usable(reference)) reference else null
    }.distinctBy { it.fileKey.ifBlank { it.name } }.take(9)
    return PreparedShotReferences(attached, referenceIdentityLine(attached.map { displayReferenceName(it) }))
}

internal fun displayReferenceName(reference: CharacterReference): String =
    reference.name.trim().ifBlank { "主角" }

internal fun identifyStoryCharacters(
    script: String,
    shots: List<Shot>,
    parsed: List<StoryCharacter> = emptyList()
): List<StoryCharacter> {
    val corpus = buildString {
        append(script)
        shots.forEach { shot ->
            append('\n')
            append(shotCharacterBlob(shot))
        }
    }
    val hits = collectNameHits(corpus)
    val parsedNames = parsed.map { it.name.trim() }.filter { it.isNotBlank() && !genericCharacterName(it) }
    val discovered = linkedSetOf<String>()
    discovered += parsedNames
    hits.forEach { (name, hit) ->
        if (acceptScannedName(name, hit)) discovered += name
    }
    val ordered = discovered.sortedBy { name ->
        val at = corpus.indexOf(name)
        if (at >= 0) at else Int.MAX_VALUE - parsedNames.indexOf(name).coerceAtLeast(0)
    }
    val flagged = shots.filter { it.usesProtagonistReference }.joinToString("\n") { shotCharacterBlob(it) }
    val explicitProtagonist = parsed.firstOrNull {
        it.role.equals("protagonist", true) && !genericCharacterName(it.name)
    }?.name
    val protagonistName = explicitProtagonist
        ?: ordered.filter { flagged.contains(it) }.maxByOrNull { countOf(flagged, it) }
        ?: ordered.maxByOrNull { countOf(corpus, it) }
    val protagonistAppearance = parsed.firstOrNull { it.name == "主角" && it.appearance.isNotBlank() }?.appearance.orEmpty()
        .ifBlank { protagonistAppearance(parsed) }
    val cast = ordered.take(12).map { name ->
        val fromParsed = parsed.firstOrNull { it.name == name }
        val parsedAppearance = fromParsed?.appearance?.trim().orEmpty()
        val appearance = when {
            parsedAppearance.isNotBlank() && !looksLikeStructuredPrompt(parsedAppearance) -> parsedAppearance
            name == protagonistName && protagonistAppearance.isNotBlank() && fromParsed == null -> protagonistAppearance
            else -> appearanceClause(name, corpus, discovered)
        }
        val sex = resolveCharacterSex(fromParsed?.sex.orEmpty(), appearance, appearanceClause(name, corpus, discovered))
        val role = when {
            fromParsed?.role?.equals("protagonist", true) == true -> "protagonist"
            name == protagonistName -> "protagonist"
            fromParsed?.role?.isNotBlank() == true -> fromParsed.role
            else -> "supporting"
        }
        StoryCharacter(name, appearance, role, sexStorageLabel(sex))
    }.toMutableList()
    if (cast.isEmpty() && (parsed.any { it.name == "主角" && it.appearance.isNotBlank() } || shots.any { it.usesProtagonistReference })) {
        val appearance = protagonistAppearance.ifBlank {
            shots.filter { it.usesProtagonistReference }.map { it.imagePrompt.trim() }.filter { it.isNotBlank() }.take(2)
                .joinToString("\n")
        }
        val sex = resolveCharacterSex(parsed.firstOrNull { it.name == "主角" }?.sex.orEmpty(), appearance, appearance)
        cast += StoryCharacter("主角", appearance, "protagonist", sexStorageLabel(sex))
    }
    return cast
}

internal fun syncCharacterReferences(
    cast: List<StoryCharacter>,
    existing: List<CharacterReference>
): List<CharacterReference> {
    val pool = existing.map { it.copy() }.toMutableList()
    val legacy = pool.filter { it.name == "主角" || genericCharacterName(it.name) }
    val protagonist = cast.firstOrNull { it.role.equals("protagonist", true) && !genericCharacterName(it.name) }
        ?: cast.singleOrNull()?.takeIf { !genericCharacterName(it.name) }
    if (legacy.size == 1 && protagonist != null && pool.none { it.name == protagonist.name }) {
        val donor = legacy[0]
        donor.name = protagonist.name
        if (donor.role.isBlank()) donor.role = protagonist.role
        if (donor.sex.isBlank()) donor.sex = protagonist.sex
        if (donor.appearance.isBlank()) donor.appearance = protagonist.appearance
        if (donor.fileKey.isBlank()) donor.fileKey = "01"
    }
    val used = pool.mapNotNull { it.fileKey.takeIf { key -> key.matches(Regex("[0-9]{2}")) } }.toMutableSet()
    pool.filter { it.fileKey.isBlank() }.forEach { reference ->
        val key = if ("01" !in used) "01" else allocateReferenceFileKey(used)
        reference.fileKey = key
        used += key
    }
    return cast.map { character ->
        val current = pool.firstOrNull { it.name == character.name } ?: CharacterReference(name = character.name).also {
            it.fileKey = allocateReferenceFileKey(used)
            used += it.fileKey
            pool += it
        }
        current.name = character.name
        current.role = character.role
        current.sex = character.sex
        val appearance = character.appearance.ifBlank { current.appearance }
        if (!current.promptCustom || current.appearance.isBlank()) current.appearance = appearance
        val computed = buildNamedCharacterPrompt(current.name, current.appearance.ifBlank { appearance })
        val keepSaved = current.promptCustom && current.prompt.isNotBlank() && !looksLikeStructuredPrompt(current.prompt)
        if (!keepSaved) {
            current.prompt = computed
            current.promptCustom = false
        }
        if (current.status.isBlank()) current.status = "pending"
        if (current.fileKey.isBlank()) {
            current.fileKey = allocateReferenceFileKey(used)
            used += current.fileKey
        }
        current
    }
}

internal fun applyIdentifiedCast(project: VideoProject): Boolean {
    val identified = identifyStoryCharacters(project.script, project.shots, project.characters)
    val synced = syncCharacterReferences(identified, project.characterReferences)
    if (sameCharacters(project.characters, identified) && sameReferences(project.characterReferences, synced)) return false
    project.characters = identified.toMutableList()
    project.characterReferences = synced.toMutableList()
    return true
}

internal fun referenceReadyFor(
    bytes: Long,
    remoteUrl: String,
    requiresPublicUrl: Boolean
): Boolean = referenceAssetUsable(bytes, remoteUrl, requiresPublicUrl)

private fun sameCharacters(left: List<StoryCharacter>, right: List<StoryCharacter>): Boolean {
    if (left.size != right.size) return false
    return left.zip(right).all { (a, b) ->
        a.name == b.name && a.appearance == b.appearance && a.role == b.role && a.sex == b.sex
    }
}

private fun sameReferences(left: List<CharacterReference>, right: List<CharacterReference>): Boolean {
    if (left.size != right.size) return false
    return left.zip(right).all { (a, b) ->
        a.name == b.name && a.remoteUrl == b.remoteUrl && a.status == b.status &&
            a.appearance == b.appearance && a.prompt == b.prompt && a.role == b.role &&
            a.sex == b.sex && a.fileKey == b.fileKey && a.promptCustom == b.promptCustom
    }
}

private fun allocateReferenceFileKey(used: Set<String>): String {
    var number = 1
    while (number <= 99) {
        val key = "%02d".format(number)
        if (key !in used) return key
        number++
    }
    error("人物参考图数量超出范围")
}

private fun shotCharacterBlob(shot: Shot): String =
    listOf(shot.text, shot.visual, shot.imagePrompt).filter { it.isNotBlank() }.joinToString("\n")

private fun resolveCharacterSex(label: String, appearance: String, context: String): ProtagonistSex {
    val fromAppearance = protagonistSex(appearance)
    if (appearance.isNotBlank() && fromAppearance != ProtagonistSex.UNSPECIFIED) return fromAppearance
    val fromLabel = parseSexLabel(label)
    if (fromLabel != ProtagonistSex.UNSPECIFIED) return fromLabel
    val fromContext = protagonistSex(context)
    return fromContext
}

private fun appearanceClause(name: String, corpus: String, names: Set<String>): String {
    val hints = listOf("岁", "脸", "眉", "眼", "发", "袍", "衫", "裙", "甲", "须", "冠", "髻", "女", "男", "胡", "衣")
    var best = ""
    var bestScore = 0
    var index = corpus.indexOf(name)
    while (index >= 0) {
        val start = corpus.lastIndexOfAny(charArrayOf('。', '！', '？', '\n', '；'), index)
            .let { if (it < 0) (index - 8).coerceAtLeast(0) else it + 1 }
        val endPunct = corpus.indexOfAny(charArrayOf('。', '！', '？', '\n', '；'), index + name.length)
        val end = if (endPunct < 0) minOf(corpus.length, index + name.length + 48) else endPunct
        var clause = corpus.substring(start, end).trim()
        names.filter { it != name && it.isNotBlank() }.forEach { other ->
            val at = clause.indexOf(other)
            if (at >= 0) clause = clause.substring(0, at).trim('，', ',', '、', ' ', '。')
        }
        val score = hints.count { clause.contains(it) }
        if (score > bestScore) {
            best = clause
            bestScore = score
        }
        index = corpus.indexOf(name, index + name.length)
    }
    if (bestScore == 0) return ""
    return best.take(80)
}

private data class NameHit(var count: Int = 0, var anchored: Boolean = false, var cued: Boolean = false)

private fun collectNameHits(text: String): Map<String, NameHit> {
    val hits = linkedMapOf<String, NameHit>()
    var index = 0
    while (index < text.length) {
        val name = longestPersonalName(text, index)
        if (name == null) {
            index++
            continue
        }
        val hit = hits.getOrPut(name) { NameHit() }
        hit.count++
        val cued = identityCue(text, index, name)
        if (cued) hit.cued = true
        if (cued || boundaryBefore(text, index)) hit.anchored = true
        index += name.length
    }
    return hits
}

private fun acceptScannedName(name: String, hit: NameHit): Boolean {
    if (name in BLOCKED_NAMES || genericCharacterName(name)) return false
    if (!hit.anchored && !hit.cued) return false
    if (hit.cued) return true
    if (name.length >= 3 && hit.anchored) return true
    return hit.count >= 2 && hit.anchored
}

private fun identityCue(text: String, index: Int, name: String): Boolean {
    val before = text.substring(maxOf(0, index - 4), index)
    if (before.endsWith("名叫") || before.endsWith("叫做") || before.endsWith("名为") || before.endsWith("唤作")) return true
    val after = text.substring(index + name.length, minOf(text.length, index + name.length + 20))
    if (protagonistSex(after) != ProtagonistSex.UNSPECIFIED) return true
    return after.startsWith("说道") || after.startsWith("笑道") || after.startsWith("冷声") || after.startsWith("喝道")
}

private fun boundaryBefore(text: String, index: Int): Boolean {
    if (index <= 0) return true
    val previous = text[index - 1]
    if (!previous.isHan()) return true
    return previous in NAME_SEPARATORS
}

private fun longestPersonalName(text: String, index: Int): String? {
    if (index >= text.length || !text[index].isHan()) return null
    val rest = text.substring(index)
    val compound = COMPOUND_SURNAMES.firstOrNull { rest.startsWith(it) }
    if (compound != null) personalName(rest, compound.length)?.let { return it }
    if (rest[0] in SINGLE_SURNAMES) return personalName(rest, 1)
    return null
}

private fun personalName(rest: String, surnameLength: Int): String? {
    val longer = nameCandidate(rest, surnameLength, 2)
    if (longer != null) return longer
    val shorter = nameCandidate(rest, surnameLength, 1) ?: return null
    val next = surnameLength + 1
    if (next < rest.length && rest[next] in APPEARANCE_CHARS) return null
    return shorter
}

private fun nameCandidate(rest: String, surnameLength: Int, given: Int): String? {
    val end = surnameLength + given
    if (rest.length < end) return null
    val givenChars = rest.substring(surnameLength, end)
    if (givenChars.any { !it.isHan() || it in REJECTED_GIVEN }) return null
    val name = rest.substring(0, end)
    if (name in BLOCKED_NAMES || name.startsWith("空镜")) return null
    return name
}

private fun countOf(text: String, name: String): Int {
    if (name.isEmpty()) return 0
    var count = 0
    var index = text.indexOf(name)
    while (index >= 0) {
        count++
        index = text.indexOf(name, index + name.length)
    }
    return count
}

private fun Char.isHan(): Boolean = this in '\u4e00'..'\u9fff'

private val GENERIC_CHARACTER_NAMES = setOf(
    "主角", "配角", "路人", "人物", "众人", "群众", "百姓", "士兵", "侍卫", "别人", "某人", "那人", "此人"
)

private val NAME_SEPARATORS = setOf(
    '的', '是', '与', '和', '跟', '同', '对', '把', '被', '让', '向', '到', '在', '从', '为', '给',
    '将', '叫', '作', '名', '曰', '而', '但', '便', '就', '又', '也', '都', '还', '说', '问', '看', '有'
)

private val REJECTED_GIVEN = setOf(
    '的', '了', '是', '在', '和', '与', '或', '被', '把', '将', '从', '对', '向', '到', '让', '给',
    '又', '也', '就', '都', '还', '很', '最', '不', '没', '无', '这', '那', '你', '我', '他', '她',
    '它', '着', '过', '吧', '呢', '啊', '呀', '吗', '么', '而', '及', '但', '却', '于', '其', '之',
    '上', '下', '中', '内', '外', '前', '后', '里', '来', '去', '个', '位', '说', '道', '们', '等',
    '为', '以', '则', '并', '且', '已', '经', '正', '太', '更', '走', '进', '出', '站', '坐', '拿',
    '持', '拔', '回', '喝', '笑', '问', '答', '听', '想', '跑', '跳', '推', '拉', '打', '杀', '看',
    '开', '拎', '砍', '刺', '挥', '指', '怒', '叹', '点', '摇', '低', '抬', '转', '身', '冷', '哼',
    '眉', '须', '袍', '裙', '脸', '甲', '髻', '眼', '鼻', '嘴', '发', '衣', '冠', '胡', '衫', '袖'
)

private val APPEARANCE_CHARS = setOf('眉', '须', '袍', '裙', '脸', '甲', '髻', '眼', '鼻', '嘴', '发', '衣', '冠', '胡', '衫', '袖')

private val BLOCKED_NAMES = setOf(
    "长安", "后来", "马上", "于是", "因此", "以及", "其中", "其实", "甚至", "原来", "当时", "今天",
    "这里", "那里", "这样", "那样", "怎么", "什么", "自己", "我们", "他们", "她们", "人们", "时候",
    "地方", "东西", "事情", "知道", "应该", "需要", "开始", "最后", "其他", "大家", "一起", "一直",
    "一定", "一般", "一样", "不会", "不能", "不要", "不用", "不过", "关中", "关内", "关外", "江南",
    "江北", "山东", "山西", "河南", "河北", "北京", "南京", "西安", "洛阳", "东方", "西方", "南方",
    "北方", "天下", "天子", "王后", "王子", "公主", "皇帝", "皇上", "朝廷", "国家", "将军", "丞相",
    "大人", "先生", "夫人", "小姐", "公子", "书生", "和尚", "太监", "宫女", "丫鬟", "掌柜", "安城"
)

private val COMPOUND_SURNAMES = listOf(
    "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫", "万俟", "闻人", "夏侯", "诸葛",
    "尉迟", "公羊", "赫连", "澹台", "皇甫", "宗政", "濮阳", "公冶", "太叔", "申屠", "公孙", "慕容",
    "仲孙", "钟离", "长孙", "宇文", "司徒", "鲜于", "司空", "闾丘", "子车", "司寇", "巫马", "公西",
    "颛孙", "壤驷", "公良", "漆雕", "乐正", "宰父", "谷梁", "拓跋", "夹谷", "轩辕", "令狐", "段干",
    "百里", "呼延", "东郭", "南门", "羊舌", "微生", "梁丘", "左丘", "西门", "第五", "南荣", "东宫",
    "仲长", "子书", "子桑", "即墨", "达奚", "褚师"
).sortedByDescending { it.length }

private val SINGLE_SURNAMES: Set<Char> =
    "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万柯管卢莫经房裘缪干解应宗丁宣邓郁单杭洪包诸左石崔吉龚程嵇邢裴陆荣翁荀羊惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍郤璩桑桂濮牛寿通边扈燕冀浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩聂晁勾敖融冷訾辛阚那简饶空曾沙养鞠须丰巢关蒯相查后荆红游竺权逯盖益".toSet()

/** Keeps the JSON shape used by older tests from being mistaken for a character prompt. */
internal fun characterReferencePromptIsPlainText(prompt: String): Boolean {
    val text = prompt.trim()
    return text.isNotEmpty() && !looksLikeStructuredPrompt(text) && !text.contains("{") && !text.contains("}")
}
