package com.blackboxpro.fabric.action.movement

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket

class ConfirmTeleportationAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val teleportId = params.requireInt("teleportId")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(TeleportConfirmC2SPacket(teleportId))
        return ActionResult.ok()
    }
}
