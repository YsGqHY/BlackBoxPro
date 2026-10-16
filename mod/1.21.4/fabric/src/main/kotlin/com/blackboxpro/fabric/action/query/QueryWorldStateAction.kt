package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

/**
 * 查询世界全局状态。
 * Action ID: "query_world_state"
 */
class QueryWorldStateAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val world = client.world
            ?: return ActionResult.fail("World not available")

        val data = JsonObject().apply {
            addProperty("timeOfDay", world.timeOfDay)
            addProperty("worldTime", world.time)
            addProperty("raining", world.isRaining)
            addProperty("thundering", world.isThundering)
            addProperty("dimension", world.registryKey.value.toString())
            addProperty("hasSkyLight", world.dimension.hasSkyLight())
            addProperty("hasCeiling", world.dimension.hasCeiling())
            addProperty("difficulty", world.difficulty.getName())
            addProperty("seaLevel", world.seaLevel)
        }

        return ActionResult.ok("World state queried", data)
    }
}
