package com.aadil.spool.feature.details

import com.aadil.spool.core.model.getCurrentTimeMillis
import com.aadil.spool.data.entity.UsageLog

fun PrintObjectUiState.toUsageLog(): UsageLog {
    return UsageLog(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toDoubleOrNull() ?: 0.0,
        title = printTitle,
        isFailure = isFailed,
        timestamp = getCurrentTimeMillis(),
    )
}

fun UsageLog.toPrintObjectUiState(): PrintObjectUiState {
    return PrintObjectUiState(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toString(),
        printTitle = title,
        isFailed = isFailure,
    )
}
