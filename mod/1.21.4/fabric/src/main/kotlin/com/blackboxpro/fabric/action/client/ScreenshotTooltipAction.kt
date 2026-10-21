package com.blackboxpro.fabric.action.client

import com.blackboxpro.common.runtime.action.ActionResult
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig
import com.blackboxpro.common.runtime.dispatcher.RuntimeResponseSender
import com.blackboxpro.common.runtime.scheduler.RuntimeTickScheduler
import com.blackboxpro.common.runtime.screenshot.RuntimeScreenshotBridge
import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.util.ContainerTooltipHelper
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.Item
import net.minecraft.item.tooltip.TooltipType
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 在容器界面中悬停指定槽位，把 tooltip 画进**真实渲染通道**，再截图。
 *
 * 时序（与旧实现的关键差异）：
 * 1. hoverSlot 移动光标 + 取 tooltip 数据与文本行（不变）。
 * 2. 用 [ScreenEvents.afterRender] 注册回调，回调里**每帧**用回调传入的**真正的 DrawContext**
 *    调 `drawTooltip(...)`——绘制发生在真实渲染通道内，会随本帧正常提交进帧缓冲。
 * 3. 另在 `RuntimeTickScheduler.schedule(1)` 的**下一 tick** 读帧缓冲截图。
 * 4. 读帧缓冲前即关闭"继续绘制"开关，避免 tooltip 永久叠在真实画面上。
 *
 * D2（设计 P）追加的成对判据：**同一次执行**先拍"同光标、未注入绘制"的负对照帧（只统计、不落盘），
 * 再打开注入绘制拍主帧；两个计数随**同一个响应**的 `data` 回传，由 plugin 侧 `verify` 断言
 * **`controlTooltipBgLoose == 0 && tooltipBgLoose >= 300`**
 * （判据 E2 = `|R−B| ≤ 4 ∧ G ≤ R−8 ∧ R ≤ 40`；阈值的唯一出处是
 * `RuntimeScreenshotBridge.TOOLTIP_BG_ASSERT_THRESHOLD`）。
 *
 * `tooltipBgStrict` / `controlTooltipBgStrict`（判据 A，`G ≤ 6`）**仅作诊断、不参与断言**：
 * A 抓不到真正的 vanilla tooltip 背景（`[27,12,27]` G=12、`[23,8,23]` G=8），只抓到较暗的一小部分，
 * 计数随 tooltip 底下的背景变化，在更亮的面板上可掉到 0 而 tooltip 依然正常 ⇒ 用它会**假阴性**。
 *
 * 为什么必须成对：`HandledScreen.render` 自身会写 `focusedSlot`
 * 并可能走 vanilla 的悬停 tooltip 路径，单看"像素 > 0"会被 vanilla 骗过（零回归保护价值）。
 *
 * 为什么不是"回调里立刻截图"：`afterRender` 由 `Screen.renderWithTooltip` 触发，而本帧把几何
 * 真正提交给 GL 的最后一次 `DrawContext.draw()` 在其**之后**（1.21.4 实测字节码偏移 648 → 907），
 * 回调内立刻截图会拍不到刚排入缓冲的 tooltip。
 *
 * 为什么"下一 tick 截图"成立：tick 与渲染在客户端主线程上互斥执行，且回调每帧都在画；
 * 在客户端正常渲染（至少完成一帧）的前提下，下一 tick 读到的才是"已完成渲染并提交、
 * 且含 tooltip 的最近一整帧"。异常路径（渲染暂停、屏幕关闭、绘制持续抛异常）不保证能截到
 * tooltip，由超时分支区分原因后回报。
 *
 * 为什么不能照抄 1.21.11：1.21.11 用的 `DrawContext.drawTooltipImmediately` 在 1.21.4 **不存在**
 * （1.21.4 是传统即时渲染），`GuiRenderer` 也要 1.21.5+ 才有；本实现只用 1.21.4 实测存在的
 * `drawTooltip(TextRenderer, List<Text>, int, int)` 与 `ScreenEvents.afterRender`。
 * 另外 `Event` 没有 `unregister`，事件挂在 Screen 实例上随屏回收，故用本类自己的布尔量停止绘制。
 *
 * Action ID: "screenshot_tooltip"
 */
class ScreenshotTooltipAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult =
        ActionResult.fail("screenshot_tooltip requires commandId")

    override fun execute(params: JsonObject, commandId: String): ActionResult {
        val windowId = params.requireInt("windowId")
        val slotIndex = params.requireInt("slot")
        val testId = params.get("testId")?.asString ?: "default"
        val prefix = params.get("prefix")?.asString ?: "tooltip"

        val client = MinecraftClient.getInstance()
        val screen = client.currentScreen as? HandledScreen<*>
            ?: return ActionResult.fail("No container screen open")

        // 1. hover 移动光标 + 获取 tooltip 数据（不变）
        val hoverState = try {
            ContainerTooltipHelper.hoverSlot(windowId, slotIndex)
        } catch (e: Exception) {
            return ActionResult.fail(e.message ?: "Failed to hover slot")
        }

        if (!hoverState.visible || hoverState.snapshot == null) {
            return ActionResult.fail("Slot $slotIndex is empty, no tooltip to render")
        }

        // 2. 获取 tooltip 文本行（不变）
        val handler = screen.screenHandler
        val slot = handler.slots[slotIndex]
        val player = client.player ?: return ActionResult.fail("Player not available")
        val tooltipLines = client.world?.let { world ->
            slot.stack.getTooltip(Item.TooltipContext.create(world), player, TooltipType.BASIC)
        } ?: Screen.getTooltipFromItem(client, slot.stack)

        val mouseX = hoverState.mouseX.toInt()
        val mouseY = hoverState.mouseY.toInt()

        // drawing：是否允许在渲染回调里**注入**绘制 tooltip（读主帧前一刻置 false）。
        //   D2 设计 P：刻意以 false 起步——负对照相位必须先拍"同光标、未注入绘制"的一帧。
        val drawing = AtomicBoolean(false)
        // drawn：渲染回调是否已注入绘制过至少一帧（**主帧**的门）
        val drawn = AtomicBoolean(false)
        // rendered：渲染回调是否至少被调用过一次，不论是否绘制（**负对照相位**的门）；
        //   保证对照帧是 hover 之后真正渲染完成的一整帧，而不是 hover 之前的旧帧
        val rendered = AtomicBoolean(false)
        // responded：同一 commandId 只发一次响应的守卫（超时分支与截图回调共用同一个 CAS）
        val responded = AtomicBoolean(false)
        // lastDrawError：渲染回调内绘制异常的最后一次原因，供超时文案区分"画不上"与"屏幕关了"
        val lastDrawError = AtomicReference<Throwable?>(null)
        // 负对照帧的统计量；-1 = 尚未取得（**不得**把"没取到"当成 0，否则会假 PASS）
        val controlBgStrict = AtomicInteger(-1)
        val controlBgLoose = AtomicInteger(-1)

        // 3. 绘制进真实渲染通道：afterRender 在 Screen.renderWithTooltip 之后触发，回调给的
        //    DrawContext 就是本帧 GameRenderer 自己创建并正在使用的那个（不再手工构造）。
        //    回调同时充当"本帧已渲染"的探针（rendered，负对照相位的门），与是否绘制无关。
        ScreenEvents.afterRender(screen).register(ScreenEvents.AfterRender { _, drawContext, _, _, _ ->
            rendered.set(true)
            if (!drawing.get()) return@AfterRender
            try {
                drawContext.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY)
                drawn.set(true)
            } catch (e: Exception) {
                // 单帧绘制异常不打断渲染循环；记下最后一次原因，供超时分支给出可判别的文案
                lastDrawError.compareAndSet(null, e)
            }
        })

        // 4. 相位机（设计 P）：CONTROL（同光标、未注入绘制，**只统计不落盘**）
        //    → 取得负对照后才 drawing=true → MAIN（注入绘制后截图，命名/编号/行为与既有实现一致）。
        //    tick 与渲染在主线程互斥；在正常渲染（每个相位都等 rendered/drawn 的 ≥1 帧）的前提下，
        //    读到的才是"已完成渲染并提交"的最近一整帧，异常路径由超时分支区分原因后回报。
        scheduleCapture(
            client = client,
            screen = screen,
            commandId = commandId,
            testId = testId,
            prefix = prefix,
            hoverState = hoverState,
            drawing = drawing,
            drawn = drawn,
            rendered = rendered,
            responded = responded,
            lastDrawError = lastDrawError,
            controlBgStrict = controlBgStrict,
            controlBgLoose = controlBgLoose,
            phase = Phase.CONTROL,
            attempt = 0
        )

        return ActionResult.async()
    }

    /**
     * 排下一次截图尝试，每 tick 检查一次（D2 设计 P 的两个相位）：
     * - [Phase.CONTROL]：等渲染回调至少跑过一帧（[rendered]，与是否绘制无关）后，**只统计不落盘**地读
     *   当前帧的 tooltip 像素统计（负对照：同光标、未注入绘制，期望 0）；取得后才打开绘制并转 MAIN。
     *   读取失败**不伪造成 0**（伪造会变成假 PASS），直接回 failure。
     * - [Phase.MAIN]：沿用既有逻辑——已注入绘制过至少一帧且仍是同一个屏幕 → 读帧缓冲截图。
     * 两个相位各自最多重排 [MAX_RENDER_ATTEMPTS] 次；超时则停掉绘制并回 failure，与截图回调共用
     * [responded] 守卫，保证只发一次响应。MAIN 的超时文案与既有实现逐字一致。
     */
    private fun scheduleCapture(
        client: MinecraftClient,
        screen: HandledScreen<*>,
        commandId: String,
        testId: String,
        prefix: String,
        hoverState: ContainerTooltipHelper.TooltipState,
        drawing: AtomicBoolean,
        drawn: AtomicBoolean,
        rendered: AtomicBoolean,
        responded: AtomicBoolean,
        lastDrawError: AtomicReference<Throwable?>,
        controlBgStrict: AtomicInteger,
        controlBgLoose: AtomicInteger,
        phase: Phase,
        attempt: Int
    ) {
        RuntimeTickScheduler.schedule(1) {
            if (responded.get()) return@schedule

            if (phase == Phase.CONTROL) {
                // 屏幕已变 ⇒ 对照帧与主帧不可比，直接失败，不要拿不可比的帧下结论
                if (client.currentScreen !== screen) {
                    if (responded.compareAndSet(false, true)) {
                        runCatching {
                            RuntimeResponseSender.sendResponse(
                                commandId,
                                "failure",
                                "Screen closed or changed before the control frame"
                            )
                        }.onFailure { logger.error("response send failed for $commandId", it) }
                    }
                    return@schedule
                }

                if (rendered.get()) {
                    RuntimeScreenshotBridge.readTooltipStats().fold(
                        onSuccess = { stats ->
                            controlBgStrict.set(stats.tooltipBgStrict)
                            controlBgLoose.set(stats.tooltipBgLoose)
                            // 负对照已取得：此刻才允许注入绘制，并把主帧相位排到下一 tick
                            drawing.set(true)
                            scheduleCapture(
                                client = client,
                                screen = screen,
                                commandId = commandId,
                                testId = testId,
                                prefix = prefix,
                                hoverState = hoverState,
                                drawing = drawing,
                                drawn = drawn,
                                rendered = rendered,
                                responded = responded,
                                lastDrawError = lastDrawError,
                                controlBgStrict = controlBgStrict,
                                controlBgLoose = controlBgLoose,
                                phase = Phase.MAIN,
                                attempt = 0
                            )
                        },
                        onFailure = { error ->
                            if (responded.compareAndSet(false, true)) {
                                runCatching {
                                    RuntimeResponseSender.sendResponse(
                                        commandId,
                                        "failure",
                                        "Control frame probe failed: ${error.message}"
                                    )
                                }.onFailure { logger.error("response send failed for $commandId", it) }
                            }
                        }
                    )
                    return@schedule
                }
            } else if (drawn.get() && client.currentScreen === screen) {
                captureScreenshot(
                    commandId = commandId,
                    testId = testId,
                    prefix = prefix,
                    hoverState = hoverState,
                    drawing = drawing,
                    responded = responded,
                    controlBgStrict = controlBgStrict,
                    controlBgLoose = controlBgLoose
                )
                return@schedule
            }

            if (attempt + 1 >= MAX_RENDER_ATTEMPTS) {
                drawing.set(false)
                val what: String
                val reason: String
                if (phase == Phase.CONTROL) {
                    what = "control frame was not rendered"
                    reason = ""
                } else {
                    what = "tooltip was not drawn"
                    reason = lastDrawError.get()?.let {
                        " (last draw error: ${it.javaClass.simpleName}: ${it.message})"
                    } ?: " (screen closed, or rendering paused)"
                }
                if (responded.compareAndSet(false, true)) {
                    runCatching {
                        RuntimeResponseSender.sendResponse(
                            commandId,
                            "failure",
                            "Render timeout after $MAX_RENDER_ATTEMPTS ticks: $what$reason"
                        )
                    }.onFailure { logger.error("response send failed for $commandId", it) }
                }
                return@schedule
            }

            scheduleCapture(
                client = client,
                screen = screen,
                commandId = commandId,
                testId = testId,
                prefix = prefix,
                hoverState = hoverState,
                drawing = drawing,
                drawn = drawn,
                rendered = rendered,
                responded = responded,
                lastDrawError = lastDrawError,
                controlBgStrict = controlBgStrict,
                controlBgLoose = controlBgLoose,
                phase = phase,
                attempt = attempt + 1
            )
        }
    }

    private fun captureScreenshot(
        commandId: String,
        testId: String,
        prefix: String,
        hoverState: ContainerTooltipHelper.TooltipState,
        drawing: AtomicBoolean,
        responded: AtomicBoolean,
        controlBgStrict: AtomicInteger,
        controlBgLoose: AtomicInteger
    ) {
        // 已决定读取"当前这一帧"：先关掉绘制，避免后续帧继续叠加 tooltip。
        // 读取本身是同步 takeScreenshot，且渲染与 tick 在主线程互斥，不会读到半帧。
        // 有意设计：读取失败即终态、不重试——置位在同步读取之前，失败只会直接回 failure，
        // 不会留下"还在画却已放弃截图"的悬挂状态（将来若要支持重试，需把置位挪到成功之后）。
        drawing.set(false)

        val playerName = RuntimeScreenshotBridge.currentPlayerName() ?: "unknown"
        val baseDirectory = RuntimeScreenshotBridge.gameDirectory()
        if (baseDirectory == null) {
            if (responded.compareAndSet(false, true)) {
                runCatching {
                    RuntimeResponseSender.sendResponse(commandId, "failure", "Game directory not available")
                }.onFailure { logger.error("response send failed for $commandId", it) }
            }
            return
        }
        val screenshotConfig = RuntimeBlackBoxConfig.current.screenshot
        val directory = baseDirectory
            .resolve(screenshotConfig.rootDirectory)
            .resolve(sanitize(playerName))
            .resolve(sanitize(testId))

        val index = nextIndex(directory)
        val indexStr = index.toString().padStart(3, '0')
        val fileName = "${indexStr}_${sanitize(prefix)}"

        RuntimeScreenshotBridge.captureAsync(directory, fileName) { result ->
            result.fold(
                onSuccess = { screenshot ->
                    val data = hoverState.toQueryJson().apply {
                        val relativePath = baseDirectory.relativize(screenshot.filePath)
                        addProperty("filePath", relativePath.toString().replace('\\', '/'))
                        addProperty("width", screenshot.width)
                        addProperty("height", screenshot.height)
                        addProperty("fileSize", screenshot.fileSize)
                        addProperty("index", index)
                        // ---- D2（设计 P）：成对判据的两个数 + 能力声明 ----
                        // 断言只用 E2：tooltipBgLoose / controlTooltipBgLoose
                        //   （|R−B| ≤ 4 ∧ G ≤ R−8 ∧ R ≤ 40；实测阳性 1292~2504、阴性恒 0、阈值 300）
                        // *Strict 是严格判据 A 的计数，**仅诊断**（对背景敏感、会假阴性），不得参与断言。
                        addProperty("tooltipPixelProbe", RuntimeScreenshotBridge.PROBE_OK)
                        addProperty("tooltipBgStrict", screenshot.tooltipBgStrict)
                        addProperty("tooltipBgLoose", screenshot.tooltipBgLoose)
                        addProperty("controlTooltipBgStrict", controlBgStrict.get())
                        addProperty("controlTooltipBgLoose", controlBgLoose.get())
                        screenshot.tooltipBbox?.let { addProperty("tooltipBbox", it) }
                    }
                    if (responded.compareAndSet(false, true)) {
                        runCatching {
                            RuntimeResponseSender.sendResponse(commandId, "success", "Tooltip screenshot saved: $fileName.png", data)
                        }.onFailure { logger.error("response send failed for $commandId", it) }
                    }
                },
                onFailure = { error ->
                    if (responded.compareAndSet(false, true)) {
                        runCatching {
                            RuntimeResponseSender.sendResponse(commandId, "failure", "Screenshot failed: ${error.message}")
                        }.onFailure { logger.error("response send failed for $commandId", it) }
                    }
                }
            )
        }
    }

    private fun nextIndex(directory: Path): Int {
        if (!Files.exists(directory)) return 1
        val files = directory.toFile().listFiles() ?: return 1
        return (files.mapNotNull { INDEX_PATTERN.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0) + 1
    }

    private fun sanitize(name: String): String = name.replace(SANITIZE_PATTERN, "_")

    /** D2 设计 P 的相位：先拍同光标负对照帧，再启用注入绘制拍主帧。 */
    private enum class Phase { CONTROL, MAIN }

    companion object {
        private val logger = LoggerFactory.getLogger("BlackBoxPro-ScreenshotTooltip")
        private val INDEX_PATTERN = Regex("^(\\d+).*\\.png$")
        private val SANITIZE_PATTERN = Regex("[^a-zA-Z0-9_\\-.]")
        private const val MAX_RENDER_ATTEMPTS = 20
    }
}
