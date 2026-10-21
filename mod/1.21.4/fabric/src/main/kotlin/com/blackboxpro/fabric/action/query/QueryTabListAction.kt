package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient

/**
 * 查询 Tab 列表在线玩家信息。
 * Action ID: "query_tab_list"
 */
class QueryTabListAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val networkHandler = client.networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val limit = params.getIntOrDefault("limit", 100).coerceIn(1, 200)

        val players = networkHandler.playerList
            .sortedBy { it.latency }
            .take(limit)

        val arr = JsonArray()
        players.forEach { entry ->
            arr.add(JsonObject().apply {
                addProperty("name", entry.profile.name)
                addProperty("uuid", entry.profile.id.toString())
                addProperty("latency", entry.latency)
                // 1.21.4: GameMode.getId() 返回 Int（1.21.11 才返回 String），改用 getName() 取 "survival" 等小写名
                addProperty("gameMode", entry.gameMode?.getName() ?: "unknown")
                entry.displayName?.let { addProperty("displayName", it.string) }
                entry.scoreboardTeam?.let { addProperty("team", it.name) }
            })
        }

        val data = JsonObject().apply {
            add("players", arr)
            addProperty("count", arr.size())
            addProperty("total", networkHandler.playerList.size)
        }

        return ActionResult.ok("Tab list queried", data)
    }
}
