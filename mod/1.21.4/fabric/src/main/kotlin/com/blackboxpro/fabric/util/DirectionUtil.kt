package com.blackboxpro.fabric.util

import net.minecraft.util.math.Direction

object DirectionUtil {
    private val map = mapOf(
        "down" to Direction.DOWN,
        "up" to Direction.UP,
        "north" to Direction.NORTH,
        "south" to Direction.SOUTH,
        "west" to Direction.WEST,
        "east" to Direction.EAST,
        "bottom" to Direction.DOWN,
        "top" to Direction.UP
    )

    fun fromString(face: String): Direction =
        map[face.lowercase()] ?: throw IllegalArgumentException("Unknown face: $face")
}
