package com.aadil.spool.core.model

expect fun getCurrentTimeMillis(): Long
inline fun currentTimeMillis(): Long = getCurrentTimeMillis()
