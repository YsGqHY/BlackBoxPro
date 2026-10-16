package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.PlayerInput

class SneakStopAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        // 清除客户端本地 sneak 状态
        val pi = player.input.playerInput
        player.input.playerInput = PlayerInput(pi.forward(), pi.backward(), pi.left(), pi.right(), pi.jump(), false, pi.sprint())
        player.setSneaking(false)
        return ActionResult.ok("Stopped sneaking")
    }
}
