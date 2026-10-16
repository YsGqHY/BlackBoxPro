package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getDoubleOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.registry.Registries
import net.minecraft.util.math.Box
import kotlin.math.sqrt

/**
 * 获取附近实体列表。
 * Action ID: "query_nearby_entities"
 */
class QueryNearbyEntitiesAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val world = client.world
            ?: return ActionResult.fail("World not available")

        val radius = params.getDoubleOrDefault("radius", 10.0).coerceIn(0.1, 64.0)
        val typeFilter = params.getStringOrNull("type")
        val limit = params.getIntOrDefault("limit", 20).coerceIn(1, 100)

        val box = player.boundingBox.expand(radius)
        var entities = world.getOtherEntities(player, box)

        if (typeFilter != null) {
            entities = entities.filter {
                Registries.ENTITY_TYPE.getId(it.type).toString() == typeFilter
            }
        }

        val sorted = entities
            .map { it to it.squaredDistanceTo(player) }
            .sortedBy { it.second }
            .take(limit)

        val arr = JsonArray()
        sorted.forEach { (entity, distSq) ->
            arr.add(JsonObject().apply {
                addProperty("entityId", entity.id)
                addProperty("uuid", entity.uuidAsString)
                addProperty("type", Registries.ENTITY_TYPE.getId(entity.type).toString())
                addProperty("name", entity.name.string)
                addProperty("x", entity.x)
                addProperty("y", entity.y)
                addProperty("z", entity.z)
                addProperty("distance", sqrt(distSq))
                if (entity is LivingEntity) {
                    addProperty("health", entity.health)
                    addProperty("maxHealth", entity.maxHealth)
                }
            })
        }

        val data = JsonObject().apply {
            add("entities", arr)
            addProperty("count", arr.size())
        }
        return ActionResult.ok("Found ${arr.size()} entities", data)
    }
}
