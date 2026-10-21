package com.blackboxpro.fabric.util

import com.blackboxpro.common.runtime.screenshot.RuntimeScreenshotBridge
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * D2 离线自检（**只用于验证判据实现，不参与产品逻辑，也不进 jar**——它位于 `src/test/kotlin`）。
 *
 * 为什么要有它：plugin 侧的成对断言（`controlTooltipBgLoose == 0 && tooltipBgLoose >= 300`）完全建立在
 * [TooltipPixelProbe] 的 E2 判据之上；只对着规格抄常量是不够的，必须证明**Kotlin 实现逐像素复算出的
 * 计数与规格的地面真值完全一致**，且对正负例给出正确的 PASS/FAILED。
 *
 * 端到端都不需要：不启动客户端、不连服务端、不跑 `run_test`——只吃由
 * `audit\d2-pixel-fixtures.py`（用 Pillow/numpy，**独立实现**）导出的 ARGB 小端 int32 像素数组。
 * 两个独立实现（Python/numpy 与 Kotlin）在同一批真实像素上给出同一组数字 ⇒ 判据可移植。
 *
 * 运行（需先 `compileKotlin` + `compileTestKotlin`，然后**不需要 gradle**）：
 * ```
 * java -cp "<module>\build\classes\kotlin\main;<module>\build\classes\kotlin\test;<kotlin-stdlib.jar>" \
 *      com.blackboxpro.fabric.util.TooltipPixelProbeCheck [像素目录]
 * ```
 * 注意入口类是 `TooltipPixelProbeCheck`（`object` + `@JvmStatic fun main`），**不是** `...CheckKt`。
 * 像素目录默认 `%TEMP%\d2-pixels`。退出码 0 = ALL OK，非 0 = 有用例失败。
 * Windows 控制台默认 GBK，中文会乱码（不影响判定）；加 `-Dstdout.encoding=UTF-8` 并 `chcp 65001` 可得到干净输出。
 */
object TooltipPixelProbeCheck {

    /**
     * 唯一出处：common 的 `RuntimeScreenshotBridge.TOOLTIP_BG_ASSERT_THRESHOLD`
     * （plugin 侧的 `d2MainThresholdLoose` 引用同一个常量 ⇒ 两处不可能漂移）。
     * `const val` 在编译期内联，所以离线自检的 `java -cp` 仍只需 main/test/stdlib 三个路径。
     */
    private const val MAIN_THRESHOLD_LOOSE = RuntimeScreenshotBridge.TOOLTIP_BG_ASSERT_THRESHOLD

    /** 帧名、期望的 E2 计数（断言判据）、期望的严格 A 计数（诊断）。 */
    private data class Expect(val name: String, val loose: Int, val strict: Int)

    private val EXPECTED = listOf(
        // 5 个"有 tooltip"帧（阳性）
        Expect("postfix-02-tooltip-slot36", 2504, 172),
        Expect("regress-tooltip", 1292, 252),
        Expect("timeout-01-b1-racelost-success", 1292, 252),
        Expect("timeout-03-recovery1-tooltip", 1292, 252),
        Expect("timeout-05-recovery2-tooltip", 1292, 252),
        // 7 个"无 tooltip"帧（阴性，含全部负对照）
        Expect("postfix-01-baseline-noaction", 0, 0),
        Expect("postfix-03-postplain-samehover", 0, 0),
        Expect("regress-pre", 0, 0),
        Expect("regress-postcontrol", 0, 0),
        Expect("timeout-02-after-timeout-plain", 0, 0),
        Expect("timeout-04-recovery-plain", 0, 0),
        Expect("tooltip-probe-A4", 0, 0)
    )

    private const val WIDTH = 854
    private const val HEIGHT = 480

    /** 与 plugin 侧 `BlackBoxTestCatalog.verify` 的 `screenshot_tooltip` 分支同构的判定。 */
    private fun judge(controlLoose: Int, mainLoose: Int): String? = when {
        controlLoose != 0 -> "negative control contaminated (control_loose=$controlLoose)"
        mainLoose < MAIN_THRESHOLD_LOOSE -> "tooltip not in frame (loose=$mainLoose < $MAIN_THRESHOLD_LOOSE)"
        else -> null
    }

    private fun loadArgb(dir: File, name: String): IntArray? {
        val f = File(dir, "${name}__${WIDTH}x${HEIGHT}.argb.bin")
        if (!f.isFile) return null
        val bytes = f.readBytes()
        val expectedBytes = WIDTH.toLong() * HEIGHT * 4
        check(bytes.size.toLong() == expectedBytes) {
            "${f.name}: 大小 ${bytes.size} != $expectedBytes"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        return IntArray(buf.remaining()).also { buf.get(it) }
    }

    private fun hex(argb: Int) = "%08X".format(argb)

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: (System.getenv("TEMP") ?: ".") + File.separator + "d2-pixels")
        var failures = 0

