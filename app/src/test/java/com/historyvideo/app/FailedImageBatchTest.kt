package com.historyvideo.app

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedImageBatchTest {
    @Test
    fun batchWaveSizeIsTen() {
        assertEquals(10, FAILED_IMAGE_BATCH_CONCURRENCY)
    }

    @Test
    fun progressTextStaysOnTheBatch() {
        val detail = batchOptimizeProgressDetail(3, 25)
        assertEquals("批量优化并重生图 3/25", detail)
        assertFalse(detail.contains("镜头"))
        assertFalse(detail.contains("正在生成第"))
        assertFalse(detail.contains("图片已更新"))
    }

    @Test
    fun summaryKeepsSuccessAndFailureCounts() {
        assertEquals("批量优化与重生图完成：成功 4/4", batchOptimizeSummary(4, 4, emptyList()))
        assertEquals(
            "批量优化与重生图完成：成功 2/5；失败镜头：1、3、8",
            batchOptimizeSummary(2, 5, listOf(1, 3, 8))
        )
        val many = (1..12).toList()
        assertEquals(
            "批量优化与重生图完成：成功 0/12；失败镜头：1、2、3、4、5、6、7、8、9、10等 12 个",
            batchOptimizeSummary(0, 12, many)
        )
    }

    @Test
    fun runsAtMostTenAndFinishesEveryItem() = runBlocking {
        val inFlight = AtomicInteger()
        val lock = Any()
        var max = 0
        val seen = mutableListOf<Int>()
        forEachInWaves((0 until 25).toList()) { id ->
            val now = inFlight.incrementAndGet()
            synchronized(lock) { if (now > max) max = now }
            delay(20)
            inFlight.decrementAndGet()
            synchronized(lock) { seen += id }
        }
        assertEquals((0 until 25).toList(), seen.sorted())
        assertTrue(max in 2..FAILED_IMAGE_BATCH_CONCURRENCY)
    }

    @Test
    fun doesNotStartTheNextWaveUntilTheCurrentWaveFinishes() = runBlocking {
        val entered = mutableListOf<Int>()
        val lock = Any()
        val firstWaveEntered = CompletableDeferred<Unit>()
        val releaseFirstWave = CompletableDeferred<Unit>()
        val job = launch {
            forEachInWaves((0 until 25).toList(), waveSize = 10) { id ->
                val count = synchronized(lock) {
                    entered += id
                    entered.count { it < 10 }
                }
                if (id < 10) {
                    if (count == 10) firstWaveEntered.complete(Unit)
                    releaseFirstWave.await()
                }
            }
        }
        withTimeout(5_000) { firstWaveEntered.await() }
        val snapshot = synchronized(lock) { entered.toList() }
        assertEquals((0 until 10).toSet(), snapshot.toSet())
        assertEquals(10, snapshot.size)
        releaseFirstWave.complete(Unit)
        withTimeout(5_000) { job.join() }
        assertEquals((0 until 25).toList(), synchronized(lock) { entered.toList() }.sorted())
    }

    @Test
    fun cancellationDoesNotStartTheNextWave() = runBlocking {
        val started = AtomicInteger()
        val tenthStarted = CompletableDeferred<Unit>()
        val job = launch {
            forEachInWaves((0 until 25).toList(), waveSize = 10) {
                if (started.incrementAndGet() == 10) tenthStarted.complete(Unit)
                delay(30_000)
            }
        }
        withTimeout(5_000) { tenthStarted.await() }
        job.cancel()
        withTimeout(5_000) { job.join() }
        assertEquals(10, started.get())
    }

    @Test
    fun requestedWaveSizeCannotExceedTen() = runBlocking {
        val inFlight = AtomicInteger()
        val lock = Any()
        var max = 0
        forEachInWaves((0 until 15).toList(), waveSize = 15) {
            val now = inFlight.incrementAndGet()
            synchronized(lock) { if (now > max) max = now }
            delay(30)
            inFlight.decrementAndGet()
        }
        assertTrue(max in 2..FAILED_IMAGE_BATCH_CONCURRENCY)
    }
}
