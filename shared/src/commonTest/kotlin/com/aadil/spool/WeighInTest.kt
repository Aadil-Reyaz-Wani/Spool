package com.aadil.spool

import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.core.model.computeRemainingWeight
import com.aadil.spool.core.model.isWeighInSuspicious
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeighInTest {

    @Test
    fun computesRemainingFromGrossAndTare() {
        assertEquals(400.0, computeRemainingWeight(540.0, SpoolLists.DEFAULT_TARE_GRAMS))
    }

    @Test
    fun rejectsWeightsAtOrBelowTare() {
        assertNull(computeRemainingWeight(100.0, SpoolLists.DEFAULT_TARE_GRAMS))
        assertNull(computeRemainingWeight(0.0, SpoolLists.DEFAULT_TARE_GRAMS))
    }

    @Test
    fun flagsDropsBeyondMargin() {
        assertTrue(isWeighInSuspicious(860.5, 759.5))
        assertTrue(isWeighInSuspicious(860.5, 700.0))
    }

    @Test
    fun ignoresDropsAtOrBelowMargin() {
        assertFalse(isWeighInSuspicious(860.5, 760.5))
        assertFalse(isWeighInSuspicious(860.5, 761.5))
        assertFalse(isWeighInSuspicious(860.5, 860.5))
        assertFalse(isWeighInSuspicious(860.5, 960.0))
    }

    @Test
    fun ignoresWhenNoEnteredRemaining() {
        assertFalse(isWeighInSuspicious(860.5, null))
    }
}
