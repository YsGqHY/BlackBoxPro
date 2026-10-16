package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireBoolean
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket

class PlayerAbilitiesAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val flying = params.requireBoolean("flying")

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player is not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        if (flying && !player.abilities.allowFlying) {
            return ActionResult.fail("Player is not allowed to fly")
        }

        player.abilities.flying = flying
        networkHandler.sendPacket(UpdatePlayerAbilitiesC2SPacket(player.abilities))

        return ActionResult.ok("Set flying to $flying")
    }
}
