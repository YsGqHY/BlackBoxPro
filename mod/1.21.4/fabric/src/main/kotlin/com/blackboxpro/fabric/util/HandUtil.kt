package com.blackboxpro.fabric.util

import net.minecraft.util.Hand

object HandUtil {
    fun fromString(hand: String): Hand = when (hand.lowercase()) {
        "main_hand", "mainhand", "main" -> Hand.MAIN_HAND
        "off_hand", "offhand", "off" -> Hand.OFF_HAND
        else -> throw IllegalArgumentException("Unknown hand: $hand")
    }
}
