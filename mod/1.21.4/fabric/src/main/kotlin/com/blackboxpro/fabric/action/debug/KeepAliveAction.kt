package com.blackboxpro.fabric.action.debug

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireLong
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket

class KeepAliveAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val id = params.requireLong("id")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(KeepAliveC2SPacket(id))
        return ActionResult.ok()
    }
}
