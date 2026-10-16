package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.calculateYawPitch
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

class LookAtAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val targetX = params.requireDouble("x")
        val targetY = params.requireDouble("y")
        val targetZ = params.requireDouble("z")

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val (yaw, pitch) = calculateYawPitch(
            targetX - player.x,
            targetY - player.eyeY,
            targetZ - player.z
        )

        player.yaw = yaw
        player.pitch = pitch

        networkHandler.sendPacket(
            PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision)
        )

        return ActionResult.ok("Looking at ($targetX, $targetY, $targetZ) yaw=$yaw pitch=$pitch")
    }
}
