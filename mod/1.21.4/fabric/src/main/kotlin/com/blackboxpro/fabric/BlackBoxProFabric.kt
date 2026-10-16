package com.blackboxpro.fabric

import com.blackboxpro.fabric.action.composite.TickScheduler
import com.blackboxpro.fabric.dispatcher.ActionRegistry
import com.blackboxpro.fabric.dispatcher.CommandDispatcher
import com.blackboxpro.fabric.http.ModHttpServer
import com.blackboxpro.fabric.util.ChatHistoryBuffer
import com.blackboxpro.fabric.util.FabricRuntimeScreenshotProvider
import com.blackboxpro.common.runtime.screenshot.RuntimeScreenshotBridge
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object BlackBoxProFabric : ClientModInitializer {

    private val logger = LoggerFactory.getLogger("BlackBoxProFabric")
    val VERSION: String by lazy {
        FabricLoader.getInstance().getModContainer("blackboxpro")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
    }

    override fun onInitializeClient() {
        // 1. 加载配置

        // 2. 绑定运行时桥接
        RuntimeScreenshotBridge.bind(FabricRuntimeScreenshotProvider)

        // 3. 注册所有行为执行器
        ActionRegistry.registerAll()

        // 4. 初始化调度器
        CommandDispatcher.init()

        // 5. 初始化 Tick 调度器（复合行为用）
        TickScheduler.init()

        // 6. 启动 HTTP Server（最后启动，确保其他组件已就绪）
        ModHttpServer.start()
        ClientLifecycleEvents.CLIENT_STOPPING.register { ModHttpServer.stop() }

        // 7. 注册聊天消息监听器（供 query_chat_history 使用）
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            ChatHistoryBuffer.addMessage(message, "CHAT")
        }
        ClientReceiveMessageEvents.GAME.register { message, isOverlay ->
            ChatHistoryBuffer.addMessage(message, if (isOverlay) "ACTION_BAR" else "SYSTEM")
        }

        logger.info("BlackBoxProFabric v{} loaded. {} actions registered.", VERSION, ActionRegistry.size())
    }
}
