package com.aadil.spool.ui.auth

import android.content.MutableContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.aadil.spool.core.network.GoogleCredentials
import com.aadil.spool.feature.settings.GoogleSignInCancelled
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

@Composable
actual fun rememberGoogleSignIn(webClientId: String): GoogleSignIn {
    val context = LocalContext.current
    val wrapper = remember { MutableContextWrapper(context) }
    SideEffect { wrapper.baseContext = context }
    return remember(webClientId) {
        object : GoogleSignIn {
            override val supported = true
            private val manager = CredentialManager.create(context.applicationContext)

            override suspend fun requestCredentials(): GoogleCredentials {
                val nonce = Base64.encodeToString(
                    ByteArray(32).also { SecureRandom().nextBytes(it) },
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
                // Supabase validates Google's SHA-256 nonce against the original random value.
                val hash = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(hash).build()
                val response = try {
                    manager.getCredential(wrapper, GetCredentialRequest.Builder().addCredentialOption(option).build())
                } catch (_: GetCredentialCancellationException) {
                    throw GoogleSignInCancelled()
                }
                val credential = response.credential
                check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                return GoogleCredentials(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
            }

            override suspend fun clearCredentialState() {
                manager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
