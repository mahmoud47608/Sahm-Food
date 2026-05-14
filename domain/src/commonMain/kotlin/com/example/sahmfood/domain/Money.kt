package com.example.sahmfood.domain

data class Money(val minorUnits: Long) {

    operator fun plus(other: Money)  = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(minorUnits - other.minorUnits)
    operator fun times(qty: Int)     = Money(minorUnits * qty)

    fun percent(bps: Int): Money = Money((minorUnits * bps) / 10_000L)

    fun formatted(currency: String = "EGP"): String {
        val whole = minorUnits / 100
        val frac  = (minorUnits.let { if (it < 0) -it else it }) % 100
        val sign  = if (minorUnits < 0) "-" else ""
        return "$currency $sign${if (minorUnits < 0) -whole else whole}.${frac.toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Money(0)
        fun of(major: Double): Money = Money((major * 100).toLong())
    }
}
