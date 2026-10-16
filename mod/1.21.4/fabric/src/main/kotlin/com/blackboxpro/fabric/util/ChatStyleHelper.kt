package com.blackboxpro.fabric.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import net.minecraft.text.TextColor
import java.util.Optional

object ChatStyleHelper {

    data class StyleMatch(
        val matchedText: String,
        val style: Style
    )

    fun findStyleMatch(raw: String, match: String): StyleMatch? {
        val text = deserialize(raw) ?: return null
        return findStyleMatch(text, match)
    }

    fun findStyleMatch(text: Text, match: String): StyleMatch? {
        var result: StyleMatch? = null
        text.visit({ style, content ->
            if (result == null && content.contains(match)) {
                result = StyleMatch(match, style)
                Optional.of(content)
            } else {
                Optional.empty<String>()
            }
        }, Style.EMPTY)
        return result
    }

    fun styleToJson(style: Style): JsonObject = JsonObject().apply {
        style.clickEvent?.let { add("clickEvent", clickEventToJson(it)) }
        style.hoverEvent?.let { add("hoverEvent", hoverEventToJson(it)) }
        style.color?.let { addProperty("color", it.toString()) }
        if (style.isBold) addProperty("bold", true)
        if (style.isItalic) addProperty("italic", true)
        if (style.isUnderlined) addProperty("underlined", true)
        if (style.isStrikethrough) addProperty("strikethrough", true)
        if (style.isObfuscated) addProperty("obfuscated", true)
        style.insertion?.let { addProperty("insertion", it) }
    }

    fun clickEventToJson(clickEvent: ClickEvent): JsonObject = JsonObject().apply {
        addProperty("action", clickEvent.action.asString())
        addProperty("value", clickEvent.value)
    }

    fun hoverEventToJson(hoverEvent: HoverEvent): JsonObject = JsonObject().apply {
        addProperty("action", hoverEvent.action.asString())
        hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT)?.let { text ->
            addProperty("contents", text.string)
            add("contentsJson", serializeComponent(text))
        }
        hoverEvent.getValue(HoverEvent.Action.SHOW_ITEM)?.let { item ->
            add("contents", ItemStackSerializer.serialize(item.asStack()))
        }
        hoverEvent.getValue(HoverEvent.Action.SHOW_ENTITY)?.let { entity ->
            add("contents", JsonObject().apply {
                addProperty("type", Registries.ENTITY_TYPE.getId(entity.entityType).toString())
                addProperty("id", entity.uuid.toString())
                entity.name.ifPresent { addProperty("name", it.string) }
            })
        }
    }

    fun serializeComponent(text: Text): JsonElement = runCatching {
        val client = MinecraftClient.getInstance()
        val ops = client.world?.registryManager?.getOps(JsonOps.INSTANCE) ?: JsonOps.INSTANCE
        TextCodecs.CODEC.encodeStart(ops, text).result().orElse(JsonPrimitive(text.string))
    }.getOrElse {
        JsonPrimitive(text.string)
    }

    fun componentToLegacyString(text: Text): String {
        val builder = StringBuilder()
        text.visit({ style, content ->
            appendStyleCodes(builder, style)
            builder.append(content)
            Optional.empty<String>()
        }, Style.EMPTY)
        return builder.toString()
    }

    private fun deserialize(raw: String): Text? = runCatching {
        val client = MinecraftClient.getInstance()
        val ops = client.world?.registryManager?.getOps(JsonOps.INSTANCE) ?: JsonOps.INSTANCE
        TextCodecs.CODEC.parse(ops, JsonParser.parseString(raw)).result().orElse(null)
    }.getOrNull()

    private fun appendStyleCodes(builder: StringBuilder, style: Style) {
        style.color?.let { builder.append('§').append(colorToLegacyCode(it)) }
        if (style.isBold) builder.append("§l")
        if (style.isItalic) builder.append("§o")
        if (style.isUnderlined) builder.append("§n")
        if (style.isStrikethrough) builder.append("§m")
        if (style.isObfuscated) builder.append("§k")
    }

    private fun colorToLegacyCode(color: TextColor): Char = when (color.rgb) {
        0x000000 -> '0'
        0x0000AA -> '1'
        0x00AA00 -> '2'
        0x00AAAA -> '3'
        0xAA0000 -> '4'
        0xAA00AA -> '5'
        0xFFAA00 -> '6'
        0xAAAAAA -> '7'
        0x555555 -> '8'
        0x5555FF -> '9'
        0x55FF55 -> 'a'
        0x55FFFF -> 'b'
        0xFF5555 -> 'c'
        0xFF55FF -> 'd'
        0xFFFF55 -> 'e'
        0xFFFFFF -> 'f'
        else -> findNearestLegacyCode(color.rgb)
    }

    private fun findNearestLegacyCode(rgb: Int): Char {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        val legacyColors = mapOf(
            '0' to 0x000000,
            '1' to 0x0000AA,
            '2' to 0x00AA00,
            '3' to 0x00AAAA,
            '4' to 0xAA0000,
            '5' to 0xAA00AA,
            '6' to 0xFFAA00,
            '7' to 0xAAAAAA,
            '8' to 0x555555,
            '9' to 0x5555FF,
            'a' to 0x55FF55,
            'b' to 0x55FFFF,
            'c' to 0xFF5555,
            'd' to 0xFF55FF,
            'e' to 0xFFFF55,
            'f' to 0xFFFFFF
        )
        return legacyColors.minBy { (_, value) ->
            val dr = r - (value shr 16 and 0xFF)
            val dg = g - (value shr 8 and 0xFF)
            val db = b - (value and 0xFF)
            dr * dr + dg * dg + db * db
        }.key
    }
}
