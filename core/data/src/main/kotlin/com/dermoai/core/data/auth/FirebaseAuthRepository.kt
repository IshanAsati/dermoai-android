package com.dermoai.core.data.auth

import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.data.preferences.UserPreferencesDataStore
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.entity.UserProfileEntity
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.model.UserRole
import com.dermoai.core.domain.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Auth implementation with a local session fallback when the
 * project still uses the placeholder `google-services.json`.
 *
 * Local mode keeps the auth UX navigable on device without a real Firebase project.
 * Production builds should drop in a real Firebase config so Firebase is used end-to-end.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val preferences: UserPreferencesDataStore,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    private val localUser = MutableStateFlow<AuthUser?>(null)
    private val firebaseAuth: FirebaseAuth? = resolveFirebaseAuth()
    private val localMode: Boolean = firebaseAuth == null || isPlaceholderProject()

    override fun isLocalAuthMode(): Boolean = localMode

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAuthState(): Flow<AuthUser?> {
        return if (!localMode && firebaseAuth != null) {
            callbackFlowAuth(firebaseAuth)
        } else {
            preferences.activeUserId.flatMapLatest { userId ->
                val cached = localUser.value
                when {
                    cached != null && (userId == null || cached.id == userId) -> flowOf(cached)
                    userId.isNullOrBlank() -> {
                        localUser.value = null
                        flowOf(null)
                    }
                    else -> userProfileDao.observeById(userId).map { profile ->
                        val user = profile?.toAuthUser()
                        if (user != null) {
                            localUser.value = user
                        }
                        user
                    }
                }
            }.distinctUntilChanged()
        }
    }

    override suspend fun getCurrentUser(): AuthUser? = withContext(dispatchers.io) {
        if (!localMode) {
            firebaseAuth?.currentUser?.toAuthUser()?.also { return@withContext it }
        }
        localUser.value
            ?: preferences.activeUserId.first()?.let { id ->
                userProfileDao.getById(id)?.toAuthUser()?.also { localUser.value = it }
            }
    }

    override suspend fun signInWithEmail(email: String, password: String): AppResult<AuthUser> =
        withContext(dispatchers.io) {
            if (localMode) {
                localSignIn(email, password)
            } else {
                runCatching {
                    val result = firebaseAuth!!
                        .signInWithEmailAndPassword(email, password)
                        .await()
                    val user = result.user?.toAuthUser()
                        ?: return@runCatching AppResult.Error(
                            IllegalStateException("No user after sign-in"),
                            "Sign-in failed. Please try again.",
                        )
                    persistSession(user)
                    AppResult.Success(user)
                }.getOrElse { mapAuthError(it, "Sign-in failed") }
            }
        }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<AuthUser> = withContext(dispatchers.io) {
        if (localMode) {
            localSignUp(email, password, displayName)
        } else {
            runCatching {
                val result = firebaseAuth!!
                    .createUserWithEmailAndPassword(email, password)
                    .await()
                val firebaseUser = result.user
                    ?: return@runCatching AppResult.Error(
                        IllegalStateException("No user after sign-up"),
                        "Could not create account. Please try again.",
                    )
                if (displayName.isNotBlank()) {
                    firebaseUser.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build(),
                    ).await()
                }
                val user = firebaseUser.toAuthUser().copy(
                    displayName = displayName.ifBlank { firebaseUser.displayName.orEmpty() },
                )
                persistSession(user)
                AppResult.Success(user)
            }.getOrElse { mapAuthError(it, "Sign-up failed") }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser> =
        withContext(dispatchers.io) {
            if (localMode) {
                AppResult.Error(
                    IllegalStateException("Google Sign-In requires a provisioned Firebase project"),
                    "Google Sign-In is unavailable until Firebase is configured. Use email and password for now.",
                )
            } else {
                runCatching {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val result = firebaseAuth!!.signInWithCredential(credential).await()
                    val user = result.user?.toAuthUser()
                        ?: return@runCatching AppResult.Error(
                            IllegalStateException("No user after Google sign-in"),
                            "Google Sign-In failed. Please try again.",
                        )
                    persistSession(user)
                    AppResult.Success(user)
                }.getOrElse { mapAuthError(it, "Google Sign-In failed") }
            }
        }

    override suspend fun signOut(): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            if (!localMode) {
                firebaseAuth?.signOut()
            }
            localUser.value = null
            preferences.setActiveUserId(null)
            AppResult.Success(Unit)
        }.getOrElse { AppResult.Error(it, it.message ?: "Sign-out failed") }
    }

    private suspend fun localSignIn(email: String, password: String): AppResult<AuthUser> {
        if (!isValidEmail(email) || password.length < 6) {
            return AppResult.Error(
                IllegalArgumentException("Invalid credentials"),
                "Enter a valid email and a password with at least 6 characters.",
            )
        }
        val userId = localUserId(email)
        val existing = userProfileDao.getById(userId)
        val user = if (existing != null) {
            existing.toAuthUser()
        } else {
            AuthUser(
                id = userId,
                email = email,
                displayName = email.substringBefore("@"),
            )
        }
        persistSession(user)
        localUser.value = user
        return AppResult.Success(user)
    }

    private suspend fun localSignUp(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<AuthUser> {
        if (!isValidEmail(email)) {
            return AppResult.Error(
                IllegalArgumentException("Invalid email"),
                "Enter a valid email address.",
            )
        }
        if (password.length < 6) {
            return AppResult.Error(
                IllegalArgumentException("Password too short"),
                "Password must be at least 6 characters.",
            )
        }
        val user = AuthUser(
            id = localUserId(email),
            email = email,
            displayName = displayName.ifBlank { email.substringBefore("@") },
        )
        persistSession(user)
        localUser.value = user
        return AppResult.Success(user)
    }

    private suspend fun persistSession(user: AuthUser) {
        val now = System.currentTimeMillis()
        val existing = userProfileDao.getById(user.id)
        userProfileDao.upsert(
            UserProfileEntity(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncStatus = if (localMode) {
                    UserProfileEntity.SYNC_PENDING
                } else {
                    UserProfileEntity.SYNCED
                },
                // ── doctor dashboard: role plumbing only ──────────────────────
                // An existing role always wins. Firebase's user object carries no
                // role, so `user.role` is the PATIENT default on every non-local
                // sign-in, and taking it here would silently demote a doctor on
                // their next launch. Role *changes* belong to the doctor feature;
                // auth only seeds the default for a brand-new profile row.
                role = existing?.role ?: user.role.name,
            ),
        )
        preferences.setActiveUserId(user.id)
    }

    private fun callbackFlowAuth(auth: FirebaseAuth): Flow<AuthUser?> =
        kotlinx.coroutines.flow.callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser?.toAuthUser())
            }
            auth.addAuthStateListener(listener)
            trySend(auth.currentUser?.toAuthUser())
            awaitClose { auth.removeAuthStateListener(listener) }
        }

    // Role is deliberately absent here: a FirebaseUser carries no role claim, so
    // this mapping leaves AuthUser.role at its PATIENT default. Callers that need
    // the persisted role in Firebase mode should read it from user_profiles via
    // DoctorProfile/UserProfileDao rather than trusting this object.
    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        id = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty().ifBlank { email?.substringBefore("@").orEmpty() },
        isAnonymous = isAnonymous,
        photoUrl = photoUrl?.toString(),
    )

    private fun UserProfileEntity.toAuthUser(): AuthUser = AuthUser(
        id = id,
        email = email,
        displayName = displayName,
        // ── doctor dashboard: role plumbing only ──────────────────────────────
        // Lenient parse: an unrecognised stored value downgrades to PATIENT
        // rather than throwing, so a bad row costs a surface, not the session.
        role = UserRole.fromStorage(role),
    )

    private fun mapAuthError(throwable: Throwable, fallback: String): AppResult.Error {
        val message = throwable.message?.takeIf { it.isNotBlank() } ?: fallback
        return AppResult.Error(throwable, message)
    }

    private fun isValidEmail(email: String): Boolean =
        email.contains("@") && email.substringAfter("@").contains(".")

    private fun localUserId(email: String): String =
        UUID.nameUUIDFromBytes(email.lowercase().toByteArray()).toString()

    private fun resolveFirebaseAuth(): FirebaseAuth? = try {
        FirebaseApp.getInstance()
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }

    private fun isPlaceholderProject(): Boolean = try {
        FirebaseApp.getInstance().options.projectId == PLACEHOLDER_PROJECT_ID
    } catch (_: Exception) {
        true
    }

    companion object {
        private const val PLACEHOLDER_PROJECT_ID = "dermoai-placeholder"
    }
}
