package com.blackboxpro.runtime.bindings

/**
 * 平台无关的 Screen 渲染回调桥接。
 *
 * NeoForge 端通过 ScreenEvent.Render.Post 事件实现，
 * Fabric 端通过 ScreenEvents.afterRender 实现。
 *
 * 用于 ScreenshotTooltipAction 在 screen 渲染完成后注入 tooltip 绘制。
 */
object ScreenRenderBridge {

    /**
     * 注册一次性的 screen 渲染后回调。
     * 回调在下一帧 screen 渲染完成后触发，仅触发一次。
     *
     * @param callback 接收当前帧的 DrawContext/GuiGraphics 实例（平台相关，需自行 cast）
     */
    fun interface AfterRenderCallback {
        fun onAfterRender(drawContext: Any)
    }

    fun interface Provider {
        fun registerOneShotAfterRender(callback: AfterRenderCallback)
    }

    @Volatile
    private var provider: Provider? = null

    fun bind(provider: Provider) {
        this.provider = provider
    }

    fun registerOneShotAfterRender(callback: AfterRenderCallback) {
        val p = provider ?: throw IllegalStateException("ScreenRenderBridge not bound")
        p.registerOneShotAfterRender(callback)
    }
}
