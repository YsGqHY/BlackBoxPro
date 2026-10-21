package com.blackboxpro.fabric.action.player

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

class OpenHorseInventoryAction : PlayerCommandAction(
    ClientCommandC2SPacket.Mode.OPEN_INVENTORY,
    "Opened horse inventory"
)
