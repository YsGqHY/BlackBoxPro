package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import org.tabooproject.reflex.Reflex.Companion.getProperty

/**
 * 查询当前活跃的 Boss Bar 信息。
 * Action ID: "query_boss_bar"
 */
class QueryBossBarAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val bossBarHud = client.inGameHud.bossBarHud

        val bars = JsonArray()

        try {
            // BossBarHud.bossBars 是 Map<UUID, ClientBossBar>
            @Suppress("UNCHECKED_CAST")
            val bossBars = bossBarHud.getProperty<Map<*, *>>("bossBars") ?: emptyMap<Any, Any>()

            bossBars.values.forEach { bar ->
                if (bar is net.minecraft.entity.boss.BossBar) {
                    bars.add(JsonObject().apply {
                        addProperty("name", bar.name.string)
                        addProperty("percent", bar.percent)
                        addProperty("color", bar.color.name.lowercase())
                        addProperty("style", bar.style.name.lowercase())
                        addProperty("darkenSky", bar.shouldDarkenSky())
                        addProperty("playMusic", bar.hasDragonMusic())
                        addProperty("createFog", bar.shouldThickenFog())
                    })
                }
            }
        } catch (e: Exception) {
            // 反射失败时返回空列表，记录日志
            org.slf4j.LoggerFactory.getLogger("BlackBoxPro").warn("QueryBossBarAction reflection failed: {}", e.message)
        }

        val data = JsonObject().apply {
            add("bossBars", bars)
            addProperty("count", bars.size())
        }

        return ActionResult.ok("Boss bars queried", data)
    }
}
