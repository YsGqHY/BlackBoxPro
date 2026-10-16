package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket

class RenameItemAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val name = params.requireString("name")

        if (name.length > 50) {
            return ActionResult.fail("Name too long: ${name.length} > 50")
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(RenameItemC2SPacket(name))
        return ActionResult.ok("Renamed item to '$name'")
    }
}
