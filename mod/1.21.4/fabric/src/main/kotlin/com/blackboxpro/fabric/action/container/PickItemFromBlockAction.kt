package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PickItemFromBlockC2SPacket
import net.minecraft.util.math.BlockPos

class PickItemFromBlockAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val includeData = params.getBooleanOrDefault("includeData", false)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(PickItemFromBlockC2SPacket(BlockPos(x, y, z), includeData))
        return ActionResult.ok("Picked item from block at $x, $y, $z")
    }
}
