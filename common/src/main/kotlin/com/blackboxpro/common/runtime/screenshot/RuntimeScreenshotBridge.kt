package com.blackboxpro.common.runtime.screenshot

import java.nio.file.Path

object RuntimeScreenshotBridge {

    /** [CaptureResult.tooltipPixelProbe]：本版本实现了像素探针，统计量可信。 */
    const val PROBE_OK = "ok"

    /** [CaptureResult.tooltipPixelProbe]：本版本未实现像素探针，统计量恒为默认值、**不得用于断言**。 */
    const val PROBE_UNSUPPORTED = "unsupported"

    /**
     * D2 成对断言里**主帧像素计数的唯一阈值出处**
     * （判据 E2 = `|R−B| ≤ 4 ∧ G ≤ R−8 ∧ R ≤ 40`）。
     *
     * 标定：`audit\d2-pixel-fixtures.py` 对 `audit\` 下 12 张真实帧独立复算 ⇒
     * 阳性 5 帧 = **1292 ~ 2504**、阴性 7 帧（含全部负对照）**恒为 0** ⇒ 余量 **4.31×**。
     *
     * **plugin 的 `d2MainThresholdLoose` 与离线自检的 `MAIN_THRESHOLD_LOOSE` 都必须引用本常量**，
     * 不要各自写字面量（两处独立字面量只靠注释互指，迟早漂移）。
     */
    const val TOOLTIP_BG_ASSERT_THRESHOLD = 300

    /**
     * D2（设计 P）负对照帧的统计量：同光标、未注入绘制的那一帧。
     * 跨版本共用；未实现探针的版本不会产生它。
     */
    data class TooltipStats(
        /** 严格判据 A 的计数，**仅诊断**（对 tooltip 底下的背景敏感、会假阴性，禁止用于断言）。 */
        val tooltipBgStrict: Int,
        /** **断言判据 E2** 的计数（`|R−B| ≤ 4 ∧ G ≤ R−8 ∧ R ≤ 40`）；负对照正确时应为 0。 */
        val tooltipBgLoose: Int,
        /** 诊断用 bbox；无命中为 null。 */
        val bbox: String?
    )

    data class CaptureResult(
        val filePath: Path,
        val width: Int,
        val height: Int,
        val fileSize: Long,
        /**
         * D2 能力声明：[PROBE_OK] 表示本帧带真实像素统计，[PROBE_UNSUPPORTED]（默认值）表示
         * 该版本未实现探针。**全部带默认值** ⇒ 既有构造点无需改动。
         */
        val tooltipPixelProbe: String = RuntimeScreenshotBridge.PROBE_UNSUPPORTED,
        /** 主帧的严格判据 A 计数，**仅诊断**。 */
        val tooltipBgStrict: Int = 0,
        /** 主帧的 **E2** 计数（断言判据）。 */
        val tooltipBgLoose: Int = 0,
        /** 诊断用 bbox；无命中为 null。 */
        val tooltipBbox: String? = null
    )

    interface Provider {
        fun currentPlayerName(): String?
        fun gameDirectory(): Path?
        fun captureAsync(directory: Path, fileName: String, callback: (Result<CaptureResult>) -> Unit)

        /**
         * D2：只统计、不落盘地读取一帧（设计 P 的负对照帧）。
         *
         * 默认实现直接失败 ⇒ **未实现该能力的版本无需任何改动**（其它版本的 provider 不覆写即可，
         * 其 [CaptureResult.tooltipPixelProbe] 保持 [PROBE_UNSUPPORTED]）。
         */
        fun readTooltipStats(): Result<TooltipStats> =
            Result.failure(UnsupportedOperationException("tooltip pixel probe not supported"))
    }

    private object NoopProvider : Provider {
        override fun currentPlayerName(): String? = null
        override fun gameDirectory(): Path? = null
        override fun captureAsync(directory: Path, fileName: String, callback: (Result<CaptureResult>) -> Unit) {
            callback(Result.failure(IllegalStateException("Screenshot provider not bound")))
        }
    }

    @Volatile
    private var provider: Provider = NoopProvider

    fun bind(provider: Provider) {
        this.provider = provider
    }

    fun currentPlayerName(): String? = provider.currentPlayerName()

    fun gameDirectory(): Path? = provider.gameDirectory()

    fun captureAsync(directory: Path, fileName: String, callback: (Result<CaptureResult>) -> Unit) {
        provider.captureAsync(directory, fileName, callback)
    }

    /**
     * D2：读负对照帧的统计量。未绑定 provider 或版本未实现时返回失败（**不要**伪造成 0）。
     * 必须在客户端主线程调用。
     */
    fun readTooltipStats(): Result<TooltipStats> = provider.readTooltipStats()
}
