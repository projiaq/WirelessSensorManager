package com.example.wirelesssensormanager.core.protocol

object DeviceIdentityParser {
    private val macPattern = Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}|[0-9a-f]{12}")
    fun extractMac(value: String): String? = macPattern.find(value)?.value?.filter(Char::isLetterOrDigit)?.chunked(2)?.joinToString(":") { it.uppercase() }
}
