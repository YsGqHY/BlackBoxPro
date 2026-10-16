package com.blackboxpro.fabric.action.entity

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireDouble
import com.blackboxpro.fabric.util.requireInt
import com.blackboxpro.fabric.util.HandUtil
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.math.Vec3d

class InteractEntityAtAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val entityId = params.requireInt("entityId")
        val targetX = params.requireDouble("targetX")
        val targetY = params.requireDouble("targetY")
        val targetZ = params.requireDouble("targetZ")
        val hand = HandUtil.fromString(params.getStringOrNull("hand") ?: "main_hand")
        val sneaking = params.getBooleanOrDefault("sneaking", false)

        val world = MinecraftClient.getInstance().world
            ?: return ActionResult.fail("World not available")
        val entity = world.getEntityById(entityId)
            ?: return ActionResult.fail("Entity not found: $entityId")

        val target = Vec3d(targetX, targetY, targetZ)
        val packet = PlayerInteractEntityC2SPacket.interactAt(entity, sneaking, hand, target)
        MinecraftClient.getInstance().networkHandler?.sendPacket(packet)
            ?: return ActionResult.fail("Not connected to server")

        return ActionResult.ok("Interacted with entity $entityId at ($targetX, $targetY, $targetZ)")
    }
}
