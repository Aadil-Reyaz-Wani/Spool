package com.aadil.spool.data.mapper

import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.screens.entry.SpoolEntryUiState

fun SpoolEntryUiState.toFilament(): Filament {
    return Filament(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toDoubleOrNull() ?: 0.0,
        currentWeight = currentWeight.toDoubleOrNull() ?: 0.0,
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toIntOrNull() ?: 0,
        tempBed = tempBed.toIntOrNull() ?: 0,
        note = note,
        price = price.toDoubleOrNull() ?: 0.0
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