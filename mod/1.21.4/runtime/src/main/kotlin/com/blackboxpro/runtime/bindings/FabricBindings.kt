package com.blackboxpro.runtime.bindings

import com.blackboxpro.common.runtime.LogHandler
import com.blackboxpro.common.runtime.LoggerSupplier

/**
 * Fabric platform binding for LoggerSupplier.
 */
object FabricBindings : LoggerSupplier {
    override fun getLogger(name: String): LogHandler = SharedSlf4jLoggerSupplier.getLogger(name)
}
