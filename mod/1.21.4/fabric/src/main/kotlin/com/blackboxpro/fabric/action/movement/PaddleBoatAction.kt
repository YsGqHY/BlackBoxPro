package com.blackboxpro.fabric.action.movement

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireBoolean
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.BoatPaddleStateC2SPacket

class PaddleBoatAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val leftPaddling = params.requireBoolean("leftPaddling")
        val rightPaddling = params.requireBoolean("rightPaddling")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(BoatPaddleStateC2SPacket(leftPaddling, rightPaddling))
        return ActionResult.ok()
    }
}
