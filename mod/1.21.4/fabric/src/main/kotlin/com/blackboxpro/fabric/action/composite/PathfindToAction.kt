package com.blackboxpro.fabric.action.composite
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getDoubleOrDefault
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import kotlin.math.sqrt

/**
 * 直线传送式移动（绕过物理引擎）。
 * 注意：此 Action 直接 setPosition + sendPacket，不经过碰撞/重力检测。
 * 推荐使用 navigate_to（A* 寻路 + InjectedInput 物理引擎驱动）替代。
 */
class PathfindToAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val targetX = params.requireDouble("x")
        val targetY = params.requireDouble("y")
        val targetZ = params.requireDouble("z")
        val speed = params.getDoubleOrDefault("speed", 1.0)

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val pathConfig = RuntimeBlackBoxConfig.current.pathfinding
        val dx = targetX - player.x
        val dy = targetY - player.y
        val dz = targetZ - player.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distance > pathConfig.maxDistance) {
            return ActionResult.fail("Target too far: ${"%.1f".format(distance)} > ${pathConfig.maxDistance}")
        }

        if (distance < pathConfig.arrivalThreshold) {
            return ActionResult.ok("Already at target")
        }

        // 计算最大步数：距离 / 步长 * 2 作为安全上限
        val stepSize = pathConfig.stepSize * speed
        val maxSteps = ((distance / stepSize) * 2).toInt().coerceAtLeast(10)

        moveStep(targetX, targetY, targetZ, speed, maxSteps)

        return ActionResult.ok("Pathfinding to ($targetX, $targetY, $targetZ), distance=${"%.1f".format(distance)}")
    }

    private fun moveStep(targetX: Double, targetY: Double, targetZ: Double, speed: Double, remainingSteps: Int) {
        if (remainingSteps <= 0) return

        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val networkHandler = client.networkHandler ?: return

        val pathConfig = RuntimeBlackBoxConfig.current.pathfinding
        val stepSize = pathConfig.stepSize * speed

        val dx = targetX - player.x
        val dy = targetY - player.y
        val dz = targetZ - player.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distance < pathConfig.arrivalThreshold) return

        // 防止越过目标点
        val ratio = (stepSize / distance).coerceAtMost(1.0)
        val newX = player.x + dx * ratio
        val newY = player.y + dy * ratio
        val newZ = player.z + dz * ratio

        player.setPosition(newX, newY, newZ)
        networkHandler.sendPacket(
            PlayerMoveC2SPacket.PositionAndOnGround(newX, newY, newZ, player.isOnGround, false)
        )

        TickScheduler.schedule(1) {
            moveStep(targetX, targetY, targetZ, speed, remainingSteps - 1)
        }
    }
}
