package com.dermoai.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Credential Manager + Google ID token helper for Sign-In with Google.
 *
 * Requires a real OAuth web client ID in [R.string.default_web_client_id]
 * when Firebase is provisioned. Placeholder projects will fail gracefully.
 */
class CredentialGoogleSignInHelper(
    private val context: Context,
) : GoogleSignInHelper {

    private val credentialManager = CredentialManager.create(context)

    override suspend fun requestIdToken(): GoogleSignInResult {
        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank() || webClientId.contains("placeholder", ignoreCase = true)) {
            return GoogleSignInResult.Error(
                "Google Sign-In needs a real Firebase OAuth client ID. Use email sign-in for now.",
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = context,
                request = request,
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleSignInResult.Error("Unexpected credential type from Google Sign-In.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Error("No Google account available on this device.")
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.Error("Could not parse Google ID token.")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error(e.message ?: "Google Sign-In failed.")
        } catch (e: Exception) {
            GoogleSignInResult.Error(e.message ?: "Google Sign-In failed.")
        }
    }
}
