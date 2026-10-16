package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.RecipeBookDataC2SPacket
import net.minecraft.recipe.NetworkRecipeId

class RecipeBookSeenAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val recipeIndex = params.requireInt("recipeIndex")

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(RecipeBookDataC2SPacket(NetworkRecipeId(recipeIndex)))
        return ActionResult.ok("Marked recipe index=$recipeIndex as seen")
    }
}
