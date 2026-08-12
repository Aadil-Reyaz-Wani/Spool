package com.aadil.spool

import com.aadil.spool.core.model.MaterialMoisture
import com.aadil.spool.core.model.MoistureLevel
import com.aadil.spool.core.model.materialMoisture
import com.aadil.spool.core.model.moistureVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoistureVerdictTest {

    @Test
    fun noBaselineIsUnknown() {
        val verdict = moistureVerdict("PLA", null, 500.0, null, null)
        assertEquals(MoistureLevel.UNKNOWN, verdict.level)
        assertEquals(0.0, verdict.absorbedGrams)
        assertFalse(verdict.tareMismatch)
    }

    @Test
    fun dryPlaBelowThreshold() {
        val verdict = moistureVerdict("PLA", 500.0, 505.0, 140.0, 140.0)
        assertEquals(MoistureLevel.DRY, verdict.level)
        assertEquals(5.0, verdict.absorbedGrams)
    }

    @Test
    fun lowPlaBetweenThresholds() {
        assertEquals(MoistureLevel.LOW, moistureVerdict("PLA", 500.0, 512.0, 140.0, 140.0).level)
    }

    @Test
    fun highPlaAboveUpperThreshold() {
        assertEquals(MoistureLevel.HIGH, moistureVerdict("PLA", 500.0, 520.0, 140.0, 140.0).level)
    }

    @Test
    fun thirstyTpuIsStricter() {
        assertEquals(MoistureLevel.DRY, moistureVerdict("TPU", 500.0, 502.0, 140.0, 140.0).level)
        assertEquals(MoistureLevel.HIGH, moistureVerdict("TPU", 500.0, 510.0, 140.0, 140.0).level)
    }

    @Test
    fun negativeDeltaClampsToZero() {
        val verdict = moistureVerdict("PETG", 500.0, 460.0, 140.0, 140.0)
        assertEquals(0.0, verdict.absorbedGrams)
        assertEquals(MoistureLevel.DRY, verdict.level)
    }

    @Test
    fun unknownMaterialFallsBackToMedium() {
        val verdict = moistureVerdict("Generic-99", 500.0, 510.0, 140.0, 140.0)
        assertEquals(MaterialMoisture.MEDIUM.dryTempC, verdict.dryTempC)
        assertEquals(MoistureLevel.LOW, verdict.level)
    }

    @Test
    fun tareMismatchDetected() {
        assertFalse(moistureVerdict("PLA", 500.0, 505.0, 140.0, 140.0).tareMismatch)
        assertTrue(moistureVerdict("PLA", 500.0, 505.0, 140.0, 220.0).tareMismatch)
        assertFalse(moistureVerdict("PLA", 500.0, 505.0, null, null).tareMismatch)
    }

    @Test
    fun materialMoistureLookupNormalizesInput() {
        assertEquals(MaterialMoisture.LOW, materialMoisture("  pla "))
        assertEquals(MaterialMoisture.VERY_HIGH, materialMoisture("pa-cf"))
        assertEquals(MaterialMoisture.MEDIUM, materialMoisture("random"))
    }

    @Test
    fun highVerdictCarriesDryingAdvice() {
        val verdict = moistureVerdict("PA", 500.0, 520.0, 140.0, 140.0)
        assertEquals(MaterialMoisture.VERY_HIGH.dryHours, verdict.dryHours)
        assertEquals(MaterialMoisture.VERY_HIGH.dryTempC, verdict.dryTempC)
    }
}
