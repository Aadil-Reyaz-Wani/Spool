package com.aadil.spool.utils

import java.text.DateFormat
import java.util.Locale


fun Long.toReadableDate() : String {
    val dateFormatter = DateFormat.getDateInstance(
        DateFormat.SHORT,
        Locale.getDefault()
    )
    val timeFormatter = DateFormat.getTimeInstance(
        DateFormat.SHORT,
        Locale.getDefault()
    )
    return "${dateFormatter.format(this)}, ${timeFormatter.format(this)}"
}