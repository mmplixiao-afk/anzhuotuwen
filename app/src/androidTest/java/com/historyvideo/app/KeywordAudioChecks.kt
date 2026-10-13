package com.historyvideo.app

internal object KeywordAudioChecks {
    fun run() {
        val shots = listOf(
            "古代生活到底有多苦？",
            "他娘的，你以为日子很轻松？",
            "放屁！她每天劳作，后来又放屁也不能休息。"
        )
        val triggers = KeywordAudioRules.triggers(shots)
        check(triggers.map { it.keyword } == listOf("他娘的", "放屁"))
        check(triggers[0].shotIndex == 1 && triggers[0].charIndex == 0)
        check(triggers[1].shotIndex == 2 && triggers[1].charIndex == 0)

        val thirdSentence = KeywordAudioRules.triggers(listOf("主题问题？普通正文结束。哼，第三句。"))
        check(thirdSentence.none { it.keyword == "哼" })
        val splitTheme = KeywordAudioRules.triggers(listOf("古代生活到底", "有多苦？哼你觉得呢？"))
        check(splitTheme.single { it.keyword == "哼" }.let { it.shotIndex == 1 && it.charIndex == 4 })

        val events = listOf(
            KeywordAudioRules.Event("放屁", offset = 2.467, duration = 0.862),
            KeywordAudioRules.Event("他娘的", offset = 0.967, duration = 4.336)
        )
        check(KeywordAudioRules.bgmStart(events) == 2.467)
        check(KeywordAudioRules.bgmStart(emptyList()) == 0.0)
        check(KeywordAudioRules.semanticCount("文案A12，🙂") == 5)
    }
}