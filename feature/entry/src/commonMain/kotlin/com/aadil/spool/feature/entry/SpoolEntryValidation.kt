package com.aadil.spool.feature.entry

enum class SpoolEntryError { INVALID_INPUT, INVALID_RESTOCK, LOAD_FAILED, SAVE_FAILED, CONCURRENT_UPDATE }

internal fun String.entryNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
private fun String.optionalNumber(): Double? = if (isBlank()) 0.0 else entryNumber()

internal fun SpoolEntryUiState.validationError(isEdit: Boolean): SpoolEntryError? {
    val total = totalWeight.entryNumber()
    val remaining = if (currentWeight.isBlank() && !isEdit) total else currentWeight.entryNumber()
    val cost = price.optionalNumber()
    if (brand.isBlank() || material.isBlank() || total == null || total <= 0.0 ||
        remaining == null || remaining < 0.0 || remaining > total || cost == null || cost < 0.0
    ) return SpoolEntryError.INVALID_INPUT

    if (isEdit) {
        val extraWeight = addedWeight.optionalNumber()
        val extraPrice = addedPrice.optionalNumber()
        if (extraWeight == null || extraWeight < 0.0 || extraPrice == null || extraPrice < 0.0 ||
            !(total + extraWeight).isFinite() || !(remaining + extraWeight).isFinite() ||
            !(cost + extraPrice).isFinite()
        ) return SpoolEntryError.INVALID_RESTOCK
    }
    return null
}
