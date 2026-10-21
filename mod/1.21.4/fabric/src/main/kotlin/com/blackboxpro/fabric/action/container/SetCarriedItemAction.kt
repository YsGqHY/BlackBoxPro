package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket

class SetCarriedItemAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val slot = params.requireInt("slot")

        if (slot !in 0..8) {
            return ActionResult.fail("Slot must be between 0 and 8, got: $slot")
        }

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")
        val player = MinecraftClient.getInstance().player

        player?.inventory?.selectedSlot = slot
        handler.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        return ActionResult.ok("Selected hotbar slot $slot")
    }
}
