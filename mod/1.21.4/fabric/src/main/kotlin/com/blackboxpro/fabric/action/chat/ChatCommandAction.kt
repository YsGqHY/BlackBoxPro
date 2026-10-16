package com.blackboxpro.fabric.action.chat

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

class ChatCommandAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val command = params.requireString("command").removePrefix("/")

        if (command.length > 32767) {
            return ActionResult.fail("Command too long: ${command.length} > 32767")
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendChatCommand(command)
        return ActionResult.ok("Sent command: /$command")
    }
}
