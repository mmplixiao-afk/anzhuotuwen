package com.historyvideo.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class TextUsage(val inputTokens: Long?, val outputTokens: Long?, val requestId: String = "") {
    companion object {
        fun from(response: JSONObject): TextUsage {
            val usage = response.optJSONObject("usage") ?: return TextUsage(null, null)
            fun count(primary: String, fallback: String): Long? {
                val value = if (usage.has(primary)) usage.opt(primary) else usage.opt(fallback)
                return (value as? Number)?.toLong()?.takeIf { it >= 0 }
            }
            return TextUsage(count("prompt_tokens", "input_tokens"), count("completion_tokens", "output_tokens"))
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
    val createdAt: Long = System.currentTimeMillis()
) {
    fun json() = JSONObject().apply {
        put("id", id); put("category", category); put("label", label)
        put("amount", amount ?: JSONObject.NULL); put("quantity", quantity); put("rate", rate)
        put("input_tokens", inputTokens ?: JSONObject.NULL); put("output_tokens", outputTokens ?: JSONObject.NULL)
        put("created_at", createdAt)
    }

    companion object {
        fun from(value: JSONObject) = CostEntry(
            id = value.getString("id"), category = value.getString("category"), label = value.getString("label"),
            amount = if (value.isNull("amount")) null else value.getString("amount"),
            quantity = value.optLong("quantity"), rate = value.optString("rate"),
            inputTokens = if (value.isNull("input_tokens")) null else value.getLong("input_tokens"),
            outputTokens = if (value.isNull("output_tokens")) null else value.getLong("output_tokens"),
            createdAt = value.optLong("created_at")
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
                rate = "输入 ¥0.75/M · 输出 ¥3.75/M · 倍率 0.33")
        }

        fun image(label: String, unitPrice: String) = CostEntry(category = "image", label = label,
            amount = BigDecimal(unitPrice).toPlainString(), quantity = 1, rate = unitPrice)

        fun voice(label: String, spoken: String): CostEntry {
            val count = spoken.codePointCount(0, spoken.length).toLong()
            return CostEntry(category = "voice", label = label,
                amount = BigDecimal.valueOf(count).multiply(BigDecimal("0.0003")).toPlainString(),
                quantity = count, rate = "3")
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
            entries[index] = entry.copy(createdAt = old.createdAt)
        }
        if (entries != previous.entries) write(projectId,
            ProjectCostSnapshot(entries, previous.hasEarlierUntrackedCalls))
    }

    private fun write(projectId: String, snapshot: ProjectCostSnapshot) {
        val destination = file(projectId)
        check(destination.parentFile?.isDirectory == true) { "项目目录不存在，无法保存成本" }
        val pending = File(destination.parentFile, "cost_ledger.pending.json")
        try {
            pending.writeText(JSONObject().apply {
                put("version", 1); put("currency", "CNY")
                put("has_earlier_untracked_calls", snapshot.hasEarlierUntrackedCalls)
                put("entries", JSONArray().apply { snapshot.entries.forEach { put(it.json()) } })
            }.toString(2))
            Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            cache[destination.absolutePath] = snapshot
        } finally { pending.delete() }
    }
}
