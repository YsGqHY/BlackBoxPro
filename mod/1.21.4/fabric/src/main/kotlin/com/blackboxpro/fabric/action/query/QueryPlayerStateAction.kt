package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries

/**
 * 读取玩家完整状态信息。
 * Action ID: "query_player_state"
 */
class QueryPlayerStateAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val client = MinecraftClient.getInstance()
        val player = client.player
            ?: return ActionResult.fail("Player not available")

        val data = JsonObject().apply {
            addProperty("x", player.x)
            addProperty("y", player.y)
            addProperty("z", player.z)
            addProperty("yaw", player.yaw)
            addProperty("pitch", player.pitch)
            addProperty("health", player.health)
            addProperty("maxHealth", player.maxHealth)
            addProperty("food", player.hungerManager.foodLevel)
            addProperty("saturation", player.hungerManager.saturationLevel)
            // 1.21.4: GameMode.getId() 返回 Int（1.21.11 才返回 String），改用 getName() 取 "survival" 等小写名
            addProperty("gameMode", client.interactionManager?.currentGameMode?.getName() ?: "unknown")
            addProperty("onGround", player.isOnGround)
            addProperty("sneaking", player.isSneaking)
            addProperty("sprinting", player.isSprinting)
            addProperty("flying", player.abilities.flying)
            addProperty("dead", player.isDead)
            addProperty("selectedSlot", player.inventory.selectedSlot)
            addProperty("experienceLevel", player.experienceLevel)
            addProperty("experienceProgress", player.experienceProgress)

            // v1.3.0 新增字段
            addProperty("absorption", player.absorptionAmount)
            addProperty("armorValue", player.armor)
            addProperty("airSupply", player.air)
            addProperty("maxAirSupply", player.maxAir)
            addProperty("isSwimming", player.isSwimming)
            addProperty("isUsingItem", player.isUsingItem)
            addProperty("isFallFlying", player.isGliding)
            addProperty("fallDistance", player.fallDistance)
            addProperty("vehicleId", player.vehicle?.id ?: -1)
            val world = client.world
            addProperty("dimension", world?.registryKey?.value?.toString() ?: "unknown")
            addProperty("biome", world?.getBiome(player.blockPos)?.key
                ?.map { it.value.toString() }?.orElse("unknown") ?: "unknown")
            val mainItem = player.mainHandStack
            addProperty("mainHandItem", if (mainItem.isEmpty) "empty"
                else Registries.ITEM.getId(mainItem.item).toString())
        }

        return ActionResult.ok("Player state queried", data)
    }
}
