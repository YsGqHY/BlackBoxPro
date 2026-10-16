package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ContainerTooltipHelper
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.google.gson.JsonObject

class QueryTooltipStateAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val advanced = params.getBooleanOrDefault("advanced", false)
        val data = ContainerTooltipHelper.queryCurrentTooltip(advanced).toQueryJson()
        return ActionResult.ok("Tooltip state queried", data)
    }
}
