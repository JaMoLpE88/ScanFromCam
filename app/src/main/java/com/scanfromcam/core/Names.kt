package com.scanfromcam.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
private val SPACES = Regex("\\s+")

/**
 * Turns arbitrary user input into a safe document name.
 * Never returns blank: falls back to a timestamped default.
 */
fun sanitizeName(
    raw: String,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
    prefix: String = "Escaneo",
): String {
    val cleaned = raw
        .replace(ILLEGAL, "")
        .replace(SPACES, " ")
        .trim()
        .take(80)
    if (cleaned.isNotEmpty()) return cleaned

    val format = SimpleDateFormat("yyyy-MM-dd HH.mm", Strings.t.locale)
    format.timeZone = zone
    return "$prefix ${format.format(Date(now))}"
}
