package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket

class CloseContainerAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        // windowId 可选，默认使用当前打开的容器
        val windowId = params.getIntOrDefault("windowId", player.currentScreenHandler.syncId)

        val handler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(CloseHandledScreenC2SPacket(windowId))

        // 重置客户端容器状态（恢复到 playerScreenHandler）并关闭 GUI
        player.closeHandledScreen()
        client.setScreen(null)

        return ActionResult.ok("Closed container window $windowId")
    }
}
