package com.historyvideo.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class TextUsage(val inputTokens: Long?, val outputTokens: Long?, val requestId: String = "", val content: String = "") {
    companion object {
        fun from(response: JSONObject): TextUsage {
            val content = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.opt("content") as? String ?: ""
            val usage = response.optJSONObject("usage") ?: return TextUsage(null, null, content = content)
            fun count(primary: String, fallback: String): Long? {
                val value = if (usage.has(primary)) usage.opt(primary) else usage.opt(fallback)
                return (value as? Number)?.toLong()?.takeIf { it >= 0 }
            }
            return TextUsage(count("prompt_tokens", "input_tokens"), count("completion_tokens", "output_tokens"), content = content)
        }
    }
}

internal data class CostEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val label: String,
    val amount: String?,
    val quantity: Long = 0,
    val rate: String = "",
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val shotId: Int? = null,
    val content: String = "",
    val assetPath: String = "",
    val assetError: String = ""
) {
    fun json() = JSONObject().apply {
        put("id", id); put("category", category); put("label", label)
        put("amount", amount ?: JSONObject.NULL); put("quantity", quantity); put("rate", rate)
        put("input_tokens", inputTokens ?: JSONObject.NULL); put("output_tokens", outputTokens ?: JSONObject.NULL)
        put("created_at", createdAt)
        put("shot_id", shotId ?: JSONObject.NULL); put("content", content)
        put("asset_path", assetPath); put("asset_error", assetError)
    }

    companion object {
        fun from(value: JSONObject) = CostEntry(
            id = value.getString("id"), category = value.getString("category"), label = value.getString("label"),
            amount = if (value.isNull("amount")) null else value.getString("amount"),
            quantity = value.optLong("quantity"), rate = value.optString("rate"),
            inputTokens = if (value.isNull("input_tokens")) null else value.getLong("input_tokens"),
            outputTokens = if (value.isNull("output_tokens")) null else value.getLong("output_tokens"),
            createdAt = value.optLong("created_at"),
            shotId = if (value.isNull("shot_id")) null else value.optInt("shot_id").takeIf { it > 0 },
            content = value.optString("content"), assetPath = value.optString("asset_path"),
            assetError = value.optString("asset_error")
        )

        fun text(label: String, usage: TextUsage): CostEntry {
            val amount = if (usage.inputTokens != null && usage.outputTokens != null) {
                BigDecimal.valueOf(usage.inputTokens).multiply(BigDecimal("0.75"))
                    .add(BigDecimal.valueOf(usage.outputTokens).multiply(BigDecimal("3.75")))
                    .multiply(BigDecimal("0.33")).divide(BigDecimal("1000000")).toPlainString()
            } else null
            return CostEntry(id = usage.requestId.ifBlank { UUID.randomUUID().toString() },
                category = "text", label = label, amount = amount,
                inputTokens = usage.inputTokens, outputTokens = usage.outputTokens,
                rate = "输入 ¥0.75/M · 输出 ¥3.75/M · 倍率 0.33", content = usage.content)
        }

        fun image(label: String, unitPrice: String, shotId: Int? = null, prompt: String = "") = CostEntry(category = "image", label = label,
            amount = BigDecimal(unitPrice).toPlainString(), quantity = 1, rate = unitPrice, shotId = shotId, content = prompt)

        fun voice(label: String, spoken: String, shotId: Int? = null): CostEntry {
            val count = spoken.codePointCount(0, spoken.length).toLong()
            return CostEntry(category = "voice", label = label,
                amount = BigDecimal.valueOf(count).multiply(BigDecimal("0.0003")).toPlainString(),
                quantity = count, rate = "3", shotId = shotId, content = spoken)
        }
    }
}

internal data class ProjectCostSnapshot(val entries: List<CostEntry>, val hasEarlierUntrackedCalls: Boolean) {
    fun subtotal(category: String? = null): BigDecimal = entries
        .filter { category == null || it.category == category }
        .fold(BigDecimal.ZERO) { sum, entry -> sum + (entry.amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    val unknownCalls get() = entries.count { it.amount == null }
}

internal fun costMoney(amount: BigDecimal) = "¥" + amount.setScale(6, RoundingMode.HALF_UP).toPlainString()

internal class ProjectCosts(private val store: ProjectStore) {
    private companion object {
        val lock = Any()
        val cache = object : LinkedHashMap<String, ProjectCostSnapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProjectCostSnapshot>?) = size > 128
        }
    }
    fun file(projectId: String) = File(store.directory(projectId), "cost_ledger.json")
    fun cached(projectId: String): ProjectCostSnapshot? = synchronized(lock) { cache[file(projectId).absolutePath] }

