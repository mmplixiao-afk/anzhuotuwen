package com.historyvideo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors

internal object ProjectCostChecks {
    fun run(context: Context) {
        val usage = TextUsage.from(JSONObject().put("usage", JSONObject()
            .put("prompt_tokens", 1000).put("completion_tokens", 2000)))
        check(CostEntry.text("正文", usage).amount!!.toBigDecimal().compareTo(BigDecimal("0.0027225")) == 0)
        check(TextUsage.from(JSONObject().put("usage", JSONObject().put("input_tokens", 50).put("output_tokens", 100))) == TextUsage(50, 100))
        check(CostEntry.text("无用量", TextUsage.from(JSONObject())).amount == null)
        check(CostEntry.voice("配音", "字".repeat(10000)).amount!!.toBigDecimal().compareTo(BigDecimal("3")) == 0)
        check(CostEntry.voice("配音", "A\uD83D\uDE00").quantity == 2L)
        val response = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("content", "本次生成结果"))))
        val withoutUsage = TextUsage.from(response)
        check(withoutUsage.inputTokens == null && withoutUsage.content == "本次生成结果")
        check(TextUsage.from(response.put("usage", JSONObject()
            .put("prompt_tokens", 1000).put("completion_tokens", 2000))).content == "本次生成结果")
        val oldEntry = CostEntry.image("旧记录", "0.017").json().apply {
            remove("shot_id"); remove("content"); remove("asset_path"); remove("asset_error")
        }
        check(CostEntry.from(oldEntry).let { it.shotId == null && it.content.isEmpty() && it.assetPath.isEmpty() })

        val store = ProjectStore(context)
        val project = store.create("成本验收")
        val legacy = VideoProject(UUID.randomUUID().toString(), "旧项目")
        store.save(legacy)
        val ledger = ProjectCosts(store)
        val executor = Executors.newFixedThreadPool(10)
        try {
            check(!ledger.read(project.id).hasEarlierUntrackedCalls)
            check(ledger.read(legacy.id).hasEarlierUntrackedCalls)
            val first = CostEntry.image("首次生成", "0.017", shotId = 1, prompt = "首次提示词")
            ledger.add(project.id, first)
            ledger.add(project.id, first)
            val regenerated = CostEntry.image("改价后重生", "0.025", shotId = 1, prompt = "修改提示词")
            ledger.add(project.id, regenerated)
            check(ledger.read(project.id).subtotal("image").compareTo(BigDecimal("0.042")) == 0)
            val jobs = (1..10).map { shot -> executor.submit {
                ledger.add(project.id, CostEntry.image("并发 $shot", "0.017"))
            } }
            jobs.forEach { it.get() }
            ledger.add(project.id, CostEntry.voice("首次配音", "字".repeat(100)))
            ledger.add(project.id, CostEntry.voice("修改后重配音", "字".repeat(50)))
            ledger.add(project.id, CostEntry.text("选题", usage))
            ledger.add(project.id, CostEntry.text("拆镜首轮", usage))
            ledger.add(project.id, CostEntry.text("拆镜重试", usage))
            ledger.add(project.id, CostEntry.text("未返回用量", TextUsage(null, null)))
            val snapshot = ProjectCosts(store).read(project.id)
            check(snapshot.entries.count { it.category == "image" } == 12)
            check(snapshot.subtotal("image").compareTo(BigDecimal("0.212")) == 0)
            check(snapshot.subtotal("voice").compareTo(BigDecimal("0.045")) == 0)
            check(snapshot.subtotal("text").compareTo(BigDecimal("0.0081675")) == 0)
            check(snapshot.unknownCalls == 1)
            store.save(project)
            store.applyStoryboard(project, listOf(Shot(1, "原文", "提示词")))
            store.save(project)
            check(ledger.read(project.id).entries == snapshot.entries)
            ledger.add(legacy.id, CostEntry.image("旧项目重生", "0.017"))
            check(ledger.read(legacy.id).hasEarlierUntrackedCalls)
            check(ledger.read(legacy.id).entries.size == 1)
            val requestId = UUID.randomUUID().toString()
            val pending = CostEntry.text("请求开始", TextUsage(null, null, requestId))
            ledger.add(legacy.id, pending)
            check(ledger.read(legacy.id).unknownCalls == 1)
            ledger.add(legacy.id, CostEntry.text("请求完成", usage.copy(requestId = requestId)))
            check(ledger.read(legacy.id).entries.size == 2)
            check(ledger.read(legacy.id).unknownCalls == 0)
            ledger.add(legacy.id, pending)
            check(ledger.read(legacy.id).unknownCalls == 0)

            val source = File(store.directory(project.id), "cost_check_source.jpg")
            val originalBytes = ByteArray(2048) { (it % 251).toByte() }
            val regeneratedBytes = ByteArray(3072) { (it % 127).toByte() }
            source.writeBytes(originalBytes)
            ledger.attachMedia(project.id, first.id, source)
            source.writeBytes(regeneratedBytes)
            ledger.attachMedia(project.id, regenerated.id, source)
            val reloaded = ProjectCosts(store).read(project.id)
            val firstSaved = reloaded.entries.first { it.id == first.id }
            val secondSaved = reloaded.entries.first { it.id == regenerated.id }
            check(firstSaved.assetPath != secondSaved.assetPath)
            check(ledger.mediaFile(project.id, firstSaved)!!.readBytes().contentEquals(originalBytes))
            check(ledger.mediaFile(project.id, secondSaved)!!.readBytes().contentEquals(regeneratedBytes))
            check(reloaded.entries.size == snapshot.entries.size && reloaded.subtotal() == snapshot.subtotal())
            ledger.add(project.id, first.copy(shotId = null, content = ""))
            check(ledger.read(project.id).entries.first { it.id == first.id }.let {
                it.assetPath == firstSaved.assetPath && it.shotId == 1 && it.content == "首次提示词"
            })
            ledger.attachMedia(project.id, first.id, File(source.parentFile, "missing_asset.jpg"))
            check(ledger.read(project.id).entries.first { it.id == first.id }.let {
                it.assetPath == firstSaved.assetPath && it.assetError.isNotBlank() && it.amount == first.amount
            })
            check(ledger.mediaFile(project.id, firstSaved.copy(assetPath = "../cost_check_source.jpg")) == null)
            check(!ledger.file(project.id).parentFile!!.listFiles().orEmpty().any { it.name.contains("pending") })
        } finally {
            executor.shutdownNow()
            store.delete(project.id)
            store.delete(legacy.id)
        }
    }
}
