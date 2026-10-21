package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ItemStackSerializer
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.registry.Registries

/**
 * 查询当前打开的容器状态。
 * Action ID: "query_container_state"
 */
class QueryContainerStateAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val handler = player.currentScreenHandler
        val isInventoryOnly = handler === player.playerScreenHandler

        val data = JsonObject().apply {
            addProperty("open", !isInventoryOnly)
            addProperty("windowId", handler.syncId)
            addProperty("stateId", handler.revision)
            addProperty("type", try { handler.type?.let { Registries.SCREEN_HANDLER.getId(it)?.toString() } ?: "unknown" } catch (_: Throwable) { "unknown" })
            addProperty("slotCount", handler.slots.size)

            // 容器标题
            val screen = client.currentScreen
            if (screen is HandledScreen<*>) {
                addProperty("title", screen.title.string)
            }

            // 光标物品
            add("carriedItem", ItemStackSerializer.serializeSlot(handler.cursorStack))
        }

        return ActionResult.ok("Container state queried", data)
    }
}
