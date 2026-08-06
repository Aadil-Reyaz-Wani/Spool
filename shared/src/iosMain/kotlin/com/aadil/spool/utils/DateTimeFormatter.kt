package com.aadil.spool.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle

actual fun Long.toReadableDate(): String {
    val date = NSDate(timeIntervalSinceReferenceDate = (this / 1000.0) - 978307200.0)
    val dateFormatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return dateFormatter.stringFromDate(date)
}
