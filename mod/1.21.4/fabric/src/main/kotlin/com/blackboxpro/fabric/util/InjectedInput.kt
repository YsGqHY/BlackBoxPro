package com.blackboxpro.fabric.util

import net.minecraft.client.input.Input
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.PlayerInput

/**
 * 自定义 Input 子类，用于注入模拟键盘输入。
 * 替换 KeyboardInput 后，每 tick 的 tick() 会从注入的 [playerInput] 计算 movementVector，
 * 而不是从真实按键状态读取。
 *
 * 使用方式：
 * 1. [install] 安装到玩家，保存原始 Input
 * 2. 每 tick 通过 [forward]/[sprint]/[jump] 等属性设置期望输入
 * 3. 移动完成后调用 [uninstall] 恢复原始 KeyboardInput
 */
class InjectedInput : Input() {

    /** 原始 Input（通常是 KeyboardInput），卸载时恢复 */
    private var original: Input? = null

    /** 安装时的 player 引用，用于卸载时恢复 */
    private var installedPlayer: ClientPlayerEntity? = null

    var forward: Boolean = false
    var backward: Boolean = false
    var left: Boolean = false
    var right: Boolean = false
    var jumping: Boolean = false
    var sneaking: Boolean = false
    var sprinting: Boolean = false

    override fun tick() {
        // 从注入的布尔值构建 PlayerInput
        playerInput = PlayerInput(forward, backward, left, right, jumping, sneaking, sprinting)

        // 1.21.4 的 Input 用两个独立的 float 字段承载移动向量
        // （与 KeyboardInput.tick() 逻辑一致）
        movementForward = getMovementMultiplier(forward, backward)
        movementSideways = getMovementMultiplier(left, right)
    }

    /** 安装到玩家，替换原始 Input。若当前已有其他 InjectedInput，先卸载以恢复原始 input。 */
    fun install(player: ClientPlayerEntity) {
        val currentInput = player.input
        if (currentInput is InjectedInput && currentInput !== this) {
            currentInput.uninstall(player)
        }
        original = player.input
        installedPlayer = player
        player.input = this
    }

    /** 卸载，恢复原始 Input。优先使用安装时保存的 player 引用。 */
    fun uninstall(player: ClientPlayerEntity? = null) {
        val target = player ?: installedPlayer
        original?.let { orig ->
            if (target != null && target.input === this) {
                target.input = orig
            }
        }
        original = null
        installedPlayer = null
    }

    /** 重置所有输入为默认值 */
    fun reset() {
        forward = false
        backward = false
        left = false
        right = false
        jumping = false
        sneaking = false
        sprinting = false
    }

    companion object {
        private fun getMovementMultiplier(positive: Boolean, negative: Boolean): Float =
            if (positive == negative) 0.0f else if (positive) 1.0f else -1.0f
    }
}
