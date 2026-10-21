package com.blackboxpro.fabric.action.composite

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject

class CraftRecipeAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val windowId = params.requireInt("windowId")
        val recipeIndex = params.requireInt("recipeIndex")
        val makeAll = params.getBooleanOrDefault("makeAll", false)

        // 1. 选择配方
        val selectRecipe = ActionRegistry.find("select_recipe")
            ?: return ActionResult.fail("select_recipe action not registered")
        val selectResult = selectRecipe.execute(JsonObject().apply {
            addProperty("windowId", windowId)
            addProperty("recipeIndex", recipeIndex)
            addProperty("makeAll", makeAll)
        })
        if (!selectResult.success) return ActionResult.fail("Failed to select recipe: ${selectResult.message}")

        // 2. 标记配方为已查看
        val seenRecipe = ActionRegistry.find("recipe_book_seen")
        seenRecipe?.execute(JsonObject().apply {
            addProperty("recipeIndex", recipeIndex)
        })

        return ActionResult.ok("Crafted recipe index=$recipeIndex in window $windowId (makeAll=$makeAll)")
    }
}
