package com.lifo.util

import kotlinx.datetime.Clock

/**
 * KMP-safe number formatting (replaces String.format which is JVM-only).
 */
fun formatDecimal(decimals: Int, value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val factor = pow10(decimals)
    val rounded = kotlin.math.round(value * factor) / factor
    val intPart = rounded.toLong()
    val fracPart = kotlin.math.abs(((rounded - intPart) * factor).toLong())
    return if (decimals > 0) "$intPart.${fracPart.toString().padStart(decimals, '0')}" else "$intPart"
}

fun formatDecimal(decimals: Int, value: Float): String = formatDecimal(decimals, value.toDouble())

private fun pow10(n: Int): Double {
    var result = 1.0
    repeat(n) { result *= 10.0 }
    return result
}

/**
 * KMP-safe compact count formatting (e.g., 1.2K, 3.4M).
 */
fun formatCompactCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> {
        val k = count / 100
        "${k / 10}.${k % 10}K"
    }
    else -> {
        val m = count / 100_000
        "${m / 10}.${m % 10}M"
    }
}

/**
 * KMP-safe relative time formatting (e.g., "2h", "3d", "1w").
 */
fun formatRelativeTimeKmp(timestampMillis: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - timestampMillis
    if (diff < 0L) return "now"

    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L

    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 7L -> "${days}d"
        days < 30L -> "${days / 7}w"
        days < 365L -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}

/**
 * KMP-safe current time millis.
 */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
