package com.blackboxpro.fabric.action.player

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class FinishUsingAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val sequence = params.getIntOrDefault("sequence", 0)
        val client = MinecraftClient.getInstance()
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        // 同步客户端状态：释放正在使用的物品（弓箭蓄力等）
        player.stopUsingItem()

        networkHandler.sendPacket(
            PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, sequence)
        )
        return ActionResult.ok("Finished using item")
    }
}
