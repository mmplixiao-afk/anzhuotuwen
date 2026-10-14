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

    fun taskLabel(project: VideoProject?): String {
        if (project == null) return "空闲"
        if (project.stage == "topic") return project.detail.ifBlank { "待成文" }
        return project.detail.ifBlank { "空闲" }
    }

    fun resumeScreen(project: VideoProject): String = if (project.stage == "topic") "script" else "project"
}
