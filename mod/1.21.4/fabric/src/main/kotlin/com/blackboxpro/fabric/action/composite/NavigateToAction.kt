package com.blackboxpro.fabric.action.composite
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getDoubleOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.pathfinding.NavigationController
import com.blackboxpro.fabric.util.pathfinding.Pathfinder
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * AI 寻路移动到目标坐标。
 * Action ID: "navigate_to"
 */
class NavigateToAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val targetX = params.requireDouble("x")
        val targetY = params.requireDouble("y")
        val targetZ = params.requireDouble("z")
        val speed = params.getDoubleOrDefault("speed", 1.0).coerceIn(0.1, 2.0)
        val navConfig = RuntimeBlackBoxConfig.current.navigation
        val timeout = params.getIntOrDefault("timeout", navConfig.defaultTimeout)
        val allowJump = params.getBooleanOrDefault("allowJump", true)

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val world = client.world
            ?: return ActionResult.fail("World not available")

        val dx = targetX - player.x
        val dy = targetY - player.y
        val dz = targetZ - player.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distance > navConfig.maxDistance) {
            return ActionResult.fail("Target too far: ${"%.1f".format(distance)} > ${navConfig.maxDistance}")
        }

        if (distance < navConfig.arrivalThreshold) {
            return ActionResult.ok("Already at target")
        }

        val start = BlockPos(player.blockX, player.blockY, player.blockZ)
        val goal = BlockPos(floor(targetX).toInt(), floor(targetY).toInt(), floor(targetZ).toInt())

        // 如果不允许跳跃，使用 jumpCost = 极大值 来禁止跳跃路径
        val config = if (allowJump) navConfig else navConfig.copy(jumpCost = 999.0)

        val path = Pathfinder.findPath(start, goal, world, config)
        if (path.isEmpty()) {
            return ActionResult.fail("No path found to ($targetX, $targetY, $targetZ)")
        }

        val controller = NavigationController(path, speed, timeout, navConfig)
        controller.start()

        val estimatedTicks = (path.size / (navConfig.stepSize * speed)).toInt()
        val data = JsonObject().apply {
            addProperty("pathLength", path.size)
            addProperty("estimatedTicks", estimatedTicks)
            addProperty("startX", player.x)
            addProperty("startY", player.y)
            addProperty("startZ", player.z)
        }

        return ActionResult.ok("Navigating to ($targetX, $targetY, $targetZ), path length=${path.size}", data)
    }
}
