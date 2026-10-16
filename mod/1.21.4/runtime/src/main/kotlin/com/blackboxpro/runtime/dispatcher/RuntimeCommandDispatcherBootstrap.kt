package com.blackboxpro.runtime.dispatcher

import com.blackboxpro.common.protocol.CommandMessage
import com.blackboxpro.common.protocol.ResponseMessage
import com.blackboxpro.common.runtime.LoggerSupplier
import com.blackboxpro.common.runtime.action.ActionExecutor
import com.blackboxpro.common.runtime.dispatcher.RuntimeCommandDispatcher
import com.blackboxpro.common.runtime.dispatcher.RuntimeResponseSender
import com.google.gson.Gson
import com.google.gson.JsonObject

object RuntimeCommandDispatcherBootstrap {
    private val gson = Gson()

    fun bind(
        loggerSupplier: LoggerSupplier,
        executeOnMainThread: ((() -> Unit)) -> Unit,
        actionResolver: (String) -> ActionExecutor?,
        sendResponseJson: (String) -> Unit
    ) {
        RuntimeCommandDispatcher.bind(
            loggerSupplier = loggerSupplier,
            mainThreadExecutor = object : RuntimeCommandDispatcher.MainThreadExecutor {
                override fun execute(task: () -> Unit) {
                    executeOnMainThread(task)
                }
            },
            actionResolver = object : RuntimeCommandDispatcher.ActionResolver {
                override fun find(actionId: String): ActionExecutor? = actionResolver(actionId)
            }
        )

        RuntimeResponseSender.bind(object : RuntimeResponseSender.Sender {
            override fun sendResponse(id: String, status: String, message: String?, data: JsonObject?) {
                sendResponseJson(gson.toJson(ResponseMessage(id, status, message, data)))
            }
        })
    }

    fun tick() {
        RuntimeCommandDispatcher.tick()
    }

    fun clear(): Int = RuntimeCommandDispatcher.clear()

    fun dispatch(message: CommandMessage) {
        RuntimeCommandDispatcher.dispatch(message)
    }

    fun sendResponse(
        id: String,
        status: String,
        message: String? = null,
        data: JsonObject? = null
    ) {
        RuntimeCommandDispatcher.sendResponse(id, status, message, data)
    }
}
