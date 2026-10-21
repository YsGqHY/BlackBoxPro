package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket
import net.minecraft.recipe.NetworkRecipeId

class SelectRecipeAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val windowId = params.requireInt("windowId")
        val recipeIndex = params.requireInt("recipeIndex")
        val makeAll = params.getBooleanOrDefault("makeAll", false)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            CraftRequestC2SPacket(windowId, NetworkRecipeId(recipeIndex), makeAll)
        )
        return ActionResult.ok("Selected recipe index=$recipeIndex in window $windowId (makeAll=$makeAll)")
    }
}
