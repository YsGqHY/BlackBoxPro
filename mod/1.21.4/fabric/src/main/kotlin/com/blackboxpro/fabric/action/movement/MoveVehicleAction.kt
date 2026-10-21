package com.blackboxpro.fabric.action.movement

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireDouble
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket
import net.minecraft.util.math.Vec3d

class MoveVehicleAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireDouble("x")
        val y = params.requireDouble("y")
        val z = params.requireDouble("z")
        val yaw = params.requireDouble("yaw").toFloat()
        val pitch = params.requireDouble("pitch").toFloat()
        val onGround = params.getBooleanOrDefault("onGround", true)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(VehicleMoveC2SPacket(Vec3d(x, y, z), yaw, pitch, onGround))
        return ActionResult.ok()
    }
}
