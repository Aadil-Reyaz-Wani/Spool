package com.aadil.spool.utils

fun Double.formatAsCurrency(currencyCode: String): String {
    val symbol = when (currencyCode.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "INR" -> "₹"
        "CAD" -> "$"
        "AUD" -> "$"
        else -> "$currencyCode "
    }
    val rounded = (this * 100.0).toLong() / 100.0
    val integerPart = rounded.toLong()
    val fracPart = ((rounded - integerPart) * 100).toLong().let { if (it < 0) -it else it }
    val fracStr = if (fracPart < 10) "0$fracPart" else "$fracPart"
    return "$symbol$integerPart.$fracStr"
}

fun Double.formatToInternationalStandard(maxDecimals: Int = 1): String {
    val factor = when (maxDecimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 10.0
    }
    val rounded = (this * factor).toLong() / factor
    return rounded.toString()
}

fun String.toParseLocalizedDouble(): Double? {
    if (this.isBlank()) return null
    return this.replace(",", ".").toDoubleOrNull()
}

fun String.toParseLocalizedInt(): Int? {
    if (this.isBlank()) return null
    return this.replace(",", "").toIntOrNull()
}

fun String.toParseCurrencyToDouble(): Double? {
    if (this.isBlank()) return null
    val clean = this.filter { it.isDigit() || it == '.' || it == '-' }
    return clean.toDoubleOrNull() ?: this.toParseLocalizedDouble()
}
