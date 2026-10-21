package com.blackboxpro.fabric.util

import com.blackboxpro.runtime.util.RuntimeJsonUtil
import com.google.gson.JsonObject

fun JsonObject.getStringOrNull(key: String): String? = RuntimeJsonUtil.getStringOrNull(this, key)

fun JsonObject.getIntOrDefault(key: String, default: Int): Int =
    RuntimeJsonUtil.getIntOrDefault(this, key, default)

fun JsonObject.getLongOrDefault(key: String, default: Long): Long =
    RuntimeJsonUtil.getLongOrDefault(this, key, default)

fun JsonObject.getDoubleOrDefault(key: String, default: Double): Double =
    RuntimeJsonUtil.getDoubleOrDefault(this, key, default)

fun JsonObject.getBooleanOrDefault(key: String, default: Boolean): Boolean =
    RuntimeJsonUtil.getBooleanOrDefault(this, key, default)

fun JsonObject.getFloatOrDefault(key: String, default: Float): Float =
    RuntimeJsonUtil.getFloatOrDefault(this, key, default)

fun JsonObject.requireString(key: String): String = RuntimeJsonUtil.requireString(this, key)

fun JsonObject.requireInt(key: String): Int = RuntimeJsonUtil.requireInt(this, key)

fun JsonObject.requireLong(key: String): Long = RuntimeJsonUtil.requireLong(this, key)

fun JsonObject.requireDouble(key: String): Double = RuntimeJsonUtil.requireDouble(this, key)

fun JsonObject.requireBoolean(key: String): Boolean = RuntimeJsonUtil.requireBoolean(this, key)
