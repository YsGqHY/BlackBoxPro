package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket

class ClickButtonAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val windowId = params.requireInt("windowId")
        val buttonId = params.requireInt("buttonId")

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(ButtonClickC2SPacket(windowId, buttonId))
        return ActionResult.ok("Clicked button $buttonId in window $windowId")
    }
}
