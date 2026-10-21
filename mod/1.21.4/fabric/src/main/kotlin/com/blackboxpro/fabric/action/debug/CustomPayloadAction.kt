package com.blackboxpro.fabric.action.debug

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.Base64

class CustomPayloadAction : ActionExecutor {

    companion object {
        private val logger = LoggerFactory.getLogger("BlackBoxPro-CustomPayload")

        /**
         * 缓存已成功注册的 channel，避免重复注册。
         * 所有调用限定在主线程（由 CommandDispatcher 保证），无需 ConcurrentHashMap。
         */
        private val registeredChannels = HashMap<String, CustomPayload.Id<DynamicPayload>>()
    }

    /**
     * 通用动态 Payload，携带任意二进制数据。
     */
    private class DynamicPayload(
        val channelId: CustomPayload.Id<DynamicPayload>,
        val data: ByteArray
    ) : CustomPayload {
        override fun getId(): CustomPayload.Id<DynamicPayload> = channelId
    }

    override fun execute(params: JsonObject): ActionResult {
        val channel = params.requireString("channel")
        val dataBase64 = params.requireString("data")

        val client = MinecraftClient.getInstance()
        val handler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val data = try {
            Base64.getDecoder().decode(dataBase64)
        } catch (e: IllegalArgumentException) {
            return ActionResult.fail("Invalid base64 data: ${e.message}")
        }

        // 校验 payload 大小
        val maxSize = 32768
        if (data.size > maxSize) {
            return ActionResult.fail("Payload too large: ${data.size} > $maxSize")
        }

        val identifier = try {
            Identifier.of(channel)
        } catch (e: Exception) {
            return ActionResult.fail("Invalid channel identifier: $channel")
        }

        // 动态注册 payload type（如果尚未注册）
        val payloadId = registeredChannels.getOrPut(channel) {
            val id = CustomPayload.Id<DynamicPayload>(identifier)
            try {
                PayloadTypeRegistry.playC2S().register(id, createCodec(id))
                logger.debug("Dynamically registered C2S payload type: {}", channel)
                id
            } catch (e: Exception) {
                logger.warn("Failed to register payload type {}: {}", channel, e.message)
                return ActionResult.fail("Failed to register payload type for channel: $channel")
            }
        }

        val payload = DynamicPayload(payloadId, data)
        handler.sendPacket(CustomPayloadC2SPacket(payload))

        return ActionResult.ok("Sent custom payload to $channel (${data.size} bytes)")
    }

    private fun createCodec(id: CustomPayload.Id<DynamicPayload>): PacketCodec<PacketByteBuf, DynamicPayload> =
        PacketCodec.of(
            { payload, buf ->
                buf.writeBytes(Unpooled.wrappedBuffer(payload.data))
            },
            { buf ->
                val bytes = ByteArray(buf.readableBytes())
                buf.readBytes(bytes)
                DynamicPayload(id, bytes)
            }
        )
}
