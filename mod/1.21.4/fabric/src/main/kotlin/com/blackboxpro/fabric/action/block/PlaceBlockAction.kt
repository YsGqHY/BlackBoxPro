package com.blackboxpro.fabric.action.block

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.*
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class PlaceBlockAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val direction = DirectionUtil.fromString(params.requireString("face"))
        val hand = HandUtil.fromString(params.getStringOrNull("hand") ?: "main_hand")
        val cursorX = params.getDoubleOrDefault("cursorX", 0.5)
        val cursorY = params.getDoubleOrDefault("cursorY", 0.5)
        val cursorZ = params.getDoubleOrDefault("cursorZ", 0.5)
        val insideBlock = params.getBooleanOrDefault("insideBlock", false)
        val sequence = params.getIntOrDefault("sequence", 0)

        val player = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val hitResult = BlockHitResult(
            Vec3d(x + cursorX, y + cursorY, z + cursorZ),
            direction,
            BlockPos(x, y, z),
            insideBlock
        )

        player.sendPacket(PlayerInteractBlockC2SPacket(hand, hitResult, sequence))
        return ActionResult.ok("Placed block at $x, $y, $z")
    }
}
