package com.aadil.spool.data.mapper

import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.screens.entry.SpoolEntryUiState
import com.aadil.spool.utils.toParseCurrencyToDouble
import com.aadil.spool.utils.toParseLocalizedDouble
import com.aadil.spool.utils.toParseLocalizedInt

fun SpoolEntryUiState.toFilament(): Filament {
    return Filament(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toParseLocalizedDouble() ?: 0.0,
        currentWeight = currentWeight.toParseLocalizedDouble() ?: 0.0,
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toParseLocalizedInt() ?: 0,
        tempBed = tempBed.toParseLocalizedInt() ?: 0,
        note = note,
        price = price.toParseCurrencyToDouble() ?: 0.0
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