package com.blackboxpro.fabric.action.advanced

import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.ActionResult
import com.blackboxpro.fabric.util.getStringOrNull
import com.blackboxpro.fabric.util.requireInt
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket
import java.util.Optional

class EditBookAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val slot = params.requireInt("slot")
        val pagesArray = params.getAsJsonArray("pages")
            ?: return ActionResult.fail("Missing required field: pages")
        val title = params.getStringOrNull("title")

        if (pagesArray.size() > 200) {
            return ActionResult.fail("Too many pages: ${pagesArray.size()} > 200")
        }

        val pages = pagesArray.map { it.asString }
        pages.forEachIndexed { i, page ->
            if (page.length > 32767) {
                return ActionResult.fail("Page $i too long: ${page.length} > 32767")
            }
        }

        val networkHandler = MinecraftClient.getInstance().networkHandler
            ?: return ActionResult.fail("Not connected to server")

        networkHandler.sendPacket(
            BookUpdateC2SPacket(slot, pages, if (title != null) Optional.of(title) else Optional.empty())
        )
        return ActionResult.ok("Edited book in slot $slot with ${pages.size} pages")
    }
}
