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
        val fileSize: Long,
        /**
         * D2：本帧的像素统计（判据见 [TooltipPixelProbe]）。
         * 带默认值 ⇒ 既有构造点（如其它版本的 provider）无需改动。
         */
        val tooltipProbe: TooltipPixelProbe.Stats? = null
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
                // D2：在 close() 之前的持有窗口内取像素统计（判据见 TooltipPixelProbe）。
                // 与写盘互不影响：writeTo 与像素读取是 NativeImage 上互相独立的方法。
                val probe = TooltipPixelProbe.countArgb(image.copyPixelsArgb(), w, h)
                logger.info("Screenshot saved: {} ({}x{}, {} bytes)", filePath, w, h, fileSize)
                callback.accept(Result.success(ScreenshotResult(filePath, w, h, fileSize, probe)))
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            callback.accept(Result.failure(e))
        }
    }

    /**
     * D2（设计 P）：**只统计、不落盘**地读取当前帧缓冲的 tooltip 像素统计。
     *
     * 与 [captureAsync] 走同一条 `takeScreenshot` 读取路径，但**不** `writeTo`：
     * 1. 不多产 PNG（负对照帧不落盘）；
     * 2. 不影响 [nextIndex] 的编号口径，也不影响按 `NNN_prefix.png` 计数的既有脚本；
     * 3. 主帧的命名与行为**完全不变**。
     *
     * ⚠️ 必须在主线程（渲染线程）调用——`takeScreenshot` 从帧缓冲纹理读回像素。
     */
    fun readTooltipStats(): Result<TooltipPixelProbe.Stats> {
        val framebuffer = MinecraftClient.getInstance().framebuffer
        return try {
            val image = ScreenshotRecorder.takeScreenshot(framebuffer)
            val stats = try {
                TooltipPixelProbe.countArgb(image.copyPixelsArgb(), image.width, image.height)
            } finally {
                image.close()
            }
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
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
