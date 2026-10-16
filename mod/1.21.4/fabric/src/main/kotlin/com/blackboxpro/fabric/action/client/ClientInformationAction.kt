package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getIntOrDefault
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.message.ChatVisibility
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket
import net.minecraft.network.packet.c2s.common.SyncedClientOptions
import net.minecraft.particle.ParticlesMode

class ClientInformationAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val locale = params.getStringOrNull("locale") ?: "en_us"
        val viewDistance = params.getIntOrDefault("viewDistance", 12)
        val chatMode = params.getIntOrDefault("chatMode", 0)
        val chatColors = params.getBooleanOrDefault("chatColors", true)
        val skinParts = params.getIntOrDefault("skinParts", 127)
        val mainHand = params.getIntOrDefault("mainHand", 1)
        val textFiltering = params.getBooleanOrDefault("textFiltering", false)
        val allowServerListings = params.getBooleanOrDefault("allowServerListings", true)

        val client = MinecraftClient.getInstance()
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val arm = when (mainHand) {
            0 -> net.minecraft.util.Arm.LEFT
            1 -> net.minecraft.util.Arm.RIGHT
            else -> return ActionResult.fail("Invalid mainHand: $mainHand (expected 0=left, 1=right)")
        }

        val chatVisibility = when (chatMode) {
            0 -> ChatVisibility.FULL
            1 -> ChatVisibility.SYSTEM
            2 -> ChatVisibility.HIDDEN
            else -> return ActionResult.fail("Invalid chatMode: $chatMode (expected 0-2)")
        }

        val syncedOptions = SyncedClientOptions(
            locale,
            viewDistance,
            chatVisibility,
            chatColors,
            skinParts,
            arm,
            textFiltering,
            allowServerListings,
            ParticlesMode.ALL
        )
        networkHandler.sendPacket(ClientOptionsC2SPacket(syncedOptions))

        return ActionResult.ok("Client information sent")
    }
}
