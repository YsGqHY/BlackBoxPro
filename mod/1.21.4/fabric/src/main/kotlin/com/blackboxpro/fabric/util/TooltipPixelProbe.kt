package com.blackboxpro.fabric.util

/**
 * D2 像素探针：对**一帧的 ARGB 像素**做与图像方向无关的计数统计。
 *
 * ## 两个判据（**断言只用 E2**）
 *
 * **E2（断言判据，字段名 `tooltipBgLoose` / `controlTooltipBgLoose`）**：
 * ```
 * |R - B| <= 4  ∧  G <= R - 8  ∧  R <= 40
 * ```
 * 实测（`audit\d2-pixel-fixtures.py` 独立复算 12 张真实帧）：5 个"有 tooltip"帧为 **1292 ~ 2504**，
 * 7 个"无 tooltip"帧（含**全部**负对照）**恒为 0**；阈值 300 ⇒ 余量 4.31×。
 * 它完整覆盖 vanilla tooltip 背景的合成像素（`[27,12,27]`、`[23,8,23]`、`[30,15,30]` 等）。
 *
 * **严格 A（仅诊断，字段名 `tooltipBgStrict` / `controlTooltipBgStrict`）**：
 * ```
 * R,B ∈ [8,30]  ∧  |R - B| <= 4  ∧  G <= 6
 * ```
 * ⚠️ **禁止用于断言**：`G <= 6` 把真正的 tooltip 背景（`[27,12,27]` G=12、`[23,8,23]` G=8）
 * 排除在外，只抓到其中较暗的一小部分（`[18,3,18]` 等）⇒ **计数取决于 tooltip 底下压着什么背景**，
 * 在更亮的面板上可以掉到 0 而 tooltip 依然画得好好的（会**假阴性**、是 flaky 断言）。
 * 保留它只为诊断与文档对照。
 *
 * 输入口径 = `NativeImage.copyPixelsArgb()`：`IntArray`，长度 `width * height`，**行优先**，
 * 每元素 `0xAARRGGBB`（1.21.4 字节码实证：`getColorArgb` = `getColor`(ABGR) → `ColorHelper.fromAbgr`）。
 * 该口径与 `Pillow` 读同一张 PNG 得到的 `(R,G,B)` 逐像素一致，故离线自检可直接对比计数。
 *
 * 本文件**刻意不依赖任何 Minecraft 类型**（只吃 `IntArray`），因此可以在纯 JVM 上离线跑自检，
 * 见 `src/test/kotlin/.../TooltipPixelProbeCheck.kt`。
 */
object TooltipPixelProbe {

    // ---- E2：断言判据 ----

    /** E2 的 R 上界（含）。 */
    const val ASSERT_MAX_R = 40

    /** E2 的 |R - B| 上界（含）。 */
    const val ASSERT_MAX_CHANNEL_DELTA = 4

    /** E2 要求的 G 与 R 的最小落差（含），即 `G <= R - 8`。 */
    const val ASSERT_MIN_GREEN_GAP = 8

    // ---- 严格 A：仅诊断 ----

    /** 严格 A 的下界（含）。 */
    const val DIAG_MIN_CHANNEL = 8

    /** 严格 A 的上界（含）。 */
    const val DIAG_MAX_CHANNEL = 30

    /** 严格 A 的 G 上界（含）。 */
    const val DIAG_MAX_GREEN = 6

    /**
     * 一帧的统计结果。
     *
     * [tooltipBgLoose] = **E2**（断言判据）；[tooltipBgStrict] = 严格 A（**仅诊断**，见类注释）。
     *
     * [bboxX1]/[bboxY1]/[bboxX2]/[bboxY2] 描述 E2 命中像素的外接矩形（闭区间，图像坐标，与
     * `DrawContext` 屏幕坐标同向——该方向目前仍是【推断】，见规格 §1.5，故**不参与断言**，
     * 只作诊断随响应回传）。无命中时四个值均为 `-1`。
     */
    data class Stats(
        /** 严格 A 计数（诊断）。 */
        val tooltipBgStrict: Int,
        /** E2 计数（**断言判据**）。 */
        val tooltipBgLoose: Int,
        val bboxX1: Int,
        val bboxY1: Int,
        val bboxX2: Int,
        val bboxY2: Int,
        val width: Int,
        val height: Int
    ) {
        /** 诊断用 bbox 字符串；无命中时为 `null`（不写进响应）。 */
        val bbox: String?
            get() = if (tooltipBgLoose == 0) null else "$bboxX1,$bboxY1,$bboxX2,$bboxY2"
    }

    /**
     * E2（**断言判据**）：`|R - B| <= 4 ∧ G <= R - 8 ∧ R <= 40`。入参为 `0xAARRGGBB`。
     * alpha 不参与判定：截图来自不透明帧缓冲（`ScreenshotRecorder` 用 RGBA 图像 + 合成后的帧）。
     */
    fun isTooltipBackgroundLooseArgb(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val delta = r - b
        return (if (delta < 0) -delta else delta) <= ASSERT_MAX_CHANNEL_DELTA &&
            g <= r - ASSERT_MIN_GREEN_GAP &&
            r <= ASSERT_MAX_R
    }

    /**
     * 严格 A（**仅诊断，禁止用于断言**）：`R,B ∈ [8,30] ∧ |R - B| <= 4 ∧ G <= 6`。
     * 见类注释：该判据对背景敏感、会假阴性。
     */
    fun isTooltipBackgroundArgb(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val delta = r - b
        return r in DIAG_MIN_CHANNEL..DIAG_MAX_CHANNEL &&
            b in DIAG_MIN_CHANNEL..DIAG_MAX_CHANNEL &&
            (if (delta < 0) -delta else delta) <= ASSERT_MAX_CHANNEL_DELTA &&
            g <= DIAG_MAX_GREEN
    }

    /**
     * 全帧计数 + bbox（bbox 以 **E2** 命中像素为基准）。纯函数，无副作用、无 MC 依赖。
     *
     * @param pixels `0xAARRGGBB` 行优先数组，长度必须 >= `width * height`
     */
    fun countArgb(pixels: IntArray, width: Int, height: Int): Stats {
        var strict = 0
        var loose = 0
        var x1 = Int.MAX_VALUE
        var y1 = Int.MAX_VALUE
        var x2 = -1
        var y2 = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val argb = pixels[row + x]
                if (isTooltipBackgroundArgb(argb)) strict++
                if (isTooltipBackgroundLooseArgb(argb)) {
                    loose++
                    if (x < x1) x1 = x
                    if (y < y1) y1 = y
                    if (x > x2) x2 = x
                    if (y > y2) y2 = y
                }
            }
        }
        return if (loose == 0) {
            Stats(strict, 0, -1, -1, -1, -1, width, height)
        } else {
            Stats(strict, loose, x1, y1, x2, y2, width, height)
        }
    }
}
