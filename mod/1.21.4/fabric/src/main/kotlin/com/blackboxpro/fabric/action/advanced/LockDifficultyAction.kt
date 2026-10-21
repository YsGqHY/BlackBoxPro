package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireBoolean
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateDifficultyLockC2SPacket

class LockDifficultyAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val locked = params.requireBoolean("locked")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(UpdateDifficultyLockC2SPacket(locked))
        return ActionResult.ok("Difficulty lock set to $locked")
    }
}
