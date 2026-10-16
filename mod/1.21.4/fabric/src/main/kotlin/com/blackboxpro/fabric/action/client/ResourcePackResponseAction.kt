package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket
import java.util.UUID

class ResourcePackResponseAction : ActionExecutor {

    companion object {
        private val STATUS_MAP = mapOf(
            "accepted" to ResourcePackStatusC2SPacket.Status.ACCEPTED,
            "declined" to ResourcePackStatusC2SPacket.Status.DECLINED,
            "successfully_loaded" to ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED,
            "failed_download" to ResourcePackStatusC2SPacket.Status.FAILED_DOWNLOAD,
            "failed_reload" to ResourcePackStatusC2SPacket.Status.FAILED_RELOAD,
            "discarded" to ResourcePackStatusC2SPacket.Status.DISCARDED,
            "invalid_url" to ResourcePackStatusC2SPacket.Status.INVALID_URL,
            "downloaded" to ResourcePackStatusC2SPacket.Status.DOWNLOADED,
        )
    }

    override fun execute(params: JsonObject): ActionResult {
        val uuidStr = params.requireString("uuid")
        val resultStr = params.requireString("result")

        val uuid = try {
            UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            return ActionResult.fail("Invalid UUID: $uuidStr")
        }

        val status = STATUS_MAP[resultStr.lowercase()]
            ?: return ActionResult.fail("Unknown result: $resultStr. Valid: ${STATUS_MAP.keys}")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(ResourcePackStatusC2SPacket(uuid, status))
        return ActionResult.ok("Sent resource pack response: $resultStr")
    }
}
