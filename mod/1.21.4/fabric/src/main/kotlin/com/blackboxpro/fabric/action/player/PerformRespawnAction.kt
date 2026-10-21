package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket

class PerformRespawnAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN))
        return ActionResult.ok("Performed respawn")
    }
}
