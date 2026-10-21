package com.blackboxpro.fabric.action.query

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.ContainerTooltipHelper
import com.blackboxpro.fabric.util.getBooleanOrDefault
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject

class QuerySlotTooltipAction : ActionExecutor {

    override fun execute(params: JsonObject): ActionResult {
        val slotIndex = params.requireInt("slot")
        val advanced = params.getBooleanOrDefault("advanced", false)
        return runCatching {
            val snapshot = ContainerTooltipHelper.querySlotTooltip(slotIndex, advanced)
            ActionResult.ok(
                if (snapshot.empty) "Slot $slotIndex is empty" else "Tooltip queried for slot $slotIndex",
                snapshot.toSlotTooltipJson()
            )
        }.getOrElse {
            ActionResult.fail(it.message ?: "Failed to query tooltip for slot $slotIndex")
        }
    }
}
