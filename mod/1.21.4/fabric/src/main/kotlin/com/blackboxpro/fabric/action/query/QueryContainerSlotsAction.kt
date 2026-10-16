package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ItemStackSerializer
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

/**
 * 批量读取容器槽位。
 * Action ID: "query_container_slots"
 */
class QueryContainerSlotsAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val handler = player.currentScreenHandler
        val windowId = params.getIntOrDefault("windowId", handler.syncId)

        // 确定要查询的槽位
        val slotIndices = if (params.has("slots")) {
            params.getAsJsonArray("slots").map { it.asInt }
        } else {
            (0 until handler.slots.size).toList()
        }

        val slotsObj = JsonObject()
        for (idx in slotIndices) {
            if (idx < 0 || idx >= handler.slots.size) continue
            val stack = handler.slots[idx].stack
            slotsObj.add(idx.toString(), ItemStackSerializer.serializeSlot(stack))
        }

        val data = JsonObject().apply {
            addProperty("windowId", windowId)
            add("slots", slotsObj)
        }

        return ActionResult.ok("Container slots queried", data)
    }
}
