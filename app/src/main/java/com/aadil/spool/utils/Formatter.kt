package com.aadil.spool.utils

import java.text.NumberFormat
import java.text.ParseException
import java.util.Currency
import java.util.Locale

fun Double.formatAsCurrency(currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance()
    format.currency = Currency.getInstance(currencyCode)
    return format.format(this)
}

fun Double.formatToInternationalStandard(maxDecimals: Int = 1) : String {
    val format = NumberFormat.getInstance(Locale.getDefault()).apply {
        maximumFractionDigits = maxDecimals
    }
    return format.format(this)
}


fun String.toParseLocalizedDouble() : Double? {
    if (this.isBlank()) return null

    return try {
        val format = NumberFormat.getInstance(Locale.getDefault())
        val number = format.parse(this)
        number?.toDouble()
    }catch (e: ParseException) {
        null
    }
}

fun String.toParseLocalizedInt() : Int? {
    if (this.isBlank()) return null

    return try {
        val format = NumberFormat.getInstance(Locale.getDefault())
        format.isParseIntegerOnly = true
        val number = format.parse(this)
        number?.toInt()
    }catch (e: ParseException) {
        null
    }
}

fun String.toParseCurrencyToDouble() : Double? {
    if (this.isBlank()) return null

    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val number = format.parse(this)
        number?.toDouble()
    }catch (e: ParseException) {
        this.toParseLocalizedDouble()
    }
}