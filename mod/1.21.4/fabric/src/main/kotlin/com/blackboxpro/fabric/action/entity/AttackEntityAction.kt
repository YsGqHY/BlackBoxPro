package com.blackboxpro.fabric.action.entity

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

class AttackEntityAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val entityId = params.requireInt("entityId")
        val sneaking = params.getBooleanOrDefault("sneaking", false)

        val world = MinecraftClient.getInstance().world
            ?: return ActionResult.fail("World is not available")
        val entity = world.getEntityById(entityId)
            ?: return ActionResult.fail("Entity not found: $entityId")

        val packet = PlayerInteractEntityC2SPacket.attack(entity, sneaking)
        MinecraftClient.getInstance().networkHandler?.sendPacket(packet)
            ?: return ActionResult.fail("Network handler is not available")

        return ActionResult.ok("Attacked entity $entityId")
    }
}
