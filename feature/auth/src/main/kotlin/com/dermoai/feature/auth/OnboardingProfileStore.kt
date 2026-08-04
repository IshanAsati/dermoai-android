package com.dermoai.feature.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.onboardingStore by preferencesDataStore(name = "onboarding_prefs")

private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
private val KEY_AGE = stringPreferencesKey("age")
private val KEY_GENDER = stringPreferencesKey("gender")
private val KEY_SKIN_TYPE = stringPreferencesKey("skin_type")
private val KEY_SKIN_TONE = stringPreferencesKey("skin_tone")
private val KEY_SKIN_CONCERNS = stringPreferencesKey("skin_concerns")
private val KEY_ALLERGIES = stringPreferencesKey("allergies")
private val KEY_MEDICATIONS = stringPreferencesKey("medications")
private val KEY_SUN_EXPOSURE = stringPreferencesKey("sun_exposure")
private val KEY_WATER_INTAKE = stringPreferencesKey("water_intake")
private val KEY_SLEEP_HOURS = stringPreferencesKey("sleep_hours")
private val KEY_STRESS_LEVEL = stringPreferencesKey("stress_level")
private val KEY_DIET = stringPreferencesKey("diet")
private val KEY_SMOKING = stringPreferencesKey("smoking")
private val KEY_ALCOHOL = stringPreferencesKey("alcohol")
private val KEY_EXERCISE = stringPreferencesKey("exercise")
private val KEY_SKINCARE_ROUTINE = stringPreferencesKey("skincare_routine")
private val KEY_LANGUAGE = stringPreferencesKey("language")

data class OnboardingProfile(
    val displayName: String = "",
    val age: String = "",
    val gender: String = "",
    val skinType: String = "",
    val skinTone: String = "",
    val skinConcerns: String = "",
    val allergies: String = "",
    val medications: String = "",
    val sunExposure: String = "",
    val waterIntake: String = "",
    val sleepHours: String = "",
    val stressLevel: String = "",
    val diet: String = "",
    val smoking: Boolean = false,
    val alcohol: Boolean = false,
    val exercise: String = "",
    val skinCareRoutine: String = "",
    val language: String = "en",
)

@Singleton
class OnboardingProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val profile: Flow<OnboardingProfile> = context.onboardingStore.data.map { prefs ->
        OnboardingProfile(
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            age = prefs[KEY_AGE] ?: "",
            gender = prefs[KEY_GENDER] ?: "",
            skinType = prefs[KEY_SKIN_TYPE] ?: "",
            skinTone = prefs[KEY_SKIN_TONE] ?: "",
            skinConcerns = prefs[KEY_SKIN_CONCERNS] ?: "",
            allergies = prefs[KEY_ALLERGIES] ?: "",
            medications = prefs[KEY_MEDICATIONS] ?: "",
            sunExposure = prefs[KEY_SUN_EXPOSURE] ?: "",
            waterIntake = prefs[KEY_WATER_INTAKE] ?: "",
            sleepHours = prefs[KEY_SLEEP_HOURS] ?: "",
            stressLevel = prefs[KEY_STRESS_LEVEL] ?: "",
            diet = prefs[KEY_DIET] ?: "",
            smoking = prefs[KEY_SMOKING] == "true",
            alcohol = prefs[KEY_ALCOHOL] == "true",
            exercise = prefs[KEY_EXERCISE] ?: "",
            skinCareRoutine = prefs[KEY_SKINCARE_ROUTINE] ?: "",
            language = prefs[KEY_LANGUAGE] ?: "en",
        )
    }

    suspend fun saveField(key: String, value: String) {
        context.onboardingStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }

    /** Persists the whole profile at once (used to stage it until auth succeeds). */
    suspend fun save(profile: OnboardingProfile) {
        context.onboardingStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = profile.displayName
            prefs[KEY_AGE] = profile.age
            prefs[KEY_GENDER] = profile.gender
            prefs[KEY_SKIN_TYPE] = profile.skinType
            prefs[KEY_SKIN_TONE] = profile.skinTone
            prefs[KEY_SKIN_CONCERNS] = profile.skinConcerns
            prefs[KEY_ALLERGIES] = profile.allergies
            prefs[KEY_MEDICATIONS] = profile.medications
            prefs[KEY_SUN_EXPOSURE] = profile.sunExposure
            prefs[KEY_WATER_INTAKE] = profile.waterIntake
            prefs[KEY_SLEEP_HOURS] = profile.sleepHours
            prefs[KEY_STRESS_LEVEL] = profile.stressLevel
            prefs[KEY_DIET] = profile.diet
            prefs[KEY_SMOKING] = profile.smoking.toString()
            prefs[KEY_ALCOHOL] = profile.alcohol.toString()
            prefs[KEY_EXERCISE] = profile.exercise
            prefs[KEY_SKINCARE_ROUTINE] = profile.skinCareRoutine
            prefs[KEY_LANGUAGE] = profile.language
        }
    }

    suspend fun clear() {
        context.onboardingStore.edit { it.clear() }
    }
}

/** Maps the staged profile into the Room entity for a specific user id. */
fun OnboardingProfile.toEntity(userId: String): com.dermoai.core.database.entity.UserProfileDetailsEntity =
    com.dermoai.core.database.entity.UserProfileDetailsEntity(
        userId = userId,
        age = age.toIntOrNull()?.coerceIn(1, 120) ?: 0,
        gender = gender,
        skinType = skinType,
        skinTone = skinTone,
        skinConcerns = skinConcerns,
        allergies = allergies,
        medications = medications,
        sunExposure = sunExposure,
        waterIntake = waterIntake,
        sleepHours = sleepHours,
        stressLevel = stressLevel,
        diet = diet,
        smoking = smoking,
        alcohol = alcohol,
        exercise = exercise,
        skinCareRoutine = skinCareRoutine,
        language = language,
        createdAt = System.currentTimeMillis(),
    )
