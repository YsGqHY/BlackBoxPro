package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.PlayerInput

class SneakStartAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        // 设置客户端本地 sneak 状态，后续 tick 循环会自动在 PlayerInput 中携带 sneaking=true
        val pi = player.input.playerInput
        player.input.playerInput = PlayerInput(pi.forward(), pi.backward(), pi.left(), pi.right(), pi.jump(), true, pi.sprint())
        player.setSneaking(true)
        return ActionResult.ok("Started sneaking")
    }
}
