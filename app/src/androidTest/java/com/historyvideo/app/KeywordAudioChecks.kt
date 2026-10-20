package com.historyvideo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object KeywordAudioChecks {
    fun run(context: Context) {
        val original = Shot(4, "本镜口播。", "本镜画面提示词", soundEffects = mutableListOf(
            ShotSoundEffect("user:impact", 0.75f), ShotSoundEffect("gong", 1.25f)
        ), punch = "重点", videoPrompt = "轻微推近")
        val restored = Shot.from(JSONObject(original.json().toString()))
        check(restored.soundEffects == original.soundEffects)
        check(restored.punch == "重点" && restored.videoPrompt == "轻微推近")

        val legacyProject = VideoProject("00000000-0000-0000-0000-000000000001", "旧音频迁移")
        legacyProject.introSfxId = "gong"
        legacyProject.introSfxVolume = 0.65f
        legacyProject.shots = mutableListOf(Shot(1, "旧开头。", "旧画面"))
        val migrated = VideoProject.from(JSONObject(legacyProject.json().toString()))
        check(migrated.introSfxId == "none")
        check(migrated.shots.first().soundEffects == listOf(ShotSoundEffect("gong", 0.65f)))

        val project = ProjectStore(context).create("音轨持久化检查")
        try {
            project.bgmId = "user:bgm"
            project.bgmStartShotId = 4
            project.storyboardGranularity = "fine"
            project.shots = mutableListOf(original)
            val loaded = VideoProject.from(JSONObject(project.json().toString()))
            check(loaded.bgmId == "user:bgm" && loaded.bgmStartShotId == 4)
            check(loaded.storyboardGranularity == "fine")

            val next = Shot(4, "本镜口播。", "更新后的画面提示词")
            ProjectStore(context).applyStoryboard(project, listOf(next))
            check(project.shots.single().soundEffects == original.soundEffects)
        } finally {
            ProjectStore(context).delete(project.id)
        }
    }
}
