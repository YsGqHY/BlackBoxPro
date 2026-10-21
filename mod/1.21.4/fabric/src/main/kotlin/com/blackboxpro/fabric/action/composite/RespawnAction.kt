package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.google.gson.JsonObject

class RespawnAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val respawn = ActionRegistry.find("perform_respawn")
            ?: return ActionResult.fail("perform_respawn action not registered")
        val result = respawn.execute(JsonObject())
        if (!result.success) return ActionResult.fail("Failed to respawn: ${result.message}")

        return ActionResult.ok("Respawned successfully")
    }
}
