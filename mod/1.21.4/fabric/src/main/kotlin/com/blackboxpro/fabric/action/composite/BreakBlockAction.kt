package com.blackboxpro.fabric.action.composite
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import kotlin.math.ceil

class BreakBlockAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")

        val client = MinecraftClient.getInstance()
        client.player ?: return ActionResult.fail("Player not available")

        // 检查方块是否可破坏
        val breakTicks = calculateBreakTicks(client, x, y, z)
        if (breakTicks < 0) {
            return ActionResult.fail("Block at ($x, $y, $z) is unbreakable")
        }

        // Step 1: 看向方块中心
        val lookAt = ActionRegistry.find("look_at")
            ?: return ActionResult.fail("look_at action not registered")
        val lookResult = lookAt.execute(JsonObject().apply {
            addProperty("x", x + 0.5)
            addProperty("y", y + 0.5)
            addProperty("z", z + 0.5)
        })
        if (!lookResult.success) {
            return ActionResult.fail("Failed to look at block: ${lookResult.message}")
        }

        // Step 2: 开始挖掘
        val digStart = ActionRegistry.find("dig_start")
            ?: return ActionResult.fail("dig_start action not registered")
        val digResult = digStart.execute(JsonObject().apply {
            addProperty("x", x)
            addProperty("y", y)
            addProperty("z", z)
            addProperty("face", "top")
            addProperty("sequence", 0)
        })
        if (!digResult.success) {
            return ActionResult.fail("Failed to start digging: ${digResult.message}")
        }

        // Step 3: 延迟后完成挖掘
        TickScheduler.schedule(breakTicks) {
            // 回调时检查方块是否仍然存在
            val world = MinecraftClient.getInstance().world ?: return@schedule
            val pos = BlockPos(x, y, z)
            if (world.getBlockState(pos).isAir) return@schedule

            val digFinish = ActionRegistry.find("dig_finish") ?: return@schedule
            digFinish.execute(JsonObject().apply {
                addProperty("x", x)
                addProperty("y", y)
                addProperty("z", z)
                addProperty("face", "top")
                addProperty("sequence", 1)
            })
        }

        return ActionResult.ok("Breaking block at ($x, $y, $z), estimated $breakTicks ticks")
    }

    /**
     * 计算挖掘方块所需的 tick 数。
     * @return tick 数，-1 表示不可破坏
     */
    private fun calculateBreakTicks(client: MinecraftClient, x: Int, y: Int, z: Int): Int {
        val defaultTicks = RuntimeBlackBoxConfig.current.execution.defaultBreakTicks
        val world = client.world ?: return defaultTicks
        val player = client.player ?: return defaultTicks
        val pos = BlockPos(x, y, z)
        val state = world.getBlockState(pos)

        val hardness = state.getHardness(world, pos)
        if (hardness < 0) return -1 // 不可破坏
        if (hardness == 0f) return 1

        val speed = player.getBlockBreakingSpeed(state)
        val canHarvest = player.canHarvest(state)
        val divisor = if (canHarvest) 30.0f else 100.0f
        return if (speed > 0) {
            ceil(hardness * divisor / speed).toInt().coerceAtLeast(1)
        } else {
            defaultTicks
        }
    }
}
