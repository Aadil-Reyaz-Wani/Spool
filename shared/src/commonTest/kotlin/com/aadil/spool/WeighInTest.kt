package com.aadil.spool

import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.core.model.computeRemainingWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
