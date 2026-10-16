package com.blackboxpro.runtime.util

import com.google.gson.JsonObject

object RuntimeJsonUtil {
    fun getStringOrNull(source: JsonObject, key: String): String? =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asString else null

    fun getIntOrDefault(source: JsonObject, key: String, default: Int): Int =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asInt else default

    fun getLongOrDefault(source: JsonObject, key: String, default: Long): Long =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asLong else default

    fun getDoubleOrDefault(source: JsonObject, key: String, default: Double): Double =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asDouble else default

    fun getBooleanOrDefault(source: JsonObject, key: String, default: Boolean): Boolean =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asBoolean else default

    fun getFloatOrDefault(source: JsonObject, key: String, default: Float): Float =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asFloat else default

    fun requireString(source: JsonObject, key: String): String =
        getStringOrNull(source, key) ?: throw IllegalArgumentException("Missing required field: $key")

    fun requireInt(source: JsonObject, key: String): Int =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asInt
        else throw IllegalArgumentException("Missing required field: $key")

    fun requireLong(source: JsonObject, key: String): Long =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asLong
        else throw IllegalArgumentException("Missing required field: $key")

    fun requireDouble(source: JsonObject, key: String): Double =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asDouble
        else throw IllegalArgumentException("Missing required field: $key")

    fun requireBoolean(source: JsonObject, key: String): Boolean =
        if (source.has(key) && !source.get(key).isJsonNull) source.get(key).asBoolean
        else throw IllegalArgumentException("Missing required field: $key")
}
