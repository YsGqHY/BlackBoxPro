package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ItemStackSerializer
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Hand

/**
 * 读取手持物品详情。
 * Action ID: "query_held_item"
 */
class QueryHeldItemAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val handStr = params.getStringOrNull("hand") ?: "main_hand"
        val hand = when (handStr.lowercase()) {
            "main_hand", "mainhand", "main" -> Hand.MAIN_HAND
            "off_hand", "offhand", "off" -> Hand.OFF_HAND
            else -> return ActionResult.fail("Invalid hand: $handStr")
        }

        val stack = player.getStackInHand(hand)
        val data = ItemStackSerializer.serialize(stack)

        return ActionResult.ok("Held item queried", data)
    }
}
