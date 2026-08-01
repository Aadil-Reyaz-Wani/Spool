package com.aadil.spool.core.network

import com.aadil.spool.core.model.FAQ
import com.aadil.spool.core.model.SpoolLists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface SpoolNetworkService {
    fun getFaqsStream(): Flow<List<FAQ>>
}

class DefaultSpoolNetworkService : SpoolNetworkService {
    override fun getFaqsStream(): Flow<List<FAQ>> = flow {
        emit(SpoolLists.faqItem)
    }
}
