package com.blackboxpro.fabric.action.player

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

class HorseJumpStopAction : PlayerCommandAction(
    ClientCommandC2SPacket.Mode.STOP_RIDING_JUMP,
    "Stopped horse jump"
)
