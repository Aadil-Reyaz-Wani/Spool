package com.aadil.spool

import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.feature.details.PrintObjectUiState
import com.aadil.spool.feature.details.toPrintObjectUiState
import com.aadil.spool.feature.details.toUsageLog
import com.aadil.spool.feature.entry.toFilament
import com.aadil.spool.feature.entry.toSpoolEntryUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EntityIdentityTest {
    @Test fun newEntitiesGetDistinctValidUuidsAndCopiesKeepThem() {
        val spool = testSpool()
        val log = UsageLog(spoolId = spool.id, title = "Print", gramsUsed = 10.0, timestamp = 123, isFailure = false)
        val identities = List(100) { testSpool().uuid } + List(100) {
            UsageLog(spoolId = spool.id, title = "Print", gramsUsed = 10.0, timestamp = 123, isFailure = false).uuid
        }
        identities.forEach { assertEquals(it, Uuid.parse(it).toString()) }
        assertEquals(identities.size, identities.toSet().size)
        assertEquals(spool.uuid, spool.copy(note = "Edited").uuid)
        assertEquals(log.uuid, log.copy(title = "Edited").uuid)
        assertNotEquals(log.uuid, UsageLog(spoolId = spool.id, title = log.title, gramsUsed = log.gramsUsed, timestamp = log.timestamp, isFailure = log.isFailure).uuid)
    }

    @Test fun reconstructionKeepsIdentityTimestampAndUneditedFields() {
        val spool = testSpool()
        assertEquals(spool, spool.toSpoolEntryUiState().toFilament(spool))
        val log = UsageLog(id = 4, spoolId = spool.id, title = "Print", gramsUsed = 12.5, timestamp = 123, isFailure = true, pricePerPrint = 1.25)
        assertEquals(log, log.toPrintObjectUiState().toUsageLog())
        assertEquals(log.copy(title = "Edited"), log.toPrintObjectUiState().copy(printTitle = "Edited").toUsageLog())
        assertNotEquals(log.uuid, PrintObjectUiState().toUsageLog().uuid)
    }

    @Test fun editsCannotSilentlyGenerateReplacementIdentities() {
        assertFailsWith<IllegalArgumentException> { testSpool().toSpoolEntryUiState().toFilament() }
        assertFailsWith<IllegalArgumentException> { PrintObjectUiState(id = 4).toUsageLog() }
    }
}
