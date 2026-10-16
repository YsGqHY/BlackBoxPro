package com.blackboxpro.fabric.action.player

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

class HorseJumpStartAction : PlayerCommandAction(
    ClientCommandC2SPacket.Mode.START_RIDING_JUMP,
    "Started horse jump"
)
