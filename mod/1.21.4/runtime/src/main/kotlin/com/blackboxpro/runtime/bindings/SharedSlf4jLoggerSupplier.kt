package com.blackboxpro.runtime.bindings

import com.blackboxpro.common.runtime.LogHandler
import com.blackboxpro.common.runtime.LoggerSupplier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SharedSlf4jLoggerSupplier : LoggerSupplier {
    override fun getLogger(name: String): LogHandler = Slf4jLogHandler(LoggerFactory.getLogger(name))
}

private class Slf4jLogHandler(private val logger: Logger) : LogHandler {
    override fun info(message: String, vararg args: Any?) = logger.info(message, *args)
    override fun debug(message: String, vararg args: Any?) = logger.debug(message, *args)
    override fun warn(message: String, vararg args: Any?) = logger.warn(message, *args)
    override fun error(message: String, vararg args: Any?) = logger.error(message, *args)
}
