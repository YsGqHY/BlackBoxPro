package com.blackboxpro.fabric.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 聊天消息环形缓冲区。
 * 通过 Fabric ClientReceiveMessageEvents 捕获消息，供 query_chat_history 查询。
 */
object ChatHistoryBuffer {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-ChatHistory")
    private const val MAX_SIZE = 200

    data class ChatEntry(
        val timestamp: Long,
        val raw: String,
        val plain: String,
        val type: String
    )

    private val buffer = ConcurrentLinkedDeque<ChatEntry>()

    fun addMessage(message: Text, type: String) {
        val raw = serializeMessage(message)
        val plain = message.string
        buffer.addLast(ChatEntry(System.currentTimeMillis(), raw, plain, type))
        while (buffer.size > MAX_SIZE) buffer.pollFirst()
    }

    fun query(count: Int, filter: String?, since: Long?): JsonObject {
        var stream = buffer.toList().asSequence()
        if (since != null) stream = stream.filter { it.timestamp >= since }
        if (filter != null) stream = stream.filter { it.plain.contains(filter) }
        val messages = stream.toList().takeLast(count.coerceIn(1, 100))

        val arr = JsonArray()
        messages.forEach { entry ->
            arr.add(JsonObject().apply {
                addProperty("timestamp", entry.timestamp)
                addProperty("raw", entry.raw)
                addProperty("plain", entry.plain)
                addProperty("type", entry.type)
            })
        }
        return JsonObject().apply {
            add("messages", arr)
            addProperty("total", buffer.size)
        }
    }

    fun getEntry(index: Int): ChatEntry? {
        if (index < 0) return null
        val entries = buffer.toList()
        val targetIndex = entries.lastIndex - index
        return entries.getOrNull(targetIndex)
    }

    fun clear() = buffer.clear()

    private fun serializeMessage(message: Text): String = runCatching {
        val client = MinecraftClient.getInstance()
        val ops = client.world?.registryManager?.getOps(JsonOps.INSTANCE) ?: JsonOps.INSTANCE
        TextCodecs.CODEC.encodeStart(ops, message).result().orElse(JsonPrimitive(message.string)).toString()
    }.getOrElse {
        JsonPrimitive(message.string).toString()
    }
}
