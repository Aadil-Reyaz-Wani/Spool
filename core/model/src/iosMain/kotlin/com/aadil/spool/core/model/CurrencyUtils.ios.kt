package com.aadil.spool.core.model

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.currencyCode

actual fun defaultCurrencyCode(): String {
    return NSLocale.currentLocale.currencyCode ?: "USD"
}
