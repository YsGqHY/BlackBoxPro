package com.blackboxpro.fabric.util.pathfinding

import com.blackboxpro.common.runtime.config.RuntimeNavigationConfig
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * A* 寻路算法。
 * 在客户端世界数据上搜索从起点到终点的可行走路径。
 */
object Pathfinder {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-Pathfinder")

    private val HORIZONTAL_OFFSETS = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0),
        intArrayOf(0, 1), intArrayOf(0, -1)
    )

    fun findPath(
        start: BlockPos,
        goal: BlockPos,
        world: ClientWorld,
        config: RuntimeNavigationConfig
    ): List<PathNode> {
        val openSet = PriorityQueue<PathNode>(compareBy { it.f })
        val closedSet = HashSet<Long>()
        val bestG = HashMap<Long, Double>()
        val mutablePos = BlockPos.Mutable()

        val startNode = PathNode(start.x, start.y, start.z, g = 0.0, h = heuristic(start.x, start.y, start.z, goal))
        openSet.add(startNode)
        bestG[packKey(start.x, start.y, start.z)] = 0.0

        var iterations = 0

        while (openSet.isNotEmpty() && iterations < config.maxIterations) {
            iterations++
            val current = openSet.poll()
            val currentKey = packKey(current.x, current.y, current.z)

            if (closedSet.contains(currentKey)) continue
            closedSet.add(currentKey)

            // 到达判定：曼哈顿距离 <= 1
            if (abs(current.x - goal.x) + abs(current.y - goal.y) + abs(current.z - goal.z) <= 1) {
                val path = reconstructPath(current)
                if (path.size <= config.maxPathLength) {
                    logger.debug("Path found: {} nodes, {} iterations", path.size, iterations)
                    return path
                }
            }

            // 展开邻居
            for (offset in HORIZONTAL_OFFSETS) {
                val dx = offset[0]
                val dz = offset[1]
                expandNeighbor(current, dx, dz, 0, false, 1.0, goal, world, config, openSet, closedSet, bestG, mutablePos)
                expandNeighbor(current, dx, dz, 1, true, config.jumpCost, goal, world, config, openSet, closedSet, bestG, mutablePos)
                expandNeighbor(current, dx, dz, -1, false, config.fallCost, goal, world, config, openSet, closedSet, bestG, mutablePos)
            }
        }

        logger.debug("No path found after {} iterations", iterations)
        return emptyList()
    }

    private fun expandNeighbor(
        current: PathNode, dx: Int, dz: Int, dy: Int, isJump: Boolean, costMultiplier: Double,
        goal: BlockPos, world: ClientWorld, config: RuntimeNavigationConfig,
        openSet: PriorityQueue<PathNode>, closedSet: HashSet<Long>, bestG: HashMap<Long, Double>,
        mutablePos: BlockPos.Mutable
    ) {
        val nx = current.x + dx
        val ny = current.y + dy
        val nz = current.z + dz
        val key = packKey(nx, ny, nz)

        if (closedSet.contains(key)) return

        // 跳跃需要头顶有空间
        if (isJump) {
            if (!isPassable(world, current.x, current.y + 2, current.z, mutablePos)) return
        }

        // 下落需要水平方向可通过
        if (dy == -1) {
            if (!isPassable(world, current.x + dx, current.y, current.z + dz, mutablePos)) return
        }

        // 目标位置：脚下实心，身体和头部可通过
        if (!isSolid(world, nx, ny - 1, nz, mutablePos)) return
        if (!isPassable(world, nx, ny, nz, mutablePos)) return
        if (!isPassable(world, nx, ny + 1, nz, mutablePos)) return

        val tentativeG = current.g + costMultiplier
        val existingG = bestG[key]
        if (existingG != null && tentativeG >= existingG) return

        bestG[key] = tentativeG
        val node = PathNode(nx, ny, nz, parent = current, g = tentativeG, h = heuristic(nx, ny, nz, goal), jumpRequired = isJump)
        openSet.add(node)
    }

    private fun isPassable(world: ClientWorld, x: Int, y: Int, z: Int, mutablePos: BlockPos.Mutable): Boolean {
        mutablePos.set(x, y, z)
        val state = world.getBlockState(mutablePos)
        return !state.isSolidBlock(world, mutablePos)
    }

    private fun isSolid(world: ClientWorld, x: Int, y: Int, z: Int, mutablePos: BlockPos.Mutable): Boolean {
        mutablePos.set(x, y, z)
        val state = world.getBlockState(mutablePos)
        return state.isSolidBlock(world, mutablePos)
    }

    private fun heuristic(x: Int, y: Int, z: Int, goal: BlockPos): Double =
        (abs(x - goal.x) + abs(y - goal.y) + abs(z - goal.z)).toDouble()

    private fun reconstructPath(node: PathNode): List<PathNode> {
        val path = mutableListOf<PathNode>()
        var current: PathNode? = node
        while (current != null) {
            path.add(current)
            current = current.parent
        }
        return path.reversed()
    }

    private fun packKey(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
}
