package com.blackboxpro.runtime.util

object RuntimeBlockFaceUtil {
    data class FaceCenter(val x: Double, val y: Double, val z: Double)

    fun calculateFaceCenter(blockX: Int, blockY: Int, blockZ: Int, face: String): FaceCenter =
        when (face.lowercase()) {
            "top", "up"      -> FaceCenter(blockX + 0.5, blockY + 1.0, blockZ + 0.5)
            "bottom", "down" -> FaceCenter(blockX + 0.5, blockY.toDouble(), blockZ + 0.5)
            "north"          -> FaceCenter(blockX + 0.5, blockY + 0.5, blockZ.toDouble())
            "south"          -> FaceCenter(blockX + 0.5, blockY + 0.5, blockZ + 1.0)
            "west"           -> FaceCenter(blockX.toDouble(), blockY + 0.5, blockZ + 0.5)
            "east"           -> FaceCenter(blockX + 1.0, blockY + 0.5, blockZ + 0.5)
            else              -> FaceCenter(blockX + 0.5, blockY + 0.5, blockZ + 0.5)
        }
}
