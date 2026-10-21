package com.blackboxpro.fabric.util

import com.blackboxpro.common.runtime.screenshot.RuntimeScreenshotBridge
import net.minecraft.client.MinecraftClient
import java.nio.file.Path

object FabricRuntimeScreenshotProvider : RuntimeScreenshotBridge.Provider {

    override fun currentPlayerName(): String? =
        MinecraftClient.getInstance().player?.gameProfile?.name

    override fun gameDirectory(): Path? =
        MinecraftClient.getInstance().runDirectory.toPath()

    override fun captureAsync(
        directory: Path,
        fileName: String,
        callback: (Result<RuntimeScreenshotBridge.CaptureResult>) -> Unit
    ) {
        ScreenshotHelper.captureAsync(directory, fileName) { result ->
            callback(
                result.map {
                    RuntimeScreenshotBridge.CaptureResult(
                        filePath = it.filePath,
                        width = it.width,
                        height = it.height,
                        fileSize = it.fileSize,
                        // D2：ScreenshotHelper 在同一持有窗口里已算好统计量，这里只做能力声明与映射。
                        tooltipPixelProbe = if (it.tooltipProbe != null) {
                            RuntimeScreenshotBridge.PROBE_OK
                        } else {
                            RuntimeScreenshotBridge.PROBE_UNSUPPORTED
                        },
                        tooltipBgStrict = it.tooltipProbe?.tooltipBgStrict ?: 0,
                        tooltipBgLoose = it.tooltipProbe?.tooltipBgLoose ?: 0,
                        tooltipBbox = it.tooltipProbe?.bbox
                    )
                }
            )
        }
    }

    /**
     * D2（设计 P）：负对照帧——只统计、不落盘。1.21.4 fabric 是本轮唯一实现该能力的版本。
     */
    override fun readTooltipStats(): Result<RuntimeScreenshotBridge.TooltipStats> =
        ScreenshotHelper.readTooltipStats().map {
            RuntimeScreenshotBridge.TooltipStats(
                tooltipBgStrict = it.tooltipBgStrict,
                tooltipBgLoose = it.tooltipBgLoose,
                bbox = it.bbox
            )
        }
}
