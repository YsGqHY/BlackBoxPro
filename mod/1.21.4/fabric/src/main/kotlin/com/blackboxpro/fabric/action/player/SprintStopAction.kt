package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

class SprintStopAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        player.isSprinting = false
        networkHandler.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING))
        return ActionResult.ok("Stopped sprinting")
    }
}
