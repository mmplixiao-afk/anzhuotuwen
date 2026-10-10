package com.historyvideo.app

import android.content.Context
import org.json.JSONObject
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

        val store = ProjectStore(context)
        val project = store.create("成本验收")
        val legacy = VideoProject(UUID.randomUUID().toString(), "旧项目")
        store.save(legacy)
        val ledger = ProjectCosts(store)
        val executor = Executors.newFixedThreadPool(10)
        try {
            check(!ledger.read(project.id).hasEarlierUntrackedCalls)
            check(ledger.read(legacy.id).hasEarlierUntrackedCalls)
            val first = CostEntry.image("首次生成", "0.017")
            ledger.add(project.id, first)
            ledger.add(project.id, first)
            ledger.add(project.id, CostEntry.image("改价后重生", "0.025"))
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
            check(!ledger.file(project.id).parentFile!!.listFiles().orEmpty().any { it.name.contains("pending") })
        } finally {
            executor.shutdownNow()
            store.delete(project.id)
            store.delete(legacy.id)
        }
    }
}
