package com.dermoai.core.data.preferences

import com.dermoai.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [UserPreferencesRepository].
 */
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
) : UserPreferencesRepository {
    override val isOnboarded: Flow<Boolean> = dataStore.isOnboarded
    override val activeUserId: Flow<String?> = dataStore.activeUserId

    override suspend fun setOnboarded(value: Boolean) = dataStore.setOnboarded(value)

    override suspend fun setActiveUserId(userId: String?) = dataStore.setActiveUserId(userId)
}
