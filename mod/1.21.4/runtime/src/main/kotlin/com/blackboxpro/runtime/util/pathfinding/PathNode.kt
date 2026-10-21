package com.blackboxpro.runtime.util.pathfinding

data class PathNode(
    val x: Int,
    val y: Int,
    val z: Int,
    val parent: PathNode? = null,
    var g: Double = 0.0,
    var h: Double = 0.0,
    var jumpRequired: Boolean = false
) {
    val f: Double get() = g + h

    override fun equals(other: Any?): Boolean =
        other is PathNode && x == other.x && y == other.y && z == other.z

    override fun hashCode(): Int = x * 31 * 31 + y * 31 + z
}
