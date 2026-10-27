package com.aadil.spool.core.network.di

import org.koin.core.module.Module
import org.koin.dsl.module

// Native iOS authentication and Keychain persistence are a separate implementation step.
internal actual val platformNetworkModule: Module = module {}
