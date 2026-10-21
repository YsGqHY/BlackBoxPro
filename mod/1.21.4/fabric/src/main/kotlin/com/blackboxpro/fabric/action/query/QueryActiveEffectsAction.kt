package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries

/**
 * 读取玩家当前药水效果列表。
 * Action ID: "query_active_effects"
 */
class QueryActiveEffectsAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val arr = JsonArray()
        player.statusEffects.forEach { instance: StatusEffectInstance ->
            arr.add(JsonObject().apply {
                addProperty("id", Registries.STATUS_EFFECT.getId(instance.effectType.value())?.toString() ?: "unknown")
                addProperty("amplifier", instance.amplifier)
                addProperty("duration", instance.duration)
                addProperty("ambient", instance.isAmbient)
                addProperty("visible", instance.shouldShowParticles())
            })
        }

        val data = JsonObject().apply { add("effects", arr) }
        return ActionResult.ok("Active effects queried", data)
    }
}
