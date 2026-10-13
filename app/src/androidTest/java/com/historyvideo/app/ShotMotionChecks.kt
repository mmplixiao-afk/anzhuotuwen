package com.historyvideo.app

import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

internal object ShotMotionChecks {
    fun run() {
        for (seed in 0..39) {
            val push = ShotMotionRules.createPlan(ShotMotionSetting("zoom_in"), Random(seed))
            val pull = ShotMotionRules.createPlan(ShotMotionSetting("zoom_out"), Random(seed))
            check(push.start.zoom in 1.02..1.061 && push.end.zoom in 1.45..1.601)
            check(push.start.zoom < push.end.zoom && pull.start.zoom > pull.end.zoom)
            check(push.start.x == 0.0 && push.end.x == 0.0 && push.start.y == 0.0 && push.end.y == 0.0)
            check(pull.start.x == 0.0 && pull.end.x == 0.0 && pull.start.y == 0.0 && pull.end.y == 0.0)
            for ((direction, _) in ShotMotionRules.directions) {
                for (mode in listOf("pan", "zoom_pan")) {
                    for (zoomDirection in listOf("in", "out")) {
                        val plan = ShotMotionRules.createPlan(ShotMotionSetting(mode, direction, zoomDirection), Random(seed))
                        when (direction) {
                            "left_to_right" -> { check(plan.start.x < plan.end.x); check(plan.start.y == 0.0 && plan.end.y == 0.0) }
                            "right_to_left" -> { check(plan.start.x > plan.end.x); check(plan.start.y == 0.0 && plan.end.y == 0.0) }
                            "top_to_bottom" -> { check(plan.start.y > plan.end.y); check(plan.start.x == 0.0 && plan.end.x == 0.0) }
                            "bottom_to_top" -> { check(plan.start.y < plan.end.y); check(plan.start.x == 0.0 && plan.end.x == 0.0) }
                        }
                        if (mode == "pan") check(plan.start.zoom == plan.end.zoom)
                        else if (zoomDirection == "out") check(plan.start.zoom > plan.end.zoom)
                        else check(plan.start.zoom < plan.end.zoom)
                        // Every interpolated crop remains inside the image without relying on axis drift.
                        for (step in 0..30) {
                            val progress = step / 30.0
                            val zoom = plan.start.zoom + (plan.end.zoom - plan.start.zoom) * progress
                            val x = plan.start.x + (plan.end.x - plan.start.x) * progress
                            val y = plan.start.y + (plan.end.y - plan.start.y) * progress
                            val horizontalMargin = (1.0 - 1.0 / zoom) / 2.0
                            check(abs(x / zoom) <= horizontalMargin + 1e-9)
                            check(abs(y / zoom) <= horizontalMargin + 1e-9)
                        }
                    }
                }
            }
        }

        val original = Shot(1, "口播。", "画面", imageStatus = "done", audioStatus = "done", duration = 2.0)
        val other = Shot(2, "下一镜。", "另一个画面")
        val cutout = Shot(3, "人物。", "人物画面", cutoutEnabled = true)
        val project = VideoProject(UUID.randomUUID().toString(), "运动验收", shots = mutableListOf(original, other, cutout))
        val preset = ShotMotionSetting("pan", "left_to_right")
        val mediaState = Triple(original.text, original.imageStatus, original.audioStatus)
        ShotMotionRules.assign(project, preset, setOf(1))
        check(original.motionSetting == preset && original.motionPlan == null)
        val cached = ShotMotionRules.createPlan(preset, Random(10))
        original.motionPlan = cached; original.motionPlanSetting = preset
        ShotMotionRules.assign(project, preset, setOf(1))
        check(original.motionPlan === cached && original.motionPlanSetting == preset)
        val reverse = ShotMotionSetting("pan", "right_to_left")
        ShotMotionRules.assign(project, reverse, setOf(1))
        check(original.motionSetting == reverse && original.motionPlan == null && original.motionPlanSetting == null)
        check(ShotMotionRules.label(reverse).contains("右 → 左"))
        check(ShotMotionRules.label(ShotMotionSetting("zoom_pan", "top_to_bottom", "out")).contains("拉远"))
        ShotMotionRules.assign(project, preset, setOf(2))
        check(original.motionSetting == reverse && other.motionSetting == preset)
        ShotMotionRules.assign(project, preset, emptySet())
        check(other.motionSetting == null && original.motionSetting == reverse)
        val beforeInvalid = project.json().toString()
        check(runCatching { ShotMotionRules.assign(project, preset, setOf(3)) }.isFailure)
        check(project.json().toString() == beforeInvalid)
        check(runCatching { ShotMotionRules.assign(project, preset, setOf(999)) }.isFailure)
        check(project.json().toString() == beforeInvalid)
        check(Triple(original.text, original.imageStatus, original.audioStatus) == mediaState && original.duration == 2.0)

        original.motionPlan = ShotMotionRules.createPlan(reverse, Random(11)); original.motionPlanSetting = reverse
        val restored = Shot.from(original.json())
        check(restored.motionSetting == reverse && restored.motionPlanSetting == reverse && restored.motionPlan == original.motionPlan)
        val legacy = original.json().apply { remove("motion_setting"); remove("motion_plan_setting") }
        check(Shot.from(legacy).motionSetting == null && Shot.from(legacy).motionPlanSetting == null)
        check(ShotMotionSetting.from(JSONObject().put("mode", "unsupported")) == null)
        check(ShotMotionRules.normalize(ShotMotionSetting("pan", "bad-direction"))?.direction == "random")
        check(ShotMotionRules.normalize(ShotMotionSetting("zoom_out"))?.zoomDirection == "out")

        ShotMotionRules.selectCutouts(project, setOf(1, 3))
        check(original.cutoutEnabled && original.motionSetting == null && original.motionPlanSetting == null && original.motionPlan == null)
        check(cutout.cutoutEnabled && project.cutoutEnabled)
        check(Shot.from(original.json()).motionSetting == null)
        ShotMotionRules.selectCutouts(project, setOf(3))
        check(!original.cutoutEnabled && original.motionSetting == null && original.motionPlan == null)
        project.status = "running"
        check(runCatching { ShotMotionRules.assign(project, preset, setOf(2)) }.isFailure)
        check(runCatching { ShotMotionRules.selectCutouts(project, emptySet()) }.isFailure)
    }
}
