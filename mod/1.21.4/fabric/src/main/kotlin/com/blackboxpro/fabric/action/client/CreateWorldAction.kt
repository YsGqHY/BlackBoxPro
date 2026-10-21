package com.blackboxpro.fabric.action.client

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireString
import com.blackboxpro.runtime.action.AsyncClientActionSupport
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.world.CreateWorldScreen
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.world.Difficulty
import net.minecraft.world.GameMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.tabooproject.reflex.Reflex.Companion.invokeMethod

class CreateWorldAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult =
        ActionResult.fail("CreateWorldAction requires commandId, use execute(params, commandId)")

    override fun execute(params: JsonObject, commandId: String): ActionResult {
        val client = MinecraftClient.getInstance()
        if (client.world != null || client.player != null) {
            return ActionResult.fail("Already in a world, leave the current world first")
        }

        val requestedWorldName = params.requireString("worldName").trim()
        validateWorldName(requestedWorldName)?.let { return ActionResult.fail(it) }

        if (client.levelStorage.levelExists(requestedWorldName)) {
            return ActionResult.fail("World already exists: $requestedWorldName")
        }

        val mode = parseMode(params.getStringOrNull("gameMode"))
            ?: return ActionResult.fail("Invalid gameMode: ${params.getStringOrNull("gameMode")}")
        val difficulty = parseDifficulty(params.getStringOrNull("difficulty"))
            ?: return ActionResult.fail("Invalid difficulty: ${params.getStringOrNull("difficulty")}")
        val allowCommands = params.getBooleanOrDefault("allowCommands", mode.gameMode == GameMode.CREATIVE)
        val generateStructures = params.getBooleanOrDefault("generateStructures", true)
        val bonusChest = params.getBooleanOrDefault("bonusChest", false)
        val seedText = params.getStringOrNull("seed")?.trim()?.takeIf { it.isNotEmpty() }
        val terminal = AtomicReference<ActionResult?>(null)

        return AsyncClientActionSupport.startPolling(
            commandId = commandId,
            executeOnMainThread = { task -> client.execute(task) },
            timeoutMs = 90_000L,
            timeoutMessage = "Timed out waiting for world creation: $requestedWorldName",
            startAction = {
                // 1.21.4 的 CreateWorldScreen.show(client, parent) 打开创建世界界面；
                // 其 create(...) 5 参重载需要预先构造 LevelInfo/GeneratorOptionsHolder，不适用此处
                CreateWorldScreen.show(client, client.currentScreen)
                client.execute {
                    try {
                        val screen = client.currentScreen as? CreateWorldScreen
                            ?: throw IllegalStateException("Create world screen did not open")
                        val creator = screen.worldCreator
                        creator.setWorldName(requestedWorldName)
                        creator.setGameMode(mode.worldCreatorMode)
                        creator.setDifficulty(difficulty)
                        creator.setCheatsEnabled(allowCommands)
                        creator.setGenerateStructures(generateStructures)
                        creator.setBonusChestEnabled(bonusChest)
                        if (seedText != null) {
                            creator.setSeed(seedText)
                        }

                        screen.invokeMethod<Any?>("createLevel")
                    } catch (t: Throwable) {
                        terminal.set(ActionResult.fail("Failed to create world: ${t.message}"))
                    }
                }
            },
            poll = {
                terminal.get()?.let { return@startPolling it }
                if (client.world != null && client.player != null) {
                    ActionResult.ok(
                        "Created and entered world: $requestedWorldName",
                        JsonObject().apply {
                            addProperty("worldName", requestedWorldName)
                            addProperty("state", "in_world")
                        }
                    )
                } else {
                    null
                }
            }
        )
    }

    private fun parseMode(raw: String?): WorldMode? =
        when ((raw ?: "survival").trim().lowercase(Locale.ROOT)) {
            "survival" -> WorldMode(GameMode.SURVIVAL, WorldCreator.Mode.SURVIVAL)
            "creative" -> WorldMode(GameMode.CREATIVE, WorldCreator.Mode.CREATIVE)
            "hardcore" -> WorldMode(GameMode.SURVIVAL, WorldCreator.Mode.HARDCORE)
            else -> null
        }

    private fun parseDifficulty(raw: String?): Difficulty? =
        when ((raw ?: "normal").trim().lowercase(Locale.ROOT)) {
            "peaceful" -> Difficulty.PEACEFUL
            "easy" -> Difficulty.EASY
            "normal" -> Difficulty.NORMAL
            "hard" -> Difficulty.HARD
            else -> null
        }

    private fun validateWorldName(worldName: String): String? = when {
        worldName.isBlank() -> "worldName cannot be blank"
        INVALID_WORLD_NAME.any(worldName::contains) -> "worldName contains invalid filesystem characters"
        worldName.contains("..") -> "worldName cannot contain '..'"
        else -> null
    }

    private data class WorldMode(
        val gameMode: GameMode,
        val worldCreatorMode: WorldCreator.Mode
    )

    companion object {
        private val INVALID_WORLD_NAME = charArrayOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
    }
}
