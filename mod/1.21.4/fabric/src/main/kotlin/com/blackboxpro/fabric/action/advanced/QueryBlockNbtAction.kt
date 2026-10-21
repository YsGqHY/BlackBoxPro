package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.QueryBlockNbtC2SPacket
import net.minecraft.util.math.BlockPos

class QueryBlockNbtAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val transactionId = params.requireInt("transactionId")
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            QueryBlockNbtC2SPacket(transactionId, BlockPos(x, y, z))
        )
        return ActionResult.ok("Queried NBT for block at $x, $y, $z (transaction=$transactionId)")
    }
}
