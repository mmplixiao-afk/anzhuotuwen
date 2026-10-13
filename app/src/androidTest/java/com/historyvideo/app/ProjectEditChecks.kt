package com.historyvideo.app

import android.content.Context
import java.util.UUID
import kotlin.math.abs

internal object ProjectEditChecks {
    fun run(context: Context) {
        check(OpeningTiming.DEFAULT_DELAY == 0.5f)
        check(OpeningTiming.start(0.5f, 3.0) == 0.5)
        check(OpeningTiming.start(0f, 3.0) == 0.0)
        check(OpeningTiming.start(1.25f, 3.0) == 1.25)
        check(abs(OpeningTiming.start(5f, 0.4) - 0.18) < 1e-9)
        check(OpeningTiming.start(5f, 0.0) == 0.0)
        check(OpeningTiming.normalizeDelay(Float.NaN) == 0.5f)
        check(OpeningTiming.normalizeDelay(-1f) == 0f)
        check(OpeningTiming.normalizeDelay(10f) == 5f)
        val model = VideoProject(UUID.randomUUID().toString(), "开场验收", coverTitleDelaySeconds = 1.25f)
        check(VideoProject.from(model.json()).coverTitleDelaySeconds == 1.25f)
        val legacy = model.json().apply { remove("cover_title_delay_seconds") }
        check(VideoProject.from(legacy).coverTitleDelaySeconds == 0.5f)
        val invalid = model.json().put("cover_title_delay_seconds", -2)
        check(VideoProject.from(invalid).coverTitleDelaySeconds == 0f)

        val store = ProjectStore(context)
        val project = store.create("插镜验收")
        try {
            project.shots = (1..3).map { id ->
                Shot(id, "原镜头$id。", "原图片$id", duration = 2.0,
                    imageStatus = "done", audioStatus = "done",
                    imageRevision = if (id == 1) "" else UUID.randomUUID().toString(),
                    audioRevision = if (id == 1) "" else UUID.randomUUID().toString(),
                    cutoutEnabled = id == 2, subtitleChunks = mutableListOf("原镜头$id。"))
            }.toMutableList()
            project.script = project.shots.joinToString("") { it.text }
            project.storyboardScript = project.script
            project.status = "done"; project.stage = "render"; project.duration = 6.0
            project.coverShotId = 2
            val savedMovement = ShotMotionSetting("pan", "left_to_right")
            project.shots[0].motionSetting = savedMovement
            project.shots[0].motionPlan = ShotMotionRules.createPlan(savedMovement)
            project.shots[0].motionPlanSetting = savedMovement
            project.coverTitleDelaySeconds = 1.25f
            project.renderRevision = UUID.randomUUID().toString()
            project.transitionPlans = mutableListOf(TransitionPlan("none", 0), TransitionPlan("fade", 8))
            project.transitionPlanSignature = "old"
            store.save(project)
            val originalShots = project.shots.toList()
            val originalData = originalShots.map { it.json().toString() }
            val originalFiles = originalShots.flatMap { listOf(store.image(project, it), store.audio(project, it)) }
            originalFiles.forEachIndexed { index, file -> file.writeBytes(ByteArray(2048) { (index + it % 127).toByte() }) }
            val originalBytes = originalFiles.map { it.readBytes() }
            val oldOutput = store.output(project).apply { writeBytes(ByteArray(2048) { 3 }) }
            val ledger = ProjectCosts(store)
            ledger.add(project.id, CostEntry.image("原生成", "0.017", shotId = 2))
            val originalCosts = ledger.read(project.id)
            val inserted = store.insertShotAfter(project, 1, "新增口播。", "新增独立提示词")
            check(project.shots.map { it.id } == listOf(1, 4, 2, 3))
            check(project.shotNumber(inserted.id) == 2 && project.shotNumber(2) == 3)
            check(inserted.imageStatus == "pending" && inserted.audioStatus == "pending")
            check(!store.image(project, inserted).exists() && !store.audio(project, inserted).exists())
            originalShots.forEachIndexed { index, shot ->
                check(project.shots.first { it.id == shot.id } === shot)
                check(shot.json().toString() == originalData[index])
                check(store.image(project, shot) == originalFiles[index * 2])
                check(store.audio(project, shot) == originalFiles[index * 2 + 1])
            }
            originalFiles.forEachIndexed { index, file -> check(file.readBytes().contentEquals(originalBytes[index])) }
            check(oldOutput.isFile && store.output(project) != oldOutput)
            check(project.script == "原镜头1。新增口播。原镜头2。原镜头3。")
            check(project.storyboardScript == project.script && project.status == "review")
            check(project.coverShotId == 2 && project.coverTitleDelaySeconds == 1.25f)
            check(project.transitionPlans.isEmpty() && project.transitionPlanSignature.isBlank())
            check(ledger.read(project.id) == originalCosts)
            store.save(project)
            val restored = requireNotNull(store.get(project.id))
            check(restored.shots.map { it.id } == listOf(1, 4, 2, 3))
            check(restored.coverTitleDelaySeconds == 1.25f && restored.script == project.script)
            check(restored.shots.first { it.id == 1 }.motionSetting == savedMovement)
            check(restored.shots.first { it.id == 1 }.motionPlanSetting == savedMovement)
            originalShots.forEach { old ->
                val restoredShot = restored.shots.first { it.id == old.id }
                check(store.image(restored, restoredShot) == store.image(project, old))
                check(store.audio(restored, restoredShot) == store.audio(project, old))
            }
            val beforeInvalid = project.json().toString()
            check(runCatching { store.insertShotAfter(project, 1, "", "提示词") }.isFailure)
            check(runCatching { store.insertShotAfter(project, 999, "正文", "提示词") }.isFailure)
            check(project.json().toString() == beforeInvalid)
            val again = store.insertShotAfter(project, inserted.id, "又一个镜头。", "另一画面")
            check(again.id == 5 && project.shots.map { it.id } == listOf(1, 4, 5, 2, 3))
            project.status = "running"
            check(runCatching { store.insertShotAfter(project, 1, "正文", "提示词") }.isFailure)
        } finally {
            store.delete(project.id)
        }
    }
}
