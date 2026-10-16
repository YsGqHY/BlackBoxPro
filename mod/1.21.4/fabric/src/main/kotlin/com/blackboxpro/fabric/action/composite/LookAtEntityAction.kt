package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.calculateYawPitch
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

class LookAtEntityAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val entityId = params.requireInt("entityId")

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val world = client.world
            ?: return ActionResult.fail("World not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val entity = world.getEntityById(entityId)
            ?: return ActionResult.fail("Entity not found: $entityId")

        val (yaw, pitch) = calculateYawPitch(
            entity.x - player.x,
            entity.eyeY - player.eyeY,
            entity.z - player.z
        )

        player.yaw = yaw
        player.pitch = pitch

        networkHandler.sendPacket(
            PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision)
        )

        return ActionResult.ok("Looking at entity $entityId yaw=$yaw pitch=$pitch")
    }
}
