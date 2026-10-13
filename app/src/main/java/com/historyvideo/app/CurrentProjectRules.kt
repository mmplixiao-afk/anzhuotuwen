package com.historyvideo.app

/** The current workspace is independent of the history list's title requirement. */
internal object CurrentProjectRules {
    fun inProgress(project: VideoProject): Boolean = project.status == "running" || project.status == "queued"

    fun running(projects: List<VideoProject>): VideoProject? = projects.filter(::inProgress)
        .maxByOrNull { it.taskStartedAt.coerceAtLeast(it.createdAt) }

    fun current(projects: List<VideoProject>, preferredId: String): VideoProject? {
        running(projects)?.let { return it }
        projects.firstOrNull { it.id == preferredId }?.let { return it }
        return projects.filter { it.title.isNotBlank() || it.taskStartedAt > 0L ||
            it.topicIdeas.isNotEmpty() || it.topicCandidates.isNotEmpty() || it.script.isNotBlank() || it.shots.isNotEmpty() }
            .maxByOrNull { it.taskStartedAt.coerceAtLeast(it.createdAt) }
    }

    fun markTopicQueued(project: VideoProject, now: Long) {
        project.stage = "topic"; project.status = "queued"; project.detail = "AI选题"
        project.taskStartedAt = now
        project.completed = 0; project.total = 0
    }

    fun taskLabel(project: VideoProject?): String {
        if (project == null) return "空闲"
        if (project.stage != "topic") return project.detail.ifBlank { "空闲" }
        return when (project.status) {
            "queued", "running" -> "AI选题"
            "review", "done" -> "AI选题完成，请选择题目"
            "failed" -> project.detail.ifBlank { "AI选题失败，请重试" }
            "paused" -> "AI选题已暂停，可继续制作"
            else -> project.detail.ifBlank { "待立题" }
        }
    }

    fun resumeScreen(project: VideoProject): String = if (project.stage == "topic") "topics" else "project"
}