        // ---- 1. E2（断言判据）边界值 ----
        println("== 1. E2 断言判据边界（|R-B|<=4 且 G<=R-8 且 R<=40）==")
        val e2Cases = listOf(
            // ⚠️ Kotlin 里 0xFF...... ≥ Int.MAX_VALUE ⇒ 字面量推成 Long，必须 .toInt()
            // Lead 用像素直方图找出的**真·tooltip 背景**合成色：必须命中 E2（严格 A 恰恰抓不到）
            Triple("真背景 [27,12,27]", 0xFF1B0C1B.toInt(), true),
            Triple("真背景 [23,8,23]", 0xFF170817.toInt(), true),
            Triple("真背景 [30,15,30]", 0xFF1E0F1E.toInt(), true),
            Triple("[41,5,96] 紫边框 G 分量", 0xFF290560.toInt(), false),
            Triple("[62,62,62] 面板灰", 0xFF3E3E3E.toInt(), false),
            Triple("[252,252,252] 文字白", 0xFFFCFCFC.toInt(), false),
            // 边界
            Triple("R=40,G=32(B=40) 恰在上界", 0xFF282028.toInt(), true),
            Triple("R=41 超上界", 0xFF292029.toInt(), false),
            Triple("G=R-8 恰在落差上", 0xFF100810.toInt(), true),
            Triple("G=R-7 落差不足", 0xFF100910.toInt(), false),
            Triple("|R-B|=4 恰在界内", 0xFF140010.toInt(), true),
            Triple("|R-B|=5 超界", 0xFF150010.toInt(), false),
            Triple("全黑 [0,0,0]（G<=R-8 不成立 ⇒ 不命中）", 0xFF000000.toInt(), false)
        )
        for ((label, argb, want) in e2Cases) {
            val got = TooltipPixelProbe.isTooltipBackgroundLooseArgb(argb)
            val ok = got == want
            if (!ok) failures++
            println("  %-34s %s expect=%-5s got=%-5s %s".format(label, hex(argb), want, got, if (ok) "OK" else "**FAIL**"))
        }

        // ---- 2. 严格 A 只作诊断：把"真背景"与 A 的关系钉死（防止有人把 A 拿回断言）----
        println("== 2. 严格 A（仅诊断）：真 tooltip 背景必须**不**命中 ==")
        for (argb in intArrayOf(0xFF1B0C1B.toInt(), 0xFF170817.toInt(), 0xFF1E0F1E.toInt())) {
            val inA = TooltipPixelProbe.isTooltipBackgroundArgb(argb)
            if (inA) failures++ // 若命中，则 A 与 E2 重叠，注释里的"假阴性"论证需重写
            println("  %s 在严格 A 内 = %s（期望 false；命中的只有更暗的 [18,3,18] 一类）".format(hex(argb), inA))
        }

        // ---- 3. 成对判定逻辑（E2 + 阈值 300 / 负对照污染）----
        println("== 3. 成对判定（controlTooltipBgLoose==0 && tooltipBgLoose>=$MAIN_THRESHOLD_LOOSE）==")
        val judgeCases = listOf(
            Triple(0, 1292, null),        // live 主帧 ⇒ PASS（余量 4.31×）
            Triple(0, 2504, null),        // postfix 主帧 ⇒ PASS
            Triple(0, 300, null),         // 恰好等于阈值 ⇒ PASS
            Triple(0, 299, "main"),       // 差 1 ⇒ FAILED
            Triple(0, 0, "main"),         // 缺陷本体（修复前）⇒ FAILED（反向验证 (i)）
            Triple(1292, 1292, "control"),// 负对照被污染 ⇒ FAILED（不是假 PASS）
            Triple(-1, 2504, "control")   // 探针读取失败不得当成 0
        )
        for ((control, main, wantKind) in judgeCases) {
            val msg = judge(control, main)
            val gotKind = when {
                msg == null -> null
                msg.startsWith("negative control") -> "control"
                else -> "main"
            }
            val ok = gotKind == wantKind
            if (!ok) failures++
            println("  control_loose=%-5d main_loose=%-5d => %-9s %s".format(control, main, msg ?: "PASS", if (ok) "OK" else "**FAIL**"))
        }

        // ---- 4. 真实像素：Kotlin 复算 vs Python 地面真值 ----
        println("== 4. 真实帧逐帧复算（目录：${dir.absolutePath}）==")
        println("  %-34s %8s %8s %8s %8s  %s".format("frame", "loose", "want", "strict", "want", "verdict"))
        var present = 0
        for (e in EXPECTED) {
            val px = loadArgb(dir, e.name)
            if (px == null) {
                println("  %-34s （缺像素文件，跳过——请先跑 audit\\d2-pixel-fixtures.py）".format(e.name))
                failures++
                continue
            }
            present++
            val stats = TooltipPixelProbe.countArgb(px, WIDTH, HEIGHT)
            val wantVerdict = if (e.loose >= MAIN_THRESHOLD_LOOSE) "PASS" else "FAILED"
            val verdict = if (judge(0, stats.tooltipBgLoose) == null) "PASS" else "FAILED"
            val ok = stats.tooltipBgLoose == e.loose &&
                stats.tooltipBgStrict == e.strict &&
                verdict == wantVerdict
            if (!ok) failures++
            println("  %-34s %8d %8d %8d %8d  %s %s".format(
                e.name, stats.tooltipBgLoose, e.loose, stats.tooltipBgStrict, e.strict,
                verdict, if (ok) "OK" else "**FAIL**"
            ))
        }
        println("  （%d/%d 帧完成复算）".format(present, EXPECTED.size))

        println(if (failures == 0) "离线自检：ALL OK" else "离线自检：$failures 项 FAILED")
        if (failures != 0) {
            throw IllegalStateException("离线自检失败：$failures 项")
        }
    }
}
