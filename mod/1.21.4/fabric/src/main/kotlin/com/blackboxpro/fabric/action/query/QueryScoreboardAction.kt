package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getStringOrNull
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.scoreboard.ScoreboardDisplaySlot

/**
 * 查询记分板状态。
 * Action ID: "query_scoreboard"
 */
class QueryScoreboardAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val world = client.world
            ?: return ActionResult.fail("World not available")
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val scoreboard = world.scoreboard
        val objectiveName = params.getStringOrNull("objective")

        val data = JsonObject()

        // 列出所有 objective
        val objectives = JsonArray()
        scoreboard.objectives.forEach { obj ->
            objectives.add(JsonObject().apply {
                addProperty("name", obj.name)
                addProperty("displayName", obj.displayName.string)
                addProperty("criteria", obj.criterion.name)
                addProperty("renderType", obj.renderType.name.lowercase())
            })
        }
        data.add("objectives", objectives)

        // sidebar 显示
        val sidebarObj = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR)
        if (sidebarObj != null) {
            val sidebar = JsonObject().apply {
                addProperty("objectiveName", sidebarObj.name)
                addProperty("displayName", sidebarObj.displayName.string)
            }
            val scores = JsonArray()
            scoreboard.getScoreboardEntries(sidebarObj).forEach { entry ->
                scores.add(JsonObject().apply {
                    addProperty("name", entry.owner)
                    addProperty("value", entry.value)
                    entry.display?.let { addProperty("displayText", it.string) }
                })
            }
            sidebar.add("scores", scores)
            data.add("sidebar", sidebar)
        }

        // 指定 objective 的分数
        if (objectiveName != null) {
            val obj = scoreboard.getNullableObjective(objectiveName)
            if (obj != null) {
                val scores = JsonArray()
                scoreboard.getScoreboardEntries(obj).forEach { entry ->
                    scores.add(JsonObject().apply {
                        addProperty("name", entry.owner)
                        addProperty("value", entry.value)
                    })
                }
                data.add("queriedScores", scores)
            }
        }

        // 玩家所在队伍
        val team = scoreboard.getScoreHolderTeam(player.nameForScoreboard)
        if (team != null) {
            data.add("playerTeam", JsonObject().apply {
                addProperty("name", team.name)
                addProperty("displayName", team.displayName.string)
                addProperty("prefix", team.prefix.string)
                addProperty("suffix", team.suffix.string)
                addProperty("color", team.color.name)
            })
        }

        return ActionResult.ok("Scoreboard queried", data)
    }
}
