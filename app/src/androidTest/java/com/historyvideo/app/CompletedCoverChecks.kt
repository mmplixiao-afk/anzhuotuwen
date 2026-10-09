package com.historyvideo.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import org.json.JSONObject
import java.io.File

internal object CompletedCoverChecks {
    fun run(context: Context) {
        check(completedCoverTitle("在古代被发配宁古塔当奴仆到底有多惨？", "") == "宁古塔奴仆")
        check(completedCoverTitle("在古代当县令到底有多爽？", "") == "县令")
        check(completedCoverTitle("主题", "  自己的\n 标题  ") == "自己的 标题")
        check(completedFileName("A/B\\C:*?\"<>|.") == "ABC")
        check(completedFileName("CON") == "_CON")
        check(completedFileName("字".repeat(110)).length == 100)
        check(completedFileName("A".repeat(99) + ".B") == "A".repeat(99))
        val store = ProjectStore(context)
        val project = store.create("在古代被发配宁古塔当奴仆到底有多惨？")
        val twin = store.create(project.title)
        try {
            project.shots = mutableListOf(Shot(1, "口播", "第一镜"), Shot(2, "口播", "另一镜"))
            project.coverShotId = 2
            store.output(project).writeBytes(ByteArray(2048) { it.toByte() })
            val generator = CompletedCover(store)
            val missing = runCatching { generator.generate(project) }.exceptionOrNull()
            check(missing?.message?.contains("第一镜封面素材") == true)
            check(!store.cover(project).exists())

            writeImage(store.image(project, project.shots[0]), Color.RED, Color.GREEN)
            writeImage(store.image(project, project.shots[1]), Color.BLUE, Color.BLUE)
            val source = store.image(project, project.shots[0])
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val cover = generator.generate(project)
            val bitmap = BitmapFactory.decodeFile(cover.absolutePath)
            try {
                check(bitmap.width == 1080 && bitmap.height == 1440)
                check(Color.alpha(bitmap.getPixel(200, 100)) == 255)
                check(Color.red(bitmap.getPixel(540, 100)) > 220)
                check(Color.green(bitmap.getPixel(540, 1340)) > 220)
                check(Color.red(bitmap.getPixel(540, 15)) > 220 && Color.blue(bitmap.getPixel(540, 15)) > 220)
            } finally { bitmap.recycle() }
            val firstInfo = JSONObject(store.completedInfo(project).readText())
            check(firstInfo.getString("cover_text") == "宁古塔奴仆")
            check(generator.isCurrent(project))
            cover.setLastModified(100000L)
            generator.generate(project)
            check(cover.lastModified() == 100000L)
            writeImage(store.image(project, project.shots[1]), Color.YELLOW, Color.YELLOW)
            generator.generate(project)
            check(cover.lastModified() == 100000L)
            project.coverText = "新封面"
            check(!generator.isCurrent(project))
            generator.generate(project)
            check(JSONObject(store.completedInfo(project).readText()).getString("cover_text") == "新封面")
            check(!store.directory(project.id).equals(store.directory(twin.id)))
            check(store.directory(project.id).walkTopDown().none { it.name.contains("pending") })
        } finally {
            store.delete(project.id)
            store.delete(twin.id)
        }
    }

    private fun writeImage(file: File, left: Int, right: Int) {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = left }
            canvas.drawRect(0f, 0f, 600f, 800f, paint)
            paint.color = right
            canvas.drawRect(600f, 0f, 1200f, 800f, paint)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
    }
}
