package com.blackboxpro.fabric.action.debug

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.DebugSampleSubscriptionC2SPacket
import net.minecraft.util.profiler.log.DebugSampleType

/**
 * Action ID: "debug_sample_subscription"
 *
 * 1.21.4 的 debug 采样订阅仍是 1.21.1 形态（非 1.21.11 的 world.debug 体系）：
 * `DebugSampleSubscriptionC2SPacket(DebugSampleType)` + `DebugSampleType.TICK_TIME`。
 * 1.21.11 的 `world.debug.*` / `DebugSubscriptionRequestC2SPacket` 在本版不存在，
 * 但那只是该体系在更高版本的重构，本版功能并未缺失。
 * （已用 javap 对照构建实际使用的 remapped 1.21.4 jar 验证：构造器、枚举、
 * ServerPlayPacketListener.onDebugSampleSubscription 均存在。）
 */
class DebugSampleAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val typeStr = params.requireString("type").lowercase()
        if (typeStr != "tick_time" && typeStr != "time") {
            return ActionResult.fail("Unknown debug subscription type: $typeStr (valid: tick_time)")
        }

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(DebugSampleSubscriptionC2SPacket(DebugSampleType.TICK_TIME))
        return ActionResult.ok("Subscribed to debug sample: tick_time")
    }
}
