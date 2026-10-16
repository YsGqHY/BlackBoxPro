package com.blackboxpro.fabric.action.entity

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.HandUtil
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

class SwingArmAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val hand = HandUtil.fromString(params.getStringOrNull("hand") ?: "main_hand")

        val player = MinecraftClient.getInstance().player
            ?: return ActionResult.fail("Player not available")

        // swingHand 同时播放客户端动画 + 发送 HandSwingC2SPacket
        player.swingHand(hand)
        return ActionResult.ok("Swung arm with ${hand.name.lowercase()}")
    }
}
