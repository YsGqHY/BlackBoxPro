package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getIntOrDefault
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import java.util.Optional

class SetBeaconEffectAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val primaryEffect = params.getIntOrDefault("primaryEffect", -1)
        val secondaryEffect = params.getIntOrDefault("secondaryEffect", -1)

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        val primaryOpt = resolveEffect(primaryEffect)
        val secondaryOpt = resolveEffect(secondaryEffect)

        networkHandler.sendPacket(
            UpdateBeaconC2SPacket(primaryOpt, secondaryOpt)
        )
        return ActionResult.ok("Set beacon effects: primary=$primaryEffect, secondary=$secondaryEffect")
    }

    private fun resolveEffect(id: Int): Optional<RegistryEntry<StatusEffect>> {
        if (id < 0) return Optional.empty()
        val effect = Registries.STATUS_EFFECT.get(id) ?: return Optional.empty()
        return Optional.of(Registries.STATUS_EFFECT.getEntry(effect))
    }
}
