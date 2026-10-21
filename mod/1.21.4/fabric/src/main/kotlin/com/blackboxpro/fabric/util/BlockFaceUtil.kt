package com.blackboxpro.fabric.util

import com.blackboxpro.runtime.util.RuntimeBlockFaceUtil

/**
 * 方块面中心坐标计算工具。
 */
object BlockFaceUtil {
    fun calculateFaceCenter(blockX: Int, blockY: Int, blockZ: Int, face: String): RuntimeBlockFaceUtil.FaceCenter =
        RuntimeBlockFaceUtil.calculateFaceCenter(blockX, blockY, blockZ, face)
}
