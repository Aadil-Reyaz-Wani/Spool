package com.aadil.spool.feature.entry

import com.aadil.spool.core.model.toParseLocalizedInt
import com.aadil.spool.data.entity.Filament
import kotlinx.serialization.Serializable

@Serializable
data class SpoolEntryUiState(
    val id: Int = 0,
    val brand: String = "",
    val material: String = "",
    val totalWeight: String = "",
    val colorName: String = "",
    val colorHex: Long = 0xFF000000,
    val currentWeight: String = "",
    val tempNozzle: String = "",
    val tempBed: String = "",
    val note: String = "",
    val price: String = "",
    val addedWeight: String = "",
    val addedPrice: String = ""
)

fun SpoolEntryUiState.toFilament(existing: Filament? = null): Filament {
    require(id == 0 || existing?.id == id) { "Editing a spool requires its stored entity" }
    val base = existing ?: Filament(
        brand = brand,
        material = material,
        colorName = colorName,
        colorHex = colorHex,
        note = note,
    )
    return base.copy(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.entryNumber() ?: 0.0,
        currentWeight = if (currentWeight.isBlank() && existing == null) {
            totalWeight.entryNumber() ?: 0.0
        } else currentWeight.entryNumber() ?: 0.0,
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toParseLocalizedInt() ?: 0,
        tempBed = tempBed.toParseLocalizedInt() ?: 0,
        note = note,
        price = price.entryNumber() ?: 0.0
    )
}

fun Filament.toSpoolEntryUiState(): SpoolEntryUiState {
    return SpoolEntryUiState(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toString(),
        currentWeight = currentWeight.toString(),
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toString(),
        tempBed = tempBed.toString(),
        note = note,
        price = price.toString()
    )
}
