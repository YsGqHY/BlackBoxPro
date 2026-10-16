package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.*
import com.google.gson.JsonObject
import net.minecraft.block.entity.StructureBlockBlockEntity
import net.minecraft.block.enums.StructureBlockMode
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.UpdateStructureBlockC2SPacket
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

class UpdateStructureBlockAction : ActionExecutor {

    companion object {
        private val MODE_MAP = mapOf(
            "save" to StructureBlockMode.SAVE,
            "load" to StructureBlockMode.LOAD,
            "corner" to StructureBlockMode.CORNER,
            "data" to StructureBlockMode.DATA
        )

        private val ACTION_MAP = mapOf(
            0 to StructureBlockBlockEntity.Action.UPDATE_DATA,
            1 to StructureBlockBlockEntity.Action.SAVE_AREA,
            2 to StructureBlockBlockEntity.Action.LOAD_AREA,
            3 to StructureBlockBlockEntity.Action.SCAN_AREA
        )

        private val MIRROR_MAP = mapOf(
            "none" to BlockMirror.NONE,
            "left_right" to BlockMirror.LEFT_RIGHT,
            "front_back" to BlockMirror.FRONT_BACK
        )

        private val ROTATION_MAP = mapOf(
            "none" to BlockRotation.NONE,
            "clockwise_90" to BlockRotation.CLOCKWISE_90,
            "clockwise_180" to BlockRotation.CLOCKWISE_180,
            "counterclockwise_90" to BlockRotation.COUNTERCLOCKWISE_90
        )

        // flags 位掩码常量
        private const val IGNORE_ENTITIES_FLAG = 0x01
        private const val SHOW_AIR_FLAG = 0x02
        private const val SHOW_BOUNDING_BOX_FLAG = 0x04
    }

    override fun execute(params: JsonObject): ActionResult {
        val x = params.requireInt("x")
        val y = params.requireInt("y")
        val z = params.requireInt("z")
        val actionInt = params.requireInt("action")
        val modeStr = params.requireString("mode")
        val name = params.requireString("name")
        val offsetX = params.getIntOrDefault("offsetX", 0)
        val offsetY = params.getIntOrDefault("offsetY", 0)
        val offsetZ = params.getIntOrDefault("offsetZ", 0)
        val sizeX = params.getIntOrDefault("sizeX", 0)
        val sizeY = params.getIntOrDefault("sizeY", 0)
        val sizeZ = params.getIntOrDefault("sizeZ", 0)
        val mirrorStr = params.getStringOrNull("mirror") ?: "none"
        val rotationStr = params.getStringOrNull("rotation") ?: "none"
        val metadata = params.getStringOrNull("metadata") ?: ""
        val integrity = params.getFloatOrDefault("integrity", 1.0f)
        val seed = params.getLongOrDefault("seed", 0L)
        val flags = params.getIntOrDefault("flags", 0)

        val action = ACTION_MAP[actionInt]
            ?: return ActionResult.fail("Unknown structure block action: $actionInt (valid: 0-3)")
        val mode = MODE_MAP[modeStr.lowercase()]
            ?: return ActionResult.fail("Unknown structure block mode: $modeStr (valid: ${MODE_MAP.keys})")
        val mirror = MIRROR_MAP[mirrorStr.lowercase()]
            ?: return ActionResult.fail("Unknown mirror: $mirrorStr (valid: ${MIRROR_MAP.keys})")
        val rotation = ROTATION_MAP[rotationStr.lowercase()]
            ?: return ActionResult.fail("Unknown rotation: $rotationStr (valid: ${ROTATION_MAP.keys})")

        val ignoreEntities = (flags and IGNORE_ENTITIES_FLAG) != 0
        val showAir = (flags and SHOW_AIR_FLAG) != 0
        val showBoundingBox = (flags and SHOW_BOUNDING_BOX_FLAG) != 0
        // 1.21.4 的结构方块包不携带 strict 标志（该参数为 1.21.11 新增）

        val handler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        handler.sendPacket(
            UpdateStructureBlockC2SPacket(
                BlockPos(x, y, z),
                action,
                mode,
                name,
                BlockPos(offsetX, offsetY, offsetZ),
                Vec3i(sizeX, sizeY, sizeZ),
                mirror,
                rotation,
                metadata,
                ignoreEntities,
                showAir,
                showBoundingBox,
                integrity,
                seed
            )
        )

        return ActionResult.ok("Updated structure block at ($x, $y, $z)")
    }
}
