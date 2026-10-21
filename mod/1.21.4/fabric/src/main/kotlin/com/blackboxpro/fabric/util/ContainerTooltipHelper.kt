package com.blackboxpro.fabric.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.registry.Registries
import net.minecraft.screen.slot.Slot
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.tabooproject.reflex.Reflex.Companion.getProperty
import org.tabooproject.reflex.Reflex.Companion.invokeMethod
import kotlin.math.roundToInt

object ContainerTooltipHelper {

    data class TooltipSnapshot(
        val slotIndex: Int,
        val empty: Boolean,
        val itemId: String = "",
        val itemName: String = "",
        val count: Int = 0,
        val damage: Int = 0,
        val maxDamage: Int = 0,
        val tooltip: List<String> = emptyList(),
        val tooltipFormatted: List<String> = emptyList(),
        val tooltipJson: List<JsonElement> = emptyList()
    ) {
        val title: String
            get() = tooltip.firstOrNull().orEmpty()

        val lines: List<String>
            get() = tooltip.drop(1)

        fun toSlotTooltipJson(): JsonObject = JsonObject().apply {
            addProperty("slot", slotIndex)
            addProperty("empty", empty)
            if (!empty) {
                addProperty("itemId", itemId)
                addProperty("itemName", itemName)
                addProperty("count", count)
                addProperty("damage", damage)
                addProperty("maxDamage", maxDamage)
                add("tooltip", JsonArray().apply { tooltip.forEach(::add) })
                add("tooltipFormatted", JsonArray().apply { tooltipFormatted.forEach(::add) })
                add("tooltipJson", JsonArray().apply { tooltipJson.forEach(::add) })
            }
        }
    }

    data class TooltipState(
        val visible: Boolean,
        val hoveredSlot: Int = -1,
        val mouseX: Double = -1.0,
        val mouseY: Double = -1.0,
        val snapshot: TooltipSnapshot? = null
    ) {
        fun toQueryJson(): JsonObject = JsonObject().apply {
            addProperty("visible", visible)
            addProperty("tooltipVisible", visible)
            addProperty("slot", hoveredSlot)
            addProperty("hoveredSlot", hoveredSlot)
            if (mouseX >= 0.0) addProperty("mouseX", mouseX.roundToInt())
            if (mouseY >= 0.0) addProperty("mouseY", mouseY.roundToInt())
            if (snapshot != null && !snapshot.empty) {
                addProperty("title", snapshot.title)
                add("lines", JsonArray().apply { snapshot.lines.forEach(::add) })
                addProperty("itemId", snapshot.itemId)
                addProperty("itemName", snapshot.itemName)
                addProperty("count", snapshot.count)
                addProperty("damage", snapshot.damage)
                addProperty("maxDamage", snapshot.maxDamage)
                add("tooltip", JsonArray().apply { snapshot.tooltip.forEach(::add) })
                add("tooltipFormatted", JsonArray().apply { snapshot.tooltipFormatted.forEach(::add) })
                add("tooltipJson", JsonArray().apply { snapshot.tooltipJson.forEach { add(it) } })
            } else {
                addProperty("title", "")
                add("lines", JsonArray())
            }
        }

        fun applyToScreenState(data: JsonObject) {
            data.addProperty("hoveredSlot", hoveredSlot)
            data.addProperty("tooltipVisible", visible)
            if (snapshot != null && !snapshot.empty) {
                data.addProperty("tooltipTitle", snapshot.title)
                data.add("tooltipLines", JsonArray().apply { snapshot.lines.forEach(::add) })
            } else {
                data.addProperty("tooltipTitle", "")
                data.add("tooltipLines", JsonArray())
            }
        }
    }

    fun querySlotTooltip(slotIndex: Int, advanced: Boolean = false): TooltipSnapshot {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: throw IllegalStateException("Player not available")
        val handler = when (val screen = client.currentScreen) {
            is HandledScreen<*> -> screen.screenHandler
            else -> player.currentScreenHandler
        }

        if (slotIndex !in 0 until handler.slots.size) {
            throw IllegalArgumentException("Slot index $slotIndex out of range [0, ${handler.slots.size})")
        }

        return createTooltipSnapshot(client, slotIndex, handler.slots[slotIndex].stack, advanced)
    }

    fun queryCurrentTooltip(advanced: Boolean = false): TooltipState {
        val client = MinecraftClient.getInstance()
        val screen = client.currentScreen as? HandledScreen<*>
            ?: return TooltipState(visible = false)
        val layout = resolveLayout(screen) ?: return TooltipState(visible = false)
        val mouse = currentGuiMouse(client) ?: return TooltipState(visible = false)
        val hovered = findHoveredSlot(screen.screenHandler.slots, layout, mouse.first, mouse.second)
            ?: return TooltipState(visible = false, mouseX = mouse.first, mouseY = mouse.second)
        val snapshot = createTooltipSnapshot(client, hovered.first, hovered.second.stack, advanced)
        return TooltipState(
            visible = !snapshot.empty,
            hoveredSlot = hovered.first,
            mouseX = mouse.first,
            mouseY = mouse.second,
            snapshot = snapshot.takeIf { !it.empty }
        )
    }

