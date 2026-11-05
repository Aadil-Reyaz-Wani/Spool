package com.aadil.spool.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteInventorySpool(
    @SerialName("owner_id") val ownerId: String,
    val uuid: String,
    val revision: Long,
    @SerialName("deleted_at") val deletedAt: String?,
    val brand: String,
    val material: String,
    @SerialName("color_name") val colorName: String,
    @SerialName("color_hex") val colorHex: Long,
    @SerialName("total_weight") val totalWeight: Double,
    @SerialName("current_weight") val currentWeight: Double,
    @SerialName("temp_nozzle") val tempNozzle: Int,
    @SerialName("temp_bed") val tempBed: Int,
    val note: String,
    val price: Double,
    val timestamp: Long,
    @SerialName("dry_baseline_weight") val dryBaselineWeight: Double?,
    @SerialName("dry_baseline_tare_grams") val dryBaselineTareGrams: Double?,
    @SerialName("last_weighed_tare_grams") val lastWeighedTareGrams: Double?,
    @SerialName("last_dried_at") val lastDriedAt: Long?,
)

@Serializable
data class RemoteInventoryLog(
    @SerialName("owner_id") val ownerId: String,
    val uuid: String,
    @SerialName("spool_uuid") val spoolUuid: String,
    @SerialName("deleted_at") val deletedAt: String?,
    val title: String,
    @SerialName("grams_used") val gramsUsed: Double,
    val timestamp: Long,
    @SerialName("is_failure") val isFailure: Boolean,
    @SerialName("price_per_print") val pricePerPrint: Double,
)

@Serializable
data class RemoteInventorySnapshot(val spool: RemoteInventorySpool, val logs: List<RemoteInventoryLog>)

@Serializable
data class InventoryPullPage(
    @SerialName("owner_id") val ownerId: String,
    val cursor: Long,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
    val snapshot: RemoteInventorySnapshot?,
)
