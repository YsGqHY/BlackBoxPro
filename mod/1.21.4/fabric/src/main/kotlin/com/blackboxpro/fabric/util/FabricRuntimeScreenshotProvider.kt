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
                        fileSize = it.fileSize
                    )
                }
            )
        }
    }
}
