package com.aadil.spool.utils

import java.text.NumberFormat
import java.util.Currency

fun Double.formatAsCurrency(currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance()
    format.currency = Currency.getInstance(currencyCode)
    return format.format(this)
}