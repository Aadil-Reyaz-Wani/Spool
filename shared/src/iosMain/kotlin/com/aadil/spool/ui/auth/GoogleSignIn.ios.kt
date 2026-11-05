package com.aadil.spool.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aadil.spool.core.network.GoogleCredentials

@Composable
actual fun rememberGoogleSignIn(webClientId: String): GoogleSignIn = remember {
    object : GoogleSignIn {
        override val supported = false
        override suspend fun requestCredentials(): GoogleCredentials = error("Google sign-in is not enabled on iOS")
        override suspend fun clearCredentialState() = Unit
    }
}
