package com.blackboxpro.fabric.dispatcher

import com.blackboxpro.fabric.http.ResponseFutureRegistry
import com.blackboxpro.runtime.bindings.FabricBindings
import com.blackboxpro.runtime.bindings.SharedSlf4jLoggerSupplier
import com.blackboxpro.runtime.dispatcher.RuntimeCommandDispatcherBootstrap
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient

object CommandDispatcher {

    private val logger = SharedSlf4jLoggerSupplier.getLogger("BlackBoxPro-Dispatcher")

    fun init() {
        RuntimeCommandDispatcherBootstrap.bind(
            loggerSupplier = FabricBindings,
            executeOnMainThread = { task -> MinecraftClient.getInstance().execute(task) },
            actionResolver = ActionRegistry::find,
            sendResponseJson = ResponseFutureRegistry::onResponseJson
        )

        ClientTickEvents.END_CLIENT_TICK.register {
            RuntimeCommandDispatcherBootstrap.tick()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            val count = RuntimeCommandDispatcherBootstrap.clear()
            if (count > 0) {
                logger.info("Cleared {} delayed commands on disconnect", count)
            }
        }
    }

    fun dispatch(message: CommandMessage) {
        RuntimeCommandDispatcherBootstrap.dispatch(message)
    }

    /** 供异步 Action 自行发送响应，不要在普通 Action 中调用 */
    internal fun sendResponse(
        id: String,
        status: String,
        message: String? = null,
        data: JsonObject? = null
    ) {
        RuntimeCommandDispatcherBootstrap.sendResponse(id, status, message, data)
    }
}
