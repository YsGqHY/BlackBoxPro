package com.blackboxpro.fabric.action.block

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.DirectionUtil
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos

class DigFinishAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val face = DirectionUtil.fromString(params.requireString("face"))
        val sequence = params.getIntOrDefault("sequence", 0)

        val player = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        player.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                BlockPos(x, y, z),
                face,
                sequence
            )
        )
        return ActionResult.ok("Finished digging block at $x, $y, $z")
    }
}
