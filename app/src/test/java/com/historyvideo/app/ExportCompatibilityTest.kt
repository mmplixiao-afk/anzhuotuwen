package com.historyvideo.app

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCompatibilityTest {
    @Test
    fun encoderSettingsStayH264BaselineAtEvenCanvasSizes() {
        val portrait = h264EncoderSettings(1080, 1920)
        val landscape = h264EncoderSettings(1920, 1080)
        val square = h264EncoderSettings(1440, 1440)
        listOf(portrait, landscape, square).forEach { settings ->
            assertEquals(H264_MIME, settings.mime)
            assertEquals("baseline", settings.profileName)
            assertEquals("surface", settings.colorFormat)
            assertEquals(EXPORT_FRAME_RATE, settings.frameRate)
            assertEquals(2, settings.iFrameIntervalSeconds)
            assertEquals("4", settings.levelName)
            assertEquals(4_976_640, settings.bitRate)
            assertTrue(settings.width % 2 == 0 && settings.height % 2 == 0)
        }
        assertEquals("3.1", avcLevelName(1280, 720))
        assertThrows(IllegalArgumentException::class.java) { h264EncoderSettings(1081, 1920) }
    }

    @Test
    fun bitrateStaysInsideTheCap() {
        assertEquals(1_500_000, h264Bitrate(160, 160))
        assertEquals(8_000_000, h264Bitrate(3840, 2160))
    }

    @Test
    fun onlyH264AacIsAccepted() {
        val good = MediaProbe(H264_MIME, AAC_MIME, 1080, 1920, 3_000_000L, 30)
        assertTrue(exportAccepted(good))
        assertTrue(exportAccepted(good.copy(videoMime = "h264", audioMime = "aac", frameRate = null)))
        assertFalse(exportAccepted(good.copy(videoMime = "video/mp4v-es")))
        assertFalse(exportAccepted(good.copy(videoMime = "mpeg4")))
        assertFalse(exportAccepted(good.copy(audioMime = "audio/mpeg")))
        assertFalse(exportAccepted(good.copy(width = 1081)))
        assertFalse(exportAccepted(good.copy(frameRate = 24)))
        assertFalse(exportAccepted(good.copy(durationUs = 0)))
    }

    @Test
    fun cachedMpeg4IsTranscodedInsteadOfReused() {
        assertEquals(
            FinalCacheAction.TRANSCODE_EXISTING,
            finalCacheAction(true, 50_000, "video/mp4v-es", AAC_MIME)
        )
        assertEquals(
            FinalCacheAction.REUSE,
            finalCacheAction(true, 50_000, H264_MIME, "aac")
        )
        assertEquals(
            FinalCacheAction.RENDER,
            finalCacheAction(false, 50_000, H264_MIME, AAC_MIME)
        )
        assertEquals(
            FinalCacheAction.RENDER,
            finalCacheAction(true, 500, H264_MIME, AAC_MIME)
        )
        assertEquals(
            FinalCacheAction.RENDER,
            finalCacheAction(true, 50_000, null, null)
        )
        assertEquals(
            FinalCacheAction.RENDER,
            finalCacheAction(true, 50_000, "video/mp4v-es", "audio/mpeg")
        )
    }

    @Test
    fun failedCheckDoesNotReplaceTheExistingFile() {
        val directory = File(System.getProperty("java.io.tmpdir"), "h264-export-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val existing = File(directory, "final.mp4").apply { writeBytes(ByteArray(2000) { 7 }) }
            val candidate = File(directory, "new.mp4").apply { writeBytes(ByteArray(2000) { 9 }) }
            val decision = decideExport(
                MediaProbe("video/mp4v-es", AAC_MIME, 1080, 1920, 1_000_000L, 30),
                fastStart = true,
                durationOk = true,
                dimensionsOk = true
            )
            assertEquals(ExportCommit.KEEP_EXISTING, decision.commit)
            val error = assertThrows(IllegalStateException::class.java) {
                applyExportDecision(decision, candidate, existing)
            }
            assertTrue(error.message.orEmpty().contains("已保留原成片"))
            assertTrue(error.message.orEmpty().contains("不是 H.264"))
            assertEquals(7, existing.readBytes()[0].toInt())
            assertTrue(candidate.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun verifiedFileReplacesOnlyAfterAcceptance() {
        val directory = File(System.getProperty("java.io.tmpdir"), "h264-export-replace-${System.nanoTime()}").apply { mkdirs() }
        try {
            val existing = File(directory, "final.mp4").apply { writeBytes(ByteArray(2000) { 3 }) }
            val verified = File(directory, "verified.mp4").apply { writeBytes(ByteArray(2000) { 4 }) }
            val decision = decideExport(
                MediaProbe(H264_MIME, AAC_MIME, 1080, 1920, 1_000_000L, 30),
                fastStart = true,
                durationOk = true,
                dimensionsOk = true
            )
            assertEquals(ExportCommit.REPLACE, decision.commit)
            applyExportDecision(decision, verified, existing)
            assertEquals(4, existing.readBytes()[0].toInt())
            assertFalse(verified.exists())
            assertFalse(File(directory, "final.mp4.bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingResultRestoresTheOriginal() {
        val directory = File(System.getProperty("java.io.tmpdir"), "h264-export-restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val existing = File(directory, "final.mp4").apply { writeBytes(ByteArray(2000) { 5 }) }
            val missing = File(directory, "missing.mp4")
            assertThrows(IllegalArgumentException::class.java) {
                replaceVerifiedFile(missing, existing)
            }
            assertEquals(5, existing.readBytes()[0].toInt())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun fastStartRequiresMoovBeforeMdat() {
        val directory = File(System.getProperty("java.io.tmpdir"), "h264-export-boxes-${System.nanoTime()}").apply { mkdirs() }
        try {
            val ready = File(directory, "ready.mp4")
            ready.writeBytes(box("ftyp", ByteArray(8)) + box("moov", ByteArray(4)) + box("mdat", ByteArray(16)))
            assertEquals(listOf("ftyp", "moov", "mdat"), mp4TopLevelBoxTypes(ready))
            assertTrue(fastStartFromBoxOrder(mp4TopLevelBoxTypes(ready)))
            val late = File(directory, "late.mp4")
            late.writeBytes(box("ftyp", ByteArray(8)) + box("mdat", ByteArray(16)) + box("moov", ByteArray(4)))
            assertFalse(fastStartFromBoxOrder(mp4TopLevelBoxTypes(late)))
            assertFalse(durationCloseEnough(1_000_000L, 1_300_000L))
            assertTrue(durationCloseEnough(1_000_000L, 1_100_000L))
            assertTrue(dimensionsMatch(1080, 1920, 1080, 1920))
            assertFalse(dimensionsMatch(1080, 1920, 720, 1280))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failureMessageKeepsTheOriginalOnlyWhenOneExists() {
        assertTrue(h264FailureMessage(true, "编码器失败").startsWith("H.264 转码失败，已保留原成片："))
        assertEquals("H.264 转码失败：编码器失败", h264FailureMessage(false, "编码器失败"))
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val header = ByteBuffer.allocate(8).putInt(size).put(type.toByteArray(Charsets.ISO_8859_1)).array()
        return header + payload
    }
}
