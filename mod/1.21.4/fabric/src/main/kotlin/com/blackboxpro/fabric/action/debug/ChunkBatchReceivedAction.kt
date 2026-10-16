package com.blackboxpro.fabric.action.debug

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getFloatOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket

class ChunkBatchReceivedAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val desiredChunksPerTick = params.getFloatOrDefault("desiredChunksPerTick", 7.0f)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(AcknowledgeChunksC2SPacket(desiredChunksPerTick))
        return ActionResult.ok("Acknowledged chunk batch (desiredChunksPerTick=$desiredChunksPerTick)")
    }
}