    fun read(projectId: String): ProjectCostSnapshot = synchronized(lock) {
        val record = file(projectId)
        if (!record.exists()) return@synchronized ProjectCostSnapshot(emptyList(), true)
        val json = JSONObject(record.readText())
        val items = json.getJSONArray("entries")
        ProjectCostSnapshot((0 until items.length()).map { CostEntry.from(items.getJSONObject(it)) },
            json.optBoolean("has_earlier_untracked_calls", false)).also { cache[record.absolutePath] = it }
    }

    fun initialize(projectId: String) = synchronized(lock) {
        if (!file(projectId).exists()) write(projectId, ProjectCostSnapshot(emptyList(), false))
    }

    fun add(projectId: String, entry: CostEntry) = synchronized(lock) {
        val previous = read(projectId)
        val index = previous.entries.indexOfFirst { it.id == entry.id }
        val entries = previous.entries.toMutableList()
        if (index < 0) entries += entry else {
            val old = entries[index]
            if (old.amount != null && entry.amount == null) return@synchronized
            entries[index] = entry.copy(createdAt = old.createdAt, shotId = entry.shotId ?: old.shotId,
                content = entry.content.ifBlank { old.content }, assetPath = entry.assetPath.ifBlank { old.assetPath },
                assetError = entry.assetError.ifBlank { old.assetError })
        }
        if (entries != previous.entries) write(projectId,
            ProjectCostSnapshot(entries, previous.hasEarlierUntrackedCalls))
    }

    fun mediaFile(projectId: String, entry: CostEntry): File? {
        if (!entry.assetPath.matches(Regex("cost_assets/[a-f0-9-]{36}\\.(jpg|mp3)"))) return null
        val file = File(store.directory(projectId), entry.assetPath)
        return file.takeIf { it.isFile && it.length() > 1000 }
    }

    // Keep the exact billed result before later generations replace the shot's current file.
    // Copy outside the ledger lock so other concurrent image requests can still record costs.
    fun attachMedia(projectId: String, entryId: String, source: File) {
        try {
            archiveMedia(projectId, entryId, source)
        } catch (error: Exception) {
            // Archiving is optional: never fail or retry an already paid generation because of it.
            Log.w("ProjectCosts", "历史素材记录保存失败：$entryId", error)
        }
    }

    private fun archiveMedia(projectId: String, entryId: String, source: File) {
        val entry = read(projectId).entries.firstOrNull { it.id == entryId } ?: return
        require(entry.id.matches(Regex("[a-f0-9-]{36}"))) { "成本记录标识无效" }
        val extension = when (entry.category) { "image" -> "jpg"; "voice" -> "mp3"; else -> return }
        val relative = "cost_assets/${entry.id}.$extension"
        val destination = File(store.directory(projectId), relative)
        val pending = File(destination.parentFile, destination.name + ".pending")
        val result = runCatching {
            check(source.isFile && source.length() > 1000) { "本次生成素材不存在或为空" }
            val assetDirectory = requireNotNull(destination.parentFile)
            if (!assetDirectory.isDirectory) assetDirectory.mkdirs()
            check(assetDirectory.isDirectory) { "无法创建历史素材目录" }
            source.inputStream().use { input -> pending.outputStream().use { input.copyTo(it) } }
            Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        pending.delete()
        synchronized(lock) {
            val snapshot = read(projectId)
            val entries = snapshot.entries.map {
                if (it.id != entryId) it else it.copy(
                    assetPath = if (result.isSuccess) relative else it.assetPath,
                    assetError = result.exceptionOrNull()?.let { error -> error.message ?: error.javaClass.simpleName }.orEmpty())
            }
            if (entries != snapshot.entries) write(projectId, snapshot.copy(entries = entries))
        }
    }

    private fun write(projectId: String, snapshot: ProjectCostSnapshot) {
        val destination = file(projectId)
        check(destination.parentFile?.isDirectory == true) { "项目目录不存在，无法保存成本" }
        val pending = File(destination.parentFile, "cost_ledger.pending.json")
        try {
            pending.writeText(JSONObject().apply {
                put("version", 2); put("currency", "CNY")
                put("has_earlier_untracked_calls", snapshot.hasEarlierUntrackedCalls)
                put("entries", JSONArray().apply { snapshot.entries.forEach { put(it.json()) } })
            }.toString(2))
            Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            cache[destination.absolutePath] = snapshot
        } finally { pending.delete() }
    }
}
