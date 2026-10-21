package com.blackboxpro.fabric.action.debug

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket

class PongAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val parameter = params.requireInt("parameter")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(CommonPongC2SPacket(parameter))
        return ActionResult.ok()
    }
}
