package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "AdVmAuth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface OAuthOutcome {
    data object Success : OAuthOutcome
    data object Cancelled : OAuthOutcome
    data class ProviderNotConfigured(val providerName: String, val message: String) : OAuthOutcome
    data class Error(val message: String) : OAuthOutcome
}

object AuthManager {

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        // Exactly one credential option per GetCredentialRequest for API 36 compatibility
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    suspend fun signInWithGoogleInteractive(
        context: Context,
        credentialManager: CredentialManager
    ): OAuthOutcome {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return OAuthOutcome.Error("Google Sign-In configuration missing: default_web_client_id not found.")
        }
        val activity = context as? Activity
            ?: return OAuthOutcome.Error("An active Activity context is required for Google Sign-In.")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                OAuthOutcome.Success
            } else {
                OAuthOutcome.Error("Unexpected credential type received from Credential Manager.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            OAuthOutcome.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            OAuthOutcome.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }

    suspend fun signInWithFederatedOAuth(
        context: Context,
        providerId: String,
        providerDisplayName: String
    ): OAuthOutcome {
        val activity = context as? Activity
            ?: return OAuthOutcome.Error("An active Activity is required for $providerDisplayName sign-in.")
        val provider = OAuthProvider.newBuilder(providerId).apply {
            if (providerId == "apple.com") {
                addCustomParameter("locale", "en")
            } else if (providerId == "microsoft.com") {
                addCustomParameter("prompt", "select_account")
            }
        }.build()

        return try {
            val auth = Firebase.auth
            val pending = auth.pendingAuthResult
            if (pending != null) {
                pending.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            OAuthOutcome.Success
        } catch (e: FirebaseAuthException) {
            Log.w(TAG, "$providerDisplayName OAuth error (${e.errorCode}): ${e.message}")
            val code = e.errorCode.uppercase()
            if (code.contains("OPERATION_NOT_ALLOWED") || code.contains("INVALID_APP_CREDENTIAL") || code.contains("INTERNAL_ERROR")) {
                OAuthOutcome.ProviderNotConfigured(
                    providerName = providerDisplayName,
                    message = "$providerDisplayName OAuth requires enabling the $providerId provider in your Firebase Console. Open the setup guide below for exact instructions."
                )
            } else {
                OAuthOutcome.Error(e.localizedMessage ?: "$providerDisplayName sign-in failed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "$providerDisplayName OAuth failed", e)
            val msg = e.localizedMessage ?: "$providerDisplayName sign-in failed."
            if (msg.contains("cancelled", ignoreCase = true) || msg.contains("canceled", ignoreCase = true)) {
                OAuthOutcome.Cancelled
            } else if (msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) || msg.contains("not enabled", ignoreCase = true)) {
                OAuthOutcome.ProviderNotConfigured(providerDisplayName, msg)
            } else {
                OAuthOutcome.Error(msg)
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
