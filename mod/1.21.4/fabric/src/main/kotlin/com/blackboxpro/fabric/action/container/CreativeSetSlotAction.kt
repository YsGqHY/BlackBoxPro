package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket

/**
 * 创造模式设置槽位。当前实现仅支持清空槽位（设置为 EMPTY）。
 * 完整的物品设置需要 ItemStack 的 NBT 反序列化支持。
 */
class CreativeSetSlotAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val slot = params.requireInt("slot")

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(CreativeInventoryActionC2SPacket(slot, ItemStack.EMPTY))
        return ActionResult.ok("Set creative slot $slot to empty")
    }
}
