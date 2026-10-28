package com.aadil.spool.data.entity

data class InventoryUploadQueue(
    val pending: Int = 0,
    val ready: Int = 0,
    val conflicts: Int = 0,
    val latest: Long = 0,
)
