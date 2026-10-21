package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket
import net.minecraft.util.math.BlockPos

class UpdateSignAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val isFrontText = params.getBooleanOrDefault("isFrontText", true)
        val linesArray = params.getAsJsonArray("lines")
            ?: return ActionResult.fail("Missing required field: lines")

        val lines = (0 until 4).map { i ->
            if (i < linesArray.size()) linesArray[i].asString else ""
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            UpdateSignC2SPacket(BlockPos(x, y, z), isFrontText, lines[0], lines[1], lines[2], lines[3])
        )
        return ActionResult.ok("Updated sign at $x, $y, $z")
    }
}