    fun hoverSlot(windowId: Int, slotIndex: Int, advanced: Boolean = false): TooltipState {
        val client = MinecraftClient.getInstance()
        val screen = client.currentScreen as? HandledScreen<*>
            ?: throw IllegalStateException("No container screen open")
        val handler = screen.screenHandler
        if (handler.syncId != windowId) {
            throw IllegalArgumentException("Current windowId ${handler.syncId} does not match requested $windowId")
        }
        if (slotIndex !in 0 until handler.slots.size) {
            throw IllegalArgumentException("Slot index $slotIndex out of range [0, ${handler.slots.size})")
        }

        val layout = resolveLayout(screen)
            ?: throw IllegalStateException("Failed to resolve container layout")
        val slot = handler.slots[slotIndex]
        val targetX = layout.left + slot.x + 8.0
        val targetY = layout.top + slot.y + 8.0

        moveCursor(client, targetX, targetY)
        runCatching { screen.mouseMoved(targetX, targetY) }

        val snapshot = createTooltipSnapshot(client, slotIndex, slot.stack, advanced)
        return TooltipState(
            visible = !snapshot.empty,
            hoveredSlot = slotIndex,
            mouseX = targetX,
            mouseY = targetY,
            snapshot = snapshot.takeIf { !it.empty }
        )
    }

    private fun createTooltipSnapshot(
        client: MinecraftClient,
        slotIndex: Int,
        stack: ItemStack,
        advanced: Boolean
    ): TooltipSnapshot {
        if (stack.isEmpty) {
            return TooltipSnapshot(slotIndex = slotIndex, empty = true)
        }

        val player = client.player ?: throw IllegalStateException("Player not available")
        val tooltipLines = client.world?.let { world ->
            val tooltipType = if (advanced) TooltipType.ADVANCED else TooltipType.BASIC
            stack.getTooltip(Item.TooltipContext.create(world), player, tooltipType)
        } ?: Screen.getTooltipFromItem(client, stack)

        return TooltipSnapshot(
            slotIndex = slotIndex,
            empty = false,
            itemId = Registries.ITEM.getId(stack.item).toString(),
            itemName = stack.name.string,
            count = stack.count,
            damage = stack.damage,
            maxDamage = stack.maxDamage,
            tooltip = tooltipLines.map { it.string },
            tooltipFormatted = tooltipLines.map(ChatStyleHelper::componentToLegacyString),
            tooltipJson = tooltipLines.map(ChatStyleHelper::serializeComponent)
        )
    }

    private fun findHoveredSlot(
        slots: List<Slot>,
        layout: ScreenLayout,
        mouseX: Double,
        mouseY: Double
    ): Pair<Int, Slot>? =
        slots.withIndex().firstOrNull { (_, slot) ->
            val left = layout.left + slot.x
            val top = layout.top + slot.y
            mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16
        }?.let { it.index to it.value }

    private fun currentGuiMouse(client: MinecraftClient): Pair<Double, Double>? {
        val metrics = resolveWindowMetrics(client.window) ?: return null
        val xBuffer = BufferUtils.createDoubleBuffer(1)
        val yBuffer = BufferUtils.createDoubleBuffer(1)
        GLFW.glfwGetCursorPos(metrics.handle, xBuffer, yBuffer)
        val windowX = xBuffer.get(0)
        val windowY = yBuffer.get(0)
        return windowX * metrics.guiWidth / metrics.width to windowY * metrics.guiHeight / metrics.height
    }

    private fun moveCursor(client: MinecraftClient, guiX: Double, guiY: Double) {
        val metrics = resolveWindowMetrics(client.window)
            ?: throw IllegalStateException("Failed to resolve window metrics")
        val windowX = (guiX * metrics.width / metrics.guiWidth).coerceIn(0.0, metrics.width - 1.0)
        val windowY = (guiY * metrics.height / metrics.guiHeight).coerceIn(0.0, metrics.height - 1.0)
        GLFW.glfwSetCursorPos(metrics.handle, windowX, windowY)
    }

    private fun resolveLayout(screen: HandledScreen<*>): ScreenLayout? {
        // x/y 通过 AccessWidener 设为 accessible，Loom remapJar 自动处理映射
        return try {
            ScreenLayout(screen.x, screen.y)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveWindowMetrics(window: Any): WindowMetrics? {
        val width = readDouble(window, "getWidth", "width") ?: return null
        val height = readDouble(window, "getHeight", "height") ?: return null
        val guiWidth = readDouble(window, "getScaledWidth", "scaledWidth", "getGuiScaledWidth", "guiScaledWidth")
            ?: return null
        val guiHeight = readDouble(window, "getScaledHeight", "scaledHeight", "getGuiScaledHeight", "guiScaledHeight")
            ?: return null
        val handle = readLong(window, "getHandle", "getWindow", "handle", "window") ?: return null
        return WindowMetrics(handle, width, height, guiWidth, guiHeight)
    }

    private fun readInt(instance: Any, vararg names: String): Int? = readNumber(instance, *names)?.toInt()

    private fun readLong(instance: Any, vararg names: String): Long? = readNumber(instance, *names)?.toLong()

    private fun readDouble(instance: Any, vararg names: String): Double? = readNumber(instance, *names)?.toDouble()

    private fun readNumber(instance: Any, vararg names: String): Number? {
        names.forEach { name ->
            // 先尝试作为字段读取（更常见的场景）
            val fieldResult = runCatching { instance.getProperty<Any?>(name) }.getOrNull()
            if (fieldResult is Number) return fieldResult
            // 再尝试作为方法调用
            val methodResult = runCatching { instance.invokeMethod<Any?>(name) }.getOrNull()
            if (methodResult is Number) return methodResult
        }
        return null
    }

    private data class ScreenLayout(val left: Int, val top: Int)

    private data class WindowMetrics(
        val handle: Long,
        val width: Double,
        val height: Double,
        val guiWidth: Double,
        val guiHeight: Double
    )
}
