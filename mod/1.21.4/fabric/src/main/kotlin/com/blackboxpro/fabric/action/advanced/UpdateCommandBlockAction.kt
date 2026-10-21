package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.block.entity.CommandBlockBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket
import net.minecraft.util.math.BlockPos

class UpdateCommandBlockAction : ActionExecutor {

    companion object {
        private val TYPE_MAP = mapOf(
            0 to CommandBlockBlockEntity.Type.SEQUENCE,
            1 to CommandBlockBlockEntity.Type.AUTO,
            2 to CommandBlockBlockEntity.Type.REDSTONE
        )
    }

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val command = params.requireString("command")
        val mode = params.getIntOrDefault("mode", 2)
        val trackOutput = params.getBooleanOrDefault("trackOutput", true)
        val conditional = params.getBooleanOrDefault("conditional", false)
        val alwaysActive = params.getBooleanOrDefault("alwaysActive", false)

        val type = TYPE_MAP[mode]
            ?: return ActionResult.fail("Invalid command block mode: $mode (valid: 0=sequence, 1=auto, 2=redstone)")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            UpdateCommandBlockC2SPacket(
                BlockPos(x, y, z),
                command,
                type,
                trackOutput,
                conditional,
                alwaysActive
            )
        )
        return ActionResult.ok("Updated command block at $x, $y, $z")
    }
}
