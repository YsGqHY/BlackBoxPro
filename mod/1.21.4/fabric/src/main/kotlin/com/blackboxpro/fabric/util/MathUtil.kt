package com.blackboxpro.fabric.util

import com.blackboxpro.runtime.util.RuntimeMathUtil

fun calculateYawPitch(dx: Double, dy: Double, dz: Double): Pair<Float, Float> =
    RuntimeMathUtil.calculateYawPitch(dx, dy, dz)
