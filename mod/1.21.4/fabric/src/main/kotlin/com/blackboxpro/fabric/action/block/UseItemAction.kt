package com.blackboxpro.fabric.action.block

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.HandUtil
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult

class UseItemAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val hand = HandUtil.fromString(params.getStringOrNull("hand") ?: "main_hand")

        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")
        val interactionManager = client.interactionManager
            ?: return ActionResult.fail("InteractionManager not available")

        // 调用原版客户端交互 API，完整模拟右键使用物品：
        // 1. 如果对准方块，先触发 interactBlock（发送 InteractBlockPacket → 服务端 PlayerInteractEvent）
        // 2. 仅当 interactBlock 未消费交互时，才 fallback 到 interactItem（与原版客户端行为一致）
        val hitResult = client.crosshairTarget
        var consumed = false
        if (hitResult is BlockHitResult && hitResult.type != HitResult.Type.MISS) {
            val result = interactionManager.interactBlock(player, hand, hitResult)
            consumed = result.isAccepted
        }
        if (!consumed) {
            interactionManager.interactItem(player, hand)
        }

        return ActionResult.ok("Used item")
    }
}
