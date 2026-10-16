package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ItemStackSerializer
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

/**
 * 读取背包指定槽位物品数据。
 * Action ID: "query_inventory_slot"
 */
class QueryInventorySlotAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val inventory = player.inventory
        val slot = params.getIntOrDefault("slot", inventory.selectedSlot)

        if (slot < 0 || slot > 40) {
            return ActionResult.fail("Invalid slot: $slot (expected 0-40)")
        }

        val stack = inventory.getStack(slot)
        val data = ItemStackSerializer.serialize(stack).apply {
            addProperty("slot", slot)
        }

        return ActionResult.ok("Inventory slot $slot queried", data)
    }
}
