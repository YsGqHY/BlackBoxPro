package com.blackboxpro.fabric.action.chat

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ChatHistoryBuffer
import com.blackboxpro.fabric.util.ChatStyleHelper
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent

class ClickChatTextAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val match = params.requireString("match")
        val index = params.getIntOrDefault("index", 0)
        val shouldExecute = params.getBooleanOrDefault("execute", true)

        val entry = ChatHistoryBuffer.getEntry(index)
            ?: return ActionResult.fail("Chat entry not found at index $index")
        val styleMatch = ChatStyleHelper.findStyleMatch(entry.raw, match)
            ?: return ActionResult.fail("Text not found in chat entry: $match")
        val clickEvent = styleMatch.style.clickEvent
            ?: return ActionResult.fail("Matched text has no click event")

        val data = ChatStyleHelper.clickEventToJson(clickEvent).apply {
            addProperty("matched", styleMatch.matchedText)
        }
        if (!shouldExecute) {
            data.addProperty("executed", false)
            return ActionResult.ok("Click event resolved", data)
        }

        val executed = executeClickEvent(clickEvent)
        data.addProperty("executed", executed)
        return ActionResult.ok(
            if (executed) "Chat click executed" else "Chat click resolved without execution",
            data
        )
    }

    private fun executeClickEvent(clickEvent: ClickEvent): Boolean {
        val client = MinecraftClient.getInstance()
        val networkHandler = client.networkHandler ?: return false
        return when (clickEvent.action) {
            ClickEvent.Action.RUN_COMMAND -> {
                val command = clickEvent.value
                if (command.startsWith("/")) {
                    networkHandler.sendChatCommand(command.removePrefix("/"))
                } else {
                    networkHandler.sendChatMessage(command)
                }
                true
            }

            ClickEvent.Action.COPY_TO_CLIPBOARD -> {
                client.keyboard.setClipboard(clickEvent.value)
                true
            }

            else -> false
        }
    }
}
