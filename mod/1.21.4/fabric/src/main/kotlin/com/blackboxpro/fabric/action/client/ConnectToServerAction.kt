package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.blackboxpro.runtime.action.AsyncClientActionSupport
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo

class ConnectToServerAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult =
        ActionResult.fail("ConnectToServerAction requires commandId, use execute(params, commandId)")

    override fun execute(params: JsonObject, commandId: String): ActionResult {
        val ip = params.requireString("ip")
        val port = params.get("port")?.asInt ?: 25565
        val address = "$ip:$port"

        val client = MinecraftClient.getInstance()
        if (client.world != null) {
            return ActionResult.fail("Already in a world, disconnect first")
        }

        return AsyncClientActionSupport.startPolling(
            commandId = commandId,
            executeOnMainThread = { task -> client.execute(task) },
            timeoutMs = 30_000L,
            timeoutMessage = "Timed out connecting to $address",
            startAction = {
                client.execute {
                    val serverInfo = ServerInfo("BlackBoxPro", address, ServerInfo.ServerType.OTHER)
                    ConnectScreen.connect(client.currentScreen ?: TitleScreen(), client, ServerAddress.parse(address), serverInfo, false, null)
                }
            },
            poll = {
                if (client.world != null && client.player != null) {
                    ActionResult.ok("Connecting to $address")
                } else {
                    null
                }
            }
        )
    }
}
