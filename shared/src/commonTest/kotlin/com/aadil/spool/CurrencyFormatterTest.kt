package com.aadil.spool

import com.aadil.spool.utils.formatAsCurrency
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyFormatterTest {
    @Test fun formatsRoundedIntegerCentsWithoutLosingPenniesToFloatingPoint() {
        assertEquals("₹1.20", 1.2.formatAsCurrency("INR"))
        assertEquals("$2.00", 1.999.formatAsCurrency("USD"))
        assertEquals("€10.02", 10.019.formatAsCurrency("EUR"))
        assertEquals("$0.01", 0.005.formatAsCurrency("USD"))
        assertEquals("$-0.50", (-0.5).formatAsCurrency("USD"))
        assertEquals("¥0.00", 0.0.formatAsCurrency("JPY"))
        assertEquals("$0.00", Double.NaN.formatAsCurrency("USD"))
    }
}
