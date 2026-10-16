package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

class AttackAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val entityId = params.requireInt("entityId")

        val client = MinecraftClient.getInstance()
        val world = client.world
            ?: return ActionResult.fail("World not available")
        world.getEntityById(entityId)
            ?: return ActionResult.fail("Entity not found: $entityId")

        val lookAtEntity = ActionRegistry.find("look_at_entity")
            ?: return ActionResult.fail("look_at_entity action not registered")
        val lookResult = lookAtEntity.execute(JsonObject().apply {
            addProperty("entityId", entityId)
        })
        if (!lookResult.success) return ActionResult.fail("Failed to look at entity: ${lookResult.message}")

        val attackEntity = ActionRegistry.find("attack_entity")
            ?: return ActionResult.fail("attack_entity action not registered")
        val attackResult = attackEntity.execute(JsonObject().apply {
            addProperty("entityId", entityId)
        })
        if (!attackResult.success) return ActionResult.fail("Failed to attack entity: ${attackResult.message}")

        val swingArm = ActionRegistry.find("swing_arm")
            ?: return ActionResult.fail("swing_arm action not registered")
        val swingResult = swingArm.execute(JsonObject())
        if (!swingResult.success) return ActionResult.fail("Failed to swing arm: ${swingResult.message}")

        return ActionResult.ok("Attacked entity $entityId")
    }
}
