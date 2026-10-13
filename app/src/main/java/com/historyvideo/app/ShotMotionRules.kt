package com.historyvideo.app

import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

/** A manual movement override; absent settings use the project's default movement. */
data class ShotMotionSetting(
    val mode: String,
    val direction: String = "random",
    val zoomDirection: String = "in"
) {
    fun json() = JSONObject().apply {
        put("mode", mode); put("direction", direction); put("zoom_direction", zoomDirection)
    }

    companion object {
        fun from(value: JSONObject?): ShotMotionSetting? = value?.let {
            ShotMotionRules.normalize(ShotMotionSetting(it.optString("mode"),
                it.optString("direction", "random"), it.optString("zoom_direction", "in")))
        }
    }
}

internal object ShotMotionRules {
    val modes = listOf("zoom_in" to "推近", "zoom_out" to "拉远",
        "pan" to "平移", "zoom_pan" to "边推拉边平移")
    val directions = listOf("left_to_right" to "左 → 右", "right_to_left" to "右 → 左",
        "top_to_bottom" to "上 → 下", "bottom_to_top" to "下 → 上")

    fun normalize(setting: ShotMotionSetting?): ShotMotionSetting? {
        if (setting == null || modes.none { it.first == setting.mode }) return null
        val moving = setting.mode == "pan" || setting.mode == "zoom_pan"
        val direction = if (moving && directions.any { it.first == setting.direction }) setting.direction else "random"
        val zoom = if (setting.mode == "zoom_out" || (setting.mode == "zoom_pan" && setting.zoomDirection == "out")) "out" else "in"
        return ShotMotionSetting(setting.mode, direction, zoom)
    }

    fun label(setting: ShotMotionSetting?): String {
        val valid = normalize(setting) ?: return "默认运动"
        val modeLabel = modes.first { it.first == valid.mode }.second
        if (valid.mode != "pan" && valid.mode != "zoom_pan") return modeLabel
        val directionLabel = directions.firstOrNull { it.first == valid.direction }?.second ?: "随机方向"
        return if (valid.mode == "zoom_pan")
            "${if (valid.zoomDirection == "out") "拉远" else "推近"} + $directionLabel"
        else "$modeLabel · $directionLabel"
    }

    fun clear(shot: Shot) {
        shot.motionSetting = null
        shot.motionPlanSetting = null
        shot.motionPlan = null
    }

    /** Update only the open shot, preserving every other shot's movement and generated media. */
    fun setShot(project: VideoProject, shotId: Int, setting: ShotMotionSetting?) {
        require(project.status != "running") { "请等待当前任务完成后再设置图片运动" }
        val shot = project.shots.firstOrNull { it.id == shotId } ?: error("镜头不存在")
        require(!shot.cutoutEnabled) { "已选抠图的镜头不能设置普通图片运动" }
        val normalized = if (setting == null) null else requireNotNull(normalize(setting)) { "图片运动类型无效" }
        if (normalize(shot.motionSetting) != normalized) {
            clear(shot)
            shot.motionSetting = normalized
        }
    }

    /** Assign selected shots; deselected members of this exact preset return to the default. */
    fun assign(project: VideoProject, preset: ShotMotionSetting, selectedIds: Set<Int>) {
        require(project.status != "running") { "请等待当前任务完成后再设置图片运动" }
        val normalized = requireNotNull(normalize(preset)) { "图片运动类型无效" }
        val shotsById = project.shots.associateBy { it.id }
        require(selectedIds.all { shotsById[it]?.cutoutEnabled == false }) { "已选抠图的镜头不能设置普通图片运动" }
        project.shots.forEach { shot ->
            if (shot.id in selectedIds) {
                if (normalize(shot.motionSetting) != normalized) {
                    clear(shot)
                    shot.motionSetting = normalized
                }
            } else if (!shot.cutoutEnabled && normalize(shot.motionSetting) == normalized) {
                clear(shot)
            }
        }
    }

    fun selectCutouts(project: VideoProject, selectedIds: Set<Int>) {
        require(project.status != "running") { "请等待当前任务完成后再选择抠图" }
        require(selectedIds.all { id -> project.shots.any { it.id == id } }) { "镜头不存在" }
        project.shots.forEach { shot ->
            shot.cutoutEnabled = shot.id in selectedIds
            if (shot.cutoutEnabled) clear(shot)
        }
        project.cutoutEnabled = project.shots.any { it.cutoutEnabled }
    }

    /** Directions describe picture movement in the rendered zoompan canvas, not crop movement. */
    fun createPlan(setting: ShotMotionSetting, random: Random = Random.Default): MotionPlan {
        val normalized = requireNotNull(normalize(setting)) { "图片运动类型无效" }
        val low = random.nextDouble(1.02, 1.0600001)
        val high = random.nextDouble(1.45, 1.6000001)
        val startZoom = when {
            normalized.mode == "pan" || normalized.zoomDirection == "out" -> high
            else -> low
        }
        val endZoom = if (normalized.mode == "pan" || normalized.zoomDirection == "in") high else low
        var startX = 0.0; var startY = 0.0; var endX = 0.0; var endY = 0.0
        if (normalized.mode == "pan" || normalized.mode == "zoom_pan") {
            val direction = if (normalized.direction == "random") directions.random(random).first else normalized.direction
            val startOffset = min(0.09, (startZoom - 1.0) * 0.30)
            val endOffset = min(0.09, (endZoom - 1.0) * 0.30)
            when (direction) {
                "left_to_right" -> { startX = -startOffset; endX = endOffset }
                "right_to_left" -> { startX = startOffset; endX = -endOffset }
                // Crop Y and picture Y move in opposite directions.
                "top_to_bottom" -> { startY = startOffset; endY = -endOffset }
                "bottom_to_top" -> { startY = -startOffset; endY = endOffset }
            }
        }
        return MotionPlan(normalized.mode, MotionPoint(startZoom, startX, startY), MotionPoint(endZoom, endX, endY))
    }
}
