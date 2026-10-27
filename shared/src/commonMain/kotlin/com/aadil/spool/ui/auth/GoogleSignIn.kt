package com.aadil.spool.ui.auth

import androidx.compose.runtime.Composable
import com.aadil.spool.core.network.GoogleCredentials

interface GoogleSignIn {
    val supported: Boolean
    suspend fun requestCredentials(): GoogleCredentials
    suspend fun clearCredentialState()
}

@Composable
expect fun rememberGoogleSignIn(webClientId: String): GoogleSignIn
