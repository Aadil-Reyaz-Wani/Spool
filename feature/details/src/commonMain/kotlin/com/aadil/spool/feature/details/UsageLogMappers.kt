package com.aadil.spool.feature.details

import com.aadil.spool.core.model.getCurrentTimeMillis
import com.aadil.spool.data.entity.UsageLog

fun PrintObjectUiState.toUsageLog(): UsageLog {
    require(id == 0 || existingLog?.id == id) { "Editing a usage log requires its stored entity" }
    val base = existingLog ?: UsageLog(
        spoolId = spoolId,
        title = printTitle,
        gramsUsed = 0.0,
        timestamp = getCurrentTimeMillis(),
        isFailure = isFailed,
    )
    return base.copy(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toDoubleOrNull() ?: 0.0,
        title = printTitle,
        isFailure = isFailed,
    )
}

fun UsageLog.toPrintObjectUiState(): PrintObjectUiState {
    return PrintObjectUiState(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toString(),
        printTitle = title,
        isFailed = isFailure,
        existingLog = this,
    )
}
