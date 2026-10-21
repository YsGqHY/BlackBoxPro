package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

/**
 * 模拟玩家跳跃。
 * Action ID: "jump"
 */
class JumpAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        if (!player.isOnGround) {
            return ActionResult.fail("Player is not on ground")
        }

        val fromY = player.y
        player.jump()

        val data = JsonObject().apply {
            addProperty("jumped", true)
            addProperty("fromY", fromY)
            addProperty("velocity", player.velocity.y)
        }

        return ActionResult.ok("Player jumped", data)
    }
}
