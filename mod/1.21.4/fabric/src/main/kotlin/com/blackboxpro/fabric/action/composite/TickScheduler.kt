package com.blackboxpro.fabric.action.composite

import com.blackboxpro.common.runtime.scheduler.RuntimeTickScheduler
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import org.slf4j.LoggerFactory

object TickScheduler {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-TickScheduler")

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            RuntimeTickScheduler.tick()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            val count = RuntimeTickScheduler.clear()
            if (count > 0) {
                logger.info("Cleared {} scheduled tasks on disconnect", count)
            }
        }
    }

    fun schedule(delayTicks: Int, task: () -> Unit) {
        RuntimeTickScheduler.schedule(delayTicks, task)
    }

    fun clear() {
        RuntimeTickScheduler.clear()
    }
}
