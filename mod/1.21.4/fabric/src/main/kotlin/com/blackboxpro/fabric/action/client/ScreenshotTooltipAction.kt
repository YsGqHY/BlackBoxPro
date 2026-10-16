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

        // drawing：是否继续在渲染回调里画 tooltip（读帧缓冲前一刻置 false）
        val drawing = AtomicBoolean(true)
        // drawn：渲染回调是否已画过至少一帧（用于确认"下一 tick 读到的帧含 tooltip"）
        val drawn = AtomicBoolean(false)
        // responded：同一 commandId 只发一次响应的守卫（超时分支与截图回调共用同一个 CAS）
        val responded = AtomicBoolean(false)
        // lastDrawError：渲染回调内绘制异常的最后一次原因，供超时文案区分"画不上"与"屏幕关了"
        val lastDrawError = AtomicReference<Throwable?>(null)

        // 3. 绘制进真实渲染通道：afterRender 在 Screen.renderWithTooltip 之后触发，回调给的
        //    DrawContext 就是本帧 GameRenderer 自己创建并正在使用的那个（不再手工构造）。
        ScreenEvents.afterRender(screen).register(ScreenEvents.AfterRender { _, drawContext, _, _, _ ->
            if (!drawing.get()) return@AfterRender
            try {
                drawContext.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY)
                drawn.set(true)
            } catch (e: Exception) {
                // 单帧绘制异常不打断渲染循环；记下最后一次原因，供超时分支给出可判别的文案
                lastDrawError.compareAndSet(null, e)
            }
        })

        // 4. 下一 tick 截图：tick 与渲染在主线程互斥；在正常渲染（≥1 帧完成）的前提下，
        //    读到的才是含 tooltip 的最近一整帧，异常路径由超时分支区分原因后回报
        scheduleCapture(
            client = client,
            screen = screen,
            commandId = commandId,
            testId = testId,
            prefix = prefix,
            hoverState = hoverState,
            drawing = drawing,
            drawn = drawn,
            responded = responded,
            lastDrawError = lastDrawError,
            attempt = 0
        )

        return ActionResult.async()
    }

    /**
     * 排下一次截图尝试，每 tick 检查一次：
     * - 已画过且仍是同一个屏幕 → 读帧缓冲截图；
     * - 否则重排，最多 [MAX_RENDER_ATTEMPTS] 次（覆盖"注册后还没渲染过"与"屏幕已关"）；
     * - 超时则停掉绘制并回 failure，文案按 [lastDrawError] 区分原因；与截图回调共用
     *   [responded] 守卫，保证只发一次响应。
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
        responded: AtomicBoolean,
        lastDrawError: AtomicReference<Throwable?>,
        attempt: Int
    ) {
        RuntimeTickScheduler.schedule(1) {
            if (responded.get()) return@schedule

            if (drawn.get() && client.currentScreen === screen) {
                captureScreenshot(commandId, testId, prefix, hoverState, drawing, responded)
                return@schedule
            }

            if (attempt + 1 >= MAX_RENDER_ATTEMPTS) {
                drawing.set(false)
                val reason = lastDrawError.get()?.let {
                    " (last draw error: ${it.javaClass.simpleName}: ${it.message})"
                } ?: " (screen closed, or rendering paused)"
                if (responded.compareAndSet(false, true)) {
                    runCatching {
                        RuntimeResponseSender.sendResponse(
                            commandId,
                            "failure",
                            "Render timeout after $MAX_RENDER_ATTEMPTS ticks: tooltip was not drawn$reason"
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
                responded = responded,
                lastDrawError = lastDrawError,
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
        responded: AtomicBoolean
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

    companion object {
        private val logger = LoggerFactory.getLogger("BlackBoxPro-ScreenshotTooltip")
        private val INDEX_PATTERN = Regex("^(\\d+).*\\.png$")
        private val SANITIZE_PATTERN = Regex("[^a-zA-Z0-9_\\-.]")
        private const val MAX_RENDER_ATTEMPTS = 20
    }
}
