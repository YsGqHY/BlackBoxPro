package com.blackboxpro.fabric.http

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object ResponseFutureRegistry {

    private val gson = Gson()
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun register(id: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        pending[id] = future
        return future
    }

    fun onResponseJson(json: String) {
        val id = try {
            gson.fromJson(json, JsonObject::class.java)?.get("id")?.asString
        } catch (e: Exception) { null }
        if (id != null) pending.remove(id)?.complete(json)
    }

    fun cancel(id: String) {
        pending.remove(id)
    }
}
