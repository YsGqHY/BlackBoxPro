package com.blackboxpro.runtime.action

import com.blackboxpro.common.runtime.action.ActionResult
import com.blackboxpro.common.runtime.dispatcher.RuntimeResponseSender
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AsyncClientActionSupport {

    private val scheduler = Executors.newSingleThreadScheduledExecutor(WorldActionThreadFactory())

    fun startPolling(
        commandId: String,
        executeOnMainThread: ((() -> Unit)) -> Unit,
        timeoutMs: Long,
        pollIntervalMs: Long = 100L,
        timeoutMessage: String,
        startAction: () -> Unit,
        poll: () -> ActionResult?
    ): ActionResult {
        return try {
            startAction()
            schedulePoll(commandId, executeOnMainThread, timeoutMs, pollIntervalMs, timeoutMessage, poll)
            ActionResult.async()
        } catch (t: Throwable) {
            ActionResult.fail(t.message ?: "Failed to start async action")
        }
    }

    private fun schedulePoll(
        commandId: String,
        executeOnMainThread: ((() -> Unit)) -> Unit,
        timeoutMs: Long,
        pollIntervalMs: Long,
        timeoutMessage: String,
        poll: () -> ActionResult?
    ) {
        val startedAt = System.nanoTime()
        val completed = AtomicBoolean(false)

        fun complete(result: ActionResult) {
            if (!completed.compareAndSet(false, true)) {
                return
            }
            RuntimeResponseSender.sendResponse(
                id = commandId,
                status = if (result.success) "success" else "failure",
                message = result.message,
                data = result.data
            )
        }

        fun tick() {
            if (completed.get()) {
                return
            }
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            if (elapsedMs >= timeoutMs) {
                complete(ActionResult.fail(timeoutMessage))
                return
            }
            try {
                executeOnMainThread {
                    if (completed.get()) {
                        return@executeOnMainThread
                    }
                    try {
                        val result = poll()
                        if (result != null) {
                            complete(result)
                        } else {
                            scheduler.schedule(::tick, pollIntervalMs, TimeUnit.MILLISECONDS)
                        }
                    } catch (t: Throwable) {
                        complete(ActionResult.fail(t.message ?: "Async action polling failed"))
                    }
                }
            } catch (t: Throwable) {
                complete(ActionResult.fail(t.message ?: "Failed to schedule async action"))
            }
        }

        scheduler.schedule(::tick, pollIntervalMs, TimeUnit.MILLISECONDS)
    }

    private class WorldActionThreadFactory : ThreadFactory {
        override fun newThread(task: Runnable): Thread =
            Thread(task, "BlackBoxPro-WorldAction").apply {
                isDaemon = true
            }
    }
}
