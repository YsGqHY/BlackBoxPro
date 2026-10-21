package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ChatHistoryBuffer
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.getLongOrDefault
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonObject

/**
 * 读取聊天消息历史。
 * Action ID: "query_chat_history"
 */
class QueryChatHistoryAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val count = params.getIntOrDefault("count", 10)
        val filter = params.getStringOrNull("filter")
        val since = if (params.has("since")) params.getLongOrDefault("since", 0L) else null

        val data = ChatHistoryBuffer.query(count, filter, since)
        return ActionResult.ok("Chat history queried", data)
    }
}
