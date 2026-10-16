package com.blackboxpro.runtime.bindings

import com.blackboxpro.common.runtime.LogHandler
import com.blackboxpro.common.runtime.LoggerSupplier

/**
 * NeoForge platform binding for LoggerSupplier.
 */
object LoggerSupplierBinding : LoggerSupplier {
    override fun getLogger(name: String): LogHandler = SharedSlf4jLoggerSupplier.getLogger(name)
}
