package com.blackboxpro.fabric.action.chat

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

class ChatMessageAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val message = params.requireString("message")

        if (message.length > 256) {
            return ActionResult.fail("Message too long: ${message.length} > 256")
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendChatMessage(message)
        return ActionResult.ok("Sent chat message")
    }
}
