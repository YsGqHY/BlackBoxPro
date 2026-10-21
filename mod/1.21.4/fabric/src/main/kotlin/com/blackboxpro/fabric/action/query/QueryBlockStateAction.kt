package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType

/**
 * 查询指定坐标的方块状态。
 * Action ID: "query_block_state"
 */
class QueryBlockStateAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val world = client.world
            ?: return ActionResult.fail("World not available")

        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val pos = BlockPos(x, y, z)

        val state = world.getBlockState(pos)

        val data = JsonObject().apply {
            addProperty("blockId", Registries.BLOCK.getId(state.block).toString())
            addProperty("isAir", state.isAir)

            val properties = JsonObject()
            state.entries.forEach { (property, value) ->
                properties.addProperty(property.name, value.toString())
            }
            add("properties", properties)

            addProperty("lightLevel", world.getLightLevel(pos))
            addProperty("blockLight", world.getLightLevel(LightType.BLOCK, pos))
            addProperty("skyLight", world.getLightLevel(LightType.SKY, pos))

            val biome = world.getBiome(pos)
            addProperty("biome", biome.key.map { it.value.toString() }.orElse("unknown"))

            addProperty("hardness", state.getHardness(world, pos))
            addProperty("x", x)
            addProperty("y", y)
            addProperty("z", z)
        }

        return ActionResult.ok("Block state queried at ($x, $y, $z)", data)
    }
}
