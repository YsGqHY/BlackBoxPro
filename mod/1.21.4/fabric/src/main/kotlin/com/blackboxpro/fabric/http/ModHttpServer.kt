package com.blackboxpro.fabric.http
import com.blackboxpro.common.runtime.config.RuntimeBlackBoxConfig

import com.blackboxpro.common.protocol.HttpEndpoints
import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

object ModHttpServer {

    private val logger = LoggerFactory.getLogger("BlackBoxPro-Http")
    private var server: HttpServer? = null

    fun start() {
        val port = RuntimeBlackBoxConfig.current.network.httpPort
        try {
            val s = HttpServer.create(InetSocketAddress(port), 0)
            s.createContext(HttpEndpoints.EXECUTE, ExecuteHandler)
            s.createContext(HttpEndpoints.STATUS, StatusHandler)
            s.executor = Executors.newCachedThreadPool { r ->
                Thread(r, "BlackBoxPro-Http").apply { isDaemon = true }
            }
            s.start()
            server = s
            logger.info("BlackBoxPro HTTP server started on port {}", port)
        } catch (e: Exception) {
            logger.error("Failed to start HTTP server on port {}", port, e)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        logger.info("BlackBoxPro HTTP server stopped.")
    }
}
