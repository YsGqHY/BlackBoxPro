package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.*
import com.google.gson.JsonObject
import net.minecraft.block.entity.JigsawBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateJigsawC2SPacket
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

class UpdateJigsawBlockAction : ActionExecutor {

    companion object {
        private val JOINT_MAP = mapOf(
            "rollable" to JigsawBlockEntity.Joint.ROLLABLE,
            "aligned" to JigsawBlockEntity.Joint.ALIGNED
        )
    }

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val name = params.requireString("name")
        val target = params.requireString("target")
        val pool = params.requireString("pool")
        val finalState = params.getStringOrNull("finalState") ?: ""
        val jointTypeStr = params.getStringOrNull("jointType") ?: "rollable"
        val selectionPriority = params.getIntOrDefault("selectionPriority", 0)
        val placementPriority = params.getIntOrDefault("placementPriority", 0)

        val jointType = JOINT_MAP[jointTypeStr.lowercase()]
            ?: return ActionResult.fail("Unknown joint type: $jointTypeStr (valid: ${JOINT_MAP.keys})")

        val nameId = try {
            Identifier.of(name)
        } catch (e: Exception) {
            return ActionResult.fail("Invalid identifier for name: $name")
        }
        val targetId = try {
            Identifier.of(target)
        } catch (e: Exception) {
            return ActionResult.fail("Invalid identifier for target: $target")
        }
        val poolId = try {
            Identifier.of(pool)
        } catch (e: Exception) {
            return ActionResult.fail("Invalid identifier for pool: $pool")
        }

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(
            UpdateJigsawC2SPacket(
                BlockPos(x, y, z),
                nameId,
                targetId,
                poolId,
                finalState,
                jointType,
                selectionPriority,
                placementPriority
            )
        )

        return ActionResult.ok("Updated jigsaw block at ($x, $y, $z)")
    }
}
