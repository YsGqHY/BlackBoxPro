package com.blackboxpro.fabric.action.container

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket

class BundleSelectedSlotAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val slotId = params.requireInt("slotId")
        val selectedIndex = params.requireInt("selectedIndex")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(BundleItemSelectedC2SPacket(slotId, selectedIndex))
        return ActionResult.ok("Selected bundle item index=$selectedIndex in slot $slotId")
    }
}
