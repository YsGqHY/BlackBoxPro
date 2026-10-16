package com.blackboxpro.fabric.action.movement
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.action.composite.TickScheduler
import com.blackboxpro.fabric.util.InjectedInput
import com.blackboxpro.fabric.util.getDoubleOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 模拟键盘驱动移动 + 设置 pitch。
 * yaw 自动朝向目标，pitch 由参数指定。
 */
class PlayerMoveLookAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireDouble("x")
        val y = params.requireDouble("y")
        val z = params.requireDouble("z")
        val pitch = params.requireDouble("pitch").toFloat().coerceIn(-90f, 90f)
        val speed = params.getDoubleOrDefault("speed", 1.0).coerceIn(0.1, 2.0)
        val timeout = params.getIntOrDefault("timeout", 200)

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val pathConfig = RuntimeBlackBoxConfig.current.pathfinding
        val dx = x - player.x
        val dy = y - player.y
        val dz = z - player.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distance > pathConfig.maxDistance) {
            return ActionResult.fail("Target too far: ${"%.1f".format(distance)} > ${pathConfig.maxDistance}")
        }

        if (distance < pathConfig.arrivalThreshold) {
            return ActionResult.ok("Already at target")
        }

        startMovement(x, y, z, pitch, speed, timeout)
        return ActionResult.ok("Moving to (${"%.1f".format(x)}, ${"%.1f".format(y)}, ${"%.1f".format(z)}), distance=${"%.1f".format(distance)}")
    }

    private fun startMovement(targetX: Double, targetY: Double, targetZ: Double, pitch: Float, speed: Double, timeout: Int) {
        val injected = InjectedInput()
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        injected.install(player)

        var ticksElapsed = 0

        fun tick() {
            val p = MinecraftClient.getInstance().player
            if (p == null) {
                injected.reset()
                injected.uninstall()
                return
            }
            if (p.input !== injected) {
                injected.reset()
                return
            }
            val threshold = RuntimeBlackBoxConfig.current.pathfinding.arrivalThreshold

            ticksElapsed++

            val dx = targetX - p.x
            val dy = targetY - p.y
            val dz = targetZ - p.z
            val totalDist = sqrt(dx * dx + dy * dy + dz * dz)

            if (totalDist < threshold || ticksElapsed >= timeout) {
                injected.reset()
                injected.uninstall(p)
                p.isSprinting = false
                return
            }

            // yaw 朝向目标，pitch 由参数指定
            val yaw = (-atan2(dx, dz) * 180.0 / Math.PI).toFloat()
            p.yaw = yaw
            p.pitch = pitch

            // 注入输入
            injected.forward = true
            injected.jumping = dy > 0.5 && p.isOnGround
            injected.sprinting = speed > 1.0
            p.isSprinting = speed > 1.0

            TickScheduler.schedule(1) { tick() }
        }

        TickScheduler.schedule(1) { tick() }
    }
}
