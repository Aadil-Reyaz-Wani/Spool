package com.aadil.spool.core.model

import java.util.Currency
import java.util.Locale

actual fun defaultCurrencyCode(): String {
    return try {
        Currency.getInstance(Locale.getDefault()).currencyCode
    } catch (e: Exception) {
        "USD"
    }
}
