package com.blackboxpro.fabric.action.movement

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireBoolean
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

class PlayerOnGroundAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val onGround = params.requireBoolean("onGround")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(onGround, false))
        return ActionResult.ok()
    }
}
