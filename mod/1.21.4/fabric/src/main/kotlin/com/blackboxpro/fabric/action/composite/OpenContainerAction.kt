package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject

class OpenContainerAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val hand = params.getStringOrNull("hand") ?: "main_hand"

        val lookAt = ActionRegistry.find("look_at")
            ?: return ActionResult.fail("look_at action not registered")
        val lookResult = lookAt.execute(JsonObject().apply {
            addProperty("x", x + 0.5)
            addProperty("y", y + 0.5)
            addProperty("z", z + 0.5)
        })
        if (!lookResult.success) return ActionResult.fail("Failed to look at block: ${lookResult.message}")

        val placeBlock = ActionRegistry.find("place_block")
            ?: return ActionResult.fail("place_block action not registered")
        val interactResult = placeBlock.execute(JsonObject().apply {
            addProperty("x", x)
            addProperty("y", y)
            addProperty("z", z)
            addProperty("face", "top")
            addProperty("hand", hand)
        })
        if (!interactResult.success) return ActionResult.fail("Failed to interact with block: ${interactResult.message}")

        return ActionResult.ok("Attempted to open container at ($x, $y, $z)")
    }
}
