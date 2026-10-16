package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.runtime.action.AsyncClientActionSupport
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen

class LeaveWorldAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult =
        ActionResult.fail("LeaveWorldAction requires commandId, use execute(params, commandId)")

    override fun execute(params: JsonObject, commandId: String): ActionResult {
        val client = MinecraftClient.getInstance()
        if (client.world == null && client.player == null && client.currentScreen is TitleScreen) {
            return ActionResult.ok(
                "Already at title screen",
                JsonObject().apply { addProperty("state", "title_screen") }
            )
        }

        return AsyncClientActionSupport.startPolling(
            commandId = commandId,
            executeOnMainThread = { task -> client.execute(task) },
            timeoutMs = 60_000L,
            timeoutMessage = "Timed out waiting to leave the current world",
            startAction = {
                if (client.world != null) {
                    client.networkHandler?.unloadWorld()
                }
                client.setScreen(TitleScreen())
            },
            poll = {
                if (client.world == null && client.player == null && client.currentScreen is TitleScreen) {
                    ActionResult.ok(
                        "Returned to title screen",
                        JsonObject().apply { addProperty("state", "title_screen") }
                    )
                } else {
                    null
                }
            }
        )
    }
}
