package com.blackboxpro.fabric.util

import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.ScreenshotRecorder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * 截图工具类。
 * 帧缓冲读取必须在渲染线程（主线程）调用。
 */
object ScreenshotHelper {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-Screenshot")
    private val INDEX_PATTERN = Regex("^(\\d{3}).*\\.png$")
    private val SANITIZE_PATTERN = Regex("[^a-zA-Z0-9_\\-.]")

    data class ScreenshotResult(
        val filePath: Path,
        val width: Int,
        val height: Int,
        val fileSize: Long
    )

    /**
     * 异步捕获当前帧缓冲并保存为 PNG。
     * 必须在主线程调用。结果通过回调返回。
     */
    fun captureAsync(directory: Path, fileName: String, callback: Consumer<Result<ScreenshotResult>>) {
        val framebuffer = MinecraftClient.getInstance().framebuffer

        try {
            val image = ScreenshotRecorder.takeScreenshot(framebuffer)
            try {
                Files.createDirectories(directory)
                val filePath = directory.resolve("$fileName.png")
                image.writeTo(filePath)
                val fileSize = Files.size(filePath)
                val w = image.width
                val h = image.height
                logger.info("Screenshot saved: {} ({}x{}, {} bytes)", filePath, w, h, fileSize)
                callback.accept(Result.success(ScreenshotResult(filePath, w, h, fileSize)))
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            callback.accept(Result.failure(e))
        }
    }

    /**
     * 计算目录下的下一个截图编号。
     */
    fun nextIndex(directory: Path): Int {
        if (!Files.exists(directory)) return 1
        val files = directory.toFile().listFiles() ?: return 1
        return (files.mapNotNull { INDEX_PATTERN.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0) + 1
    }

    /**
     * 清洗文件名，移除文件系统不允许的字符。
     */
    fun sanitize(name: String): String =
        name.replace(SANITIZE_PATTERN, "_")
}
