package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.BlockFaceUtil
import com.blackboxpro.fabric.util.calculateYawPitch
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

/**
 * 瞄准指定方块的指定面。
 * Action ID: "look_at_block"
 */
class LookAtBlockAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val face = params.getStringOrNull("face") ?: "top"

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val center = BlockFaceUtil.calculateFaceCenter(x, y, z, face)
        val (yaw, pitch) = calculateYawPitch(
            center.x - player.x,
            center.y - player.eyeY,
            center.z - player.z
        )

        player.yaw = yaw
        player.pitch = pitch

        networkHandler.sendPacket(
            PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision)
        )

        val data = JsonObject().apply {
            addProperty("yaw", yaw)
            addProperty("pitch", pitch)
            addProperty("face", face)
            addProperty("targetX", center.x)
            addProperty("targetY", center.y)
            addProperty("targetZ", center.z)
        }

        return ActionResult.ok("Looking at block ($x, $y, $z) face=$face", data)
    }
}
