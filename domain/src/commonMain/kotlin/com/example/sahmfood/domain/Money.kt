package com.example.sahmfood.domain

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.roundToLong

@JvmInline
value class Money private constructor(val minorUnits: Long) {

    operator fun plus(other: Money)  = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(minorUnits - other.minorUnits)
    operator fun times(qty: Int)     = Money(minorUnits * qty)

    fun percent(bps: Int): Money = Money((minorUnits * bps) / BPS_BASE)

    fun formatted(currency: String = "EGP"): String {
        val absUnits = abs(minorUnits)
        val whole = absUnits / 100
        val frac  = absUnits % 100
        val sign  = if (minorUnits < 0) "-" else ""
        return "$currency $sign$whole.${frac.toString().padStart(2, '0')}"
    }

    companion object {
        private const val BPS_BASE = 10_000L
        val ZERO = Money(0)
        fun of(major: Double): Money = Money((major * 100).roundToLong())
        fun fromMinor(minorUnits: Long): Money = Money(minorUnits)
    }
}
