package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket
import java.util.UUID

class SpectatorTeleportAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val uuidStr = params.requireString("targetUuid")

        val uuid = try {
            UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            return ActionResult.fail("Invalid UUID: $uuidStr")
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(SpectatorTeleportC2SPacket(uuid))
        return ActionResult.ok("Teleported to player $uuidStr")
    }
}
