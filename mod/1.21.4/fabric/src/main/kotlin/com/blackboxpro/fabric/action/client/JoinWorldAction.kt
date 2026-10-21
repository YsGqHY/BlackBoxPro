package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireString
import com.blackboxpro.runtime.action.AsyncClientActionSupport
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.world.level.storage.LevelSummary
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class JoinWorldAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult =
        ActionResult.fail("JoinWorldAction requires commandId, use execute(params, commandId)")

    override fun execute(params: JsonObject, commandId: String): ActionResult {
        val client = MinecraftClient.getInstance()
        if (client.world != null || client.player != null) {
            return ActionResult.fail("Already in a world, leave the current world first")
        }

        val requestedWorldName = params.requireString("worldName").trim()
        if (requestedWorldName.isBlank()) {
            return ActionResult.fail("worldName cannot be blank")
        }

        val started = AtomicBoolean(false)
        val targetWorldId = AtomicReference<String?>(null)
        val targetDisplayName = AtomicReference<String?>(null)
        val terminal = AtomicReference<ActionResult?>(null)

        return AsyncClientActionSupport.startPolling(
            commandId = commandId,
            executeOnMainThread = { task -> client.execute(task) },
            timeoutMs = 90_000L,
            timeoutMessage = "Timed out waiting to join world: $requestedWorldName",
            startAction = {
                val storage = client.levelStorage
                val levelList = storage.getLevelList()
                storage.loadSummaries(levelList)
                    .whenComplete { summaries, throwable ->
                        if (throwable != null) {
                            terminal.set(ActionResult.fail("Failed to load local worlds: ${throwable.message}"))
                            return@whenComplete
                        }
                        val target = summaries.orEmpty().firstOrNull { matches(it, requestedWorldName) }
                        if (target == null) {
                            terminal.set(ActionResult.fail("World not found: $requestedWorldName"))
                            return@whenComplete
                        }
                        if (!target.isImmediatelyLoadable) {
                            terminal.set(ActionResult.fail("World is not immediately loadable: ${target.displayName}"))
                            return@whenComplete
                        }
                        targetWorldId.set(target.name)
                        targetDisplayName.set(target.displayName)
                        client.execute {
                            started.set(true)
                            client.createIntegratedServerLoader().start(target.name) {
                                terminal.set(ActionResult.fail("Failed to join world: ${target.displayName}"))
                            }
                        }
                    }
            },
            poll = {
                terminal.get()?.let { return@startPolling it }
                if (!started.get()) {
                    return@startPolling null
                }
                if (client.world != null && client.player != null) {
                    val worldId = targetWorldId.get() ?: requestedWorldName
                    val displayName = targetDisplayName.get() ?: requestedWorldName
                    ActionResult.ok(
                        "Joined world: $displayName",
                        JsonObject().apply {
                            addProperty("worldId", worldId)
                            addProperty("worldName", displayName)
                            addProperty("state", "in_world")
                        }
                    )
                } else {
                    null
                }
            }
        )
    }

    private fun matches(summary: LevelSummary, requestedWorldName: String): Boolean {
        val normalized = requestedWorldName.lowercase(Locale.ROOT)
        return summary.name.lowercase(Locale.ROOT) == normalized ||
            summary.displayName.lowercase(Locale.ROOT) == normalized
    }
}
