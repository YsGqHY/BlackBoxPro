package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

open class PlayerCommandAction(
    private val mode: ClientCommandC2SPacket.Mode,
    private val description: String
) : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val hasExplicitEntity = params.has("entityId")
        val entityId = params.getIntOrDefault("entityId", player.id)
        val entity = if (hasExplicitEntity) {
            client.world?.getEntityById(entityId)
                ?: return ActionResult.fail("Entity not found: $entityId")
        } else {
            player
        }

        // jumpBoost 只对 START_HORSE_JUMP 有意义，其他 mode 传 0
        val jumpBoost = if (mode == ClientCommandC2SPacket.Mode.START_RIDING_JUMP) {
            params.getIntOrDefault("jumpBoost", 100)
        } else {
            0
        }

        networkHandler.sendPacket(ClientCommandC2SPacket(entity, mode, jumpBoost))
        return ActionResult.ok(description)
    }
}
