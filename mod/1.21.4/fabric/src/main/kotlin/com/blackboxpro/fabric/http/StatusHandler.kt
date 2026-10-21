package com.blackboxpro.fabric.http

import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig
import com.blackboxpro.fabric.BlackBoxProFabric
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.slf4j.LoggerFactory

object StatusHandler : HttpHandler {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-Http")

    override fun handle(exchange: HttpExchange) {
        try {
            val client = net.minecraft.client.MinecraftClient.getInstance()
            val obj = JsonObject().apply {
                addProperty("status", "running")
                addProperty("version", BlackBoxProFabric.VERSION)
                addProperty("platform", "fabric")
                addProperty("httpPort", RuntimeBlackBoxConfig.current.network.httpPort)
                addProperty("actions", ActionRegistry.size())
                addProperty("ready", client.player != null && client.world != null)
            }
            val bytes = obj.toString().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            logger.error("StatusHandler error", e)
        } finally {
            exchange.close()
        }
    }
}
