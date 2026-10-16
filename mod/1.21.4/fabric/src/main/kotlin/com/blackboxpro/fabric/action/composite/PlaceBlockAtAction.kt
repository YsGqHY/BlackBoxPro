package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject

class PlaceBlockAtAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val face = params.getStringOrNull("face") ?: "top"
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
        val placeResult = placeBlock.execute(JsonObject().apply {
            addProperty("x", x)
            addProperty("y", y)
            addProperty("z", z)
            addProperty("face", face)
            addProperty("hand", hand)
        })
        if (!placeResult.success) return ActionResult.fail("Failed to place block: ${placeResult.message}")

        return ActionResult.ok("Placed block at ($x, $y, $z) face=$face")
    }
}
