package com.blackboxpro.fabric.http
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.common.protocol.CommandMessage
import com.blackboxpro.common.protocol.ResponseMessage
import com.blackboxpro.fabric.dispatcher.CommandDispatcher
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ExecuteHandler : HttpHandler {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-Http")
    private val gson = Gson()
    private const val MAX_BODY_SIZE = 1024 * 1024 // 1MB

    override fun handle(exchange: HttpExchange) {
        val responseJson = buildResponse(exchange)
        sendHttpResponse(exchange, responseJson)
    }

    private fun buildResponse(exchange: HttpExchange): String {
        if (exchange.requestMethod != "POST") {
            return gson.toJson(ResponseMessage("", "failure", "Method not allowed, use POST"))
        }

        val body = try {
            val bytes = exchange.requestBody.readBytes()
            if (bytes.size > MAX_BODY_SIZE) {
                return gson.toJson(ResponseMessage("", "failure", "Request body too large: ${bytes.size} bytes"))
            }
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            return gson.toJson(ResponseMessage("", "failure", "Failed to read request body: ${e.message}"))
        }

        val command = try {
            gson.fromJson(body, CommandMessage::class.java)
                ?: return gson.toJson(ResponseMessage("", "failure", "Parse error: null command"))
        } catch (e: Exception) {
            return gson.toJson(ResponseMessage("", "failure", "Parse error: ${e.message}"))
        }

        val timeoutMs = RuntimeBlackBoxConfig.current.network.responseTimeoutMs
        val future = ResponseFutureRegistry.register(command.id)

        return try {
            CommandDispatcher.dispatch(command)
            try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                ResponseFutureRegistry.cancel(command.id)
                gson.toJson(ResponseMessage(command.id, "failure", "Timeout after ${timeoutMs}ms"))
            }
        } catch (e: Exception) {
            ResponseFutureRegistry.cancel(command.id)
            logger.error("ExecuteHandler dispatch error for action ${command.action}", e)
            gson.toJson(ResponseMessage(command.id, "failure", "Exception: ${e.message}"))
        }
    }

    private fun sendHttpResponse(exchange: HttpExchange, json: String) {
        try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            logger.error("Failed to write HTTP response", e)
        } finally {
            exchange.close()
        }
    }
}
