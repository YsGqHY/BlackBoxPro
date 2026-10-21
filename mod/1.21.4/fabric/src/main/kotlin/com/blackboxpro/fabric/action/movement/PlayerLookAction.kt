package com.blackboxpro.fabric.action.movement

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

class PlayerLookAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val yaw = params.requireDouble("yaw").toFloat()
        val pitch = params.requireDouble("pitch").toFloat().coerceIn(-90f, 90f)
        val onGround = params.getBooleanOrDefault("onGround", true)

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        player.yaw = yaw
        player.pitch = pitch
        networkHandler.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround, false))
        return ActionResult.ok()
    }
}
