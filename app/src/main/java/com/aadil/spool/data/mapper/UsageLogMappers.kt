package com.aadil.spool.data.mapper

import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.ui.screens.details.PrintObjectUiState


fun PrintObjectUiState.toUsageLog(): UsageLog {
    return UsageLog(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toDoubleOrNull() ?: 0.0,
        title = printTitle,
        isFailure = isFailed,
        timestamp = System.currentTimeMillis(),
    )
}