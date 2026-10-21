package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ChatHistoryBuffer
import com.blackboxpro.fabric.util.ChatStyleHelper
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject

class QueryChatStyleAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val match = params.requireString("match")
        val index = params.getIntOrDefault("index", 0)

        val entry = ChatHistoryBuffer.getEntry(index)
            ?: return ActionResult.fail("Chat entry not found at index $index")
        val styleMatch = ChatStyleHelper.findStyleMatch(entry.raw, match)
            ?: return ActionResult.fail("Text not found in chat entry: $match")

        val data = ChatStyleHelper.styleToJson(styleMatch.style).apply {
            addProperty("matched", styleMatch.matchedText)
            addProperty("timestamp", entry.timestamp)
            addProperty("type", entry.type)
        }
        return ActionResult.ok("Chat style queried", data)
    }
}
