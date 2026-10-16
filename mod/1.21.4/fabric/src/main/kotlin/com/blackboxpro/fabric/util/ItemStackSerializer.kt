package com.blackboxpro.fabric.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList

/**
 * ItemStack → JsonObject 序列化工具。
 * 将客户端物品数据转换为可通过 Plugin Message 回传的 JSON。
 */
object ItemStackSerializer {

    fun serialize(stack: ItemStack): JsonObject {
        if (stack.isEmpty) return JsonObject().apply { addProperty("empty", true) }

        val json = JsonObject()
        json.addProperty("empty", false)
        json.addProperty("itemId", Registries.ITEM.getId(stack.item).toString())
        json.addProperty("count", stack.count)

        // components
        val components = JsonObject()
        val registryLookup = MinecraftClient.getInstance().world?.registryManager
        serializeComponents(stack, components, registryLookup)
        json.add("components", components)

        // SNBT fallback via Codec
        try {
            if (registryLookup != null) {
                val nbtOps = net.minecraft.nbt.NbtOps.INSTANCE
                val result = ItemStack.CODEC.encodeStart(registryLookup.getOps(nbtOps), stack)
                result.result().ifPresent { nbtTag ->
                    json.addProperty("nbt", nbtTag.toString())
                }
            }
        } catch (_: Exception) { }

        return json
    }

    private fun serializeComponents(stack: ItemStack, out: JsonObject, registryManager: net.minecraft.registry.RegistryWrapper.WrapperLookup?) {
        // custom_data (深度展开 NbtCompound)
        stack.get(DataComponentTypes.CUSTOM_DATA)?.let { customData ->
            out.add("minecraft:custom_data", nbtToJson(customData.copyNbt()))
        }

        // custom_name
        stack.get(DataComponentTypes.CUSTOM_NAME)?.let { name ->
            out.addProperty("minecraft:custom_name", name.string)
        }

        // lore
        stack.get(DataComponentTypes.LORE)?.let { lore ->
            val arr = JsonArray()
            lore.lines.forEach { line -> arr.add(line.string) }
            out.add("minecraft:lore", arr)
        }

        // enchantments
        stack.get(DataComponentTypes.ENCHANTMENTS)?.let { enchants ->
            val obj = JsonObject()
            enchants.enchantmentEntries.forEach { entry ->
                val key = entry.key.idAsString
                obj.addProperty(key, entry.intValue)
            }
            out.add("minecraft:enchantments", obj)
        }

        // damage / max_damage
        stack.get(DataComponentTypes.DAMAGE)?.let { out.addProperty("minecraft:damage", it) }
        stack.get(DataComponentTypes.MAX_DAMAGE)?.let { out.addProperty("minecraft:max_damage", it) }
    }

    private fun nbtToJson(nbt: NbtCompound): JsonObject {
        val obj = JsonObject()
        for (key in nbt.getKeys()) {
            obj.add(key, nbtElementToJson(nbt.get(key)))
        }
        return obj
    }

    private fun nbtElementToJson(element: NbtElement?): com.google.gson.JsonElement {
        if (element == null) return com.google.gson.JsonNull.INSTANCE
        return when (element) {
            is NbtCompound -> nbtToJson(element)
            is NbtList -> {
                val arr = JsonArray()
                element.forEach { arr.add(nbtElementToJson(it)) }
                arr
            }
            else -> {
                // 1.21.4: NbtElement.asString() 直接返回 String（1.21.11 才返回 Optional），无需 orElse
                val str = element.asString()
                str.toIntOrNull()?.let { return com.google.gson.JsonPrimitive(it) }
                str.toLongOrNull()?.let { return com.google.gson.JsonPrimitive(it) }
                str.toDoubleOrNull()?.let { return com.google.gson.JsonPrimitive(it) }
                com.google.gson.JsonPrimitive(str)
            }
        }
    }

    /**
     * 序列化容器中指定槽位的物品（简化版，不含 SNBT）。
     */
    fun serializeSlot(stack: ItemStack): JsonObject {
        if (stack.isEmpty) return JsonObject().apply { addProperty("empty", true) }
        val json = JsonObject()
        json.addProperty("empty", false)
        json.addProperty("itemId", Registries.ITEM.getId(stack.item).toString())
        json.addProperty("count", stack.count)
        val components = JsonObject()
        serializeComponents(stack, components, MinecraftClient.getInstance().world?.registryManager)
        json.add("components", components)
        return json
    }
}
