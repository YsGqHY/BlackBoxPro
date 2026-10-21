package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireString
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.AdvancementTabC2SPacket
import net.minecraft.util.Identifier

class AdvancementTabAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val actionStr = params.requireString("action")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        when (actionStr.lowercase()) {
            "open" -> {
                val tabId = params.getStringOrNull("tabId")
                    ?: return ActionResult.fail("Missing required field: tabId (for open action)")
                val identifier = Identifier.tryParse(tabId)
                    ?: return ActionResult.fail("Invalid identifier: $tabId")
                val advancement = networkHandler.advancementHandler.get(identifier)
                    ?: return ActionResult.fail("Advancement not found: $tabId")
                networkHandler.sendPacket(AdvancementTabC2SPacket.open(advancement))
                return ActionResult.ok("Opened advancement tab: $tabId")
            }
            "close" -> {
                networkHandler.sendPacket(AdvancementTabC2SPacket.close())
                return ActionResult.ok("Closed advancement tab")
            }
            else -> return ActionResult.fail("Unknown action: $actionStr (valid: open, close)")
        }
    }
}
