package com.aadil.spool.core.model

fun String.toParseLocalizedDouble(): Double? {
    if (this.isBlank()) return null
    val cleaned = this.trim()
        .replace(" ", "")
        .replace(",", ".")
        .filter { it.isDigit() || it == '.' || it == '-' }
    return cleaned.toDoubleOrNull()
}

fun String.toParseLocalizedInt(): Int? {
    if (this.isBlank()) return null
    val cleaned = this.trim()
        .replace(" ", "")
        .takeWhile { it != '.' && it != ',' }
        .filter { it.isDigit() || it == '-' }
    return cleaned.toIntOrNull()
}

fun String.toParseCurrencyToDouble(): Double? {
    return this.toParseLocalizedDouble()
}
