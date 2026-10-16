package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockMinecartC2SPacket

class UpdateCommandBlockMinecartAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val entityId = params.requireInt("entityId")
        val command = params.requireString("command")
        val trackOutput = params.getBooleanOrDefault("trackOutput", true)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            UpdateCommandBlockMinecartC2SPacket(entityId, command, trackOutput)
        )
        return ActionResult.ok("Updated command block minecart entity $entityId")
    }
}
