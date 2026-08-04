package com.dermoai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.data.preferences.UserPreferencesDataStore
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.UserProfileDetailsEntity
import com.dermoai.core.domain.usecase.auth.ObserveAuthStateUseCase
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private data class SettingsPrefs(
    val envAlerts: Boolean,
    val language: String,
    val darkMode: Boolean?,
    val dynamicColor: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore,
    private val userProfileDetailsDao: UserProfileDetailsDao,
    private val observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {

    private val _envAlertsEnabled = MutableStateFlow(true)
    val envAlertsEnabled: StateFlow<Boolean> = _envAlertsEnabled.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _darkMode = MutableStateFlow<Boolean?>(null)
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileDetailsEntity?>(null)
    val profile: StateFlow<UserProfileDetailsEntity?> = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.envAlertsEnabled, prefs.languageCode, prefs.darkModeEnabled, prefs.dynamicColorEnabled) { e, l, d, dc ->
                SettingsPrefs(e, l, d, dc)
            }.collect { (e, l, d, dc) ->
                _envAlertsEnabled.value = e
                _language.value = l
                _darkMode.value = d
                _dynamicColor.value = dc
            }
        }
        viewModelScope.launch {
            withTimeoutOrNull(5_000) { observeAuthState().first { it != null } }?.id?.let { uid ->
                _profile.value = userProfileDetailsDao.getById(uid)
            }
        }
    }

    fun toggleEnvAlerts() { viewModelScope.launch { val n = !_envAlertsEnabled.value; _envAlertsEnabled.value = n; prefs.setEnvAlertsEnabled(n) } }
    val languages = listOf("en" to "English", "hi" to "हिंदी", "bn" to "বাংলা", "mr" to "मराठी", "ta" to "தமிழ்", "te" to "తెలుగు")
    fun setLanguage(code: String) { viewModelScope.launch {
        _language.value = code
        prefs.setLanguage(code)
    } }
    fun toggleDarkMode() { viewModelScope.launch { val n = _darkMode.value != true; _darkMode.value = n; prefs.setDarkMode(n) } }
    fun toggleDynamicColor() { viewModelScope.launch { val n = !_dynamicColor.value; _dynamicColor.value = n; prefs.setDynamicColor(n) } }

    /** Persists the editable demographics (age, gender, skin type/tone, sun exposure). */
    fun saveProfile(age: String, gender: String, skinType: String, skinTone: String, sunExposure: String) {
        viewModelScope.launch {
            val uid = withTimeoutOrNull(5_000) { observeAuthState().first { it != null } }?.id ?: return@launch
            val existing = userProfileDetailsDao.getById(uid)
            val now = System.currentTimeMillis()
            val updated = UserProfileDetailsEntity(
                userId = uid,
                age = age.toIntOrNull()?.coerceIn(1, 120) ?: 0,
                gender = gender,
                skinType = skinType,
                skinTone = skinTone,
                sunExposure = sunExposure,
                skinConcerns = existing?.skinConcerns ?: "",
                allergies = existing?.allergies ?: "",
                medications = existing?.medications ?: "",
                waterIntake = existing?.waterIntake ?: "",
                sleepHours = existing?.sleepHours ?: "",
                stressLevel = existing?.stressLevel ?: "",
                diet = existing?.diet ?: "",
                smoking = existing?.smoking ?: false,
                alcohol = existing?.alcohol ?: false,
                exercise = existing?.exercise ?: "",
                skinCareRoutine = existing?.skinCareRoutine ?: "",
                language = existing?.language ?: "en",
                createdAt = existing?.createdAt ?: now,
            )
            userProfileDetailsDao.upsert(updated)
            _profile.value = updated
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val envAlerts by viewModel.envAlertsEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val profile by viewModel.profile.collectAsState()
    var signOutDialog by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf(false) }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("Your local data will remain on this device.") },
            confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } },
        )
    }

    if (profileDialog) {
        SkinProfileDialog(
            initial = profile,
            onDismiss = { profileDialog = false },
            onSave = { age, gender, skinType, skinTone, sunExposure ->
                viewModel.saveProfile(age, gender, skinType, skinTone, sunExposure)
                profileDialog = false
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Settings")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsRow(Icons.Outlined.Brightness6, "Dark theme") { Switch(checked = darkMode == true, onCheckedChange = { viewModel.toggleDarkMode() }, modifier = Modifier.semantics { contentDescription = "Dark theme" }) }
            SettingsRow(Icons.Outlined.Notifications, "Environmental alerts") { Switch(checked = envAlerts, onCheckedChange = { viewModel.toggleEnvAlerts() }, modifier = Modifier.semantics { contentDescription = "Environmental alerts" }) }
            SettingsRow(Icons.Outlined.Palette, "Dynamic color (Android 12+)") { Switch(checked = dynamicColor, onCheckedChange = { viewModel.toggleDynamicColor() }, modifier = Modifier.semantics { contentDescription = "Dynamic color" }) }
            SettingsRow(Icons.Outlined.Person, "Skin profile") {
                Column(horizontalAlignment = Alignment.End) {
                    val summary = buildString {
                        profile?.age?.takeIf { it > 0 }?.let { append("Age $it · ") }
                        profile?.gender?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                        profile?.skinType?.takeIf { it.isNotBlank() }?.let { append(it) }
                    }.ifBlank { "Not set" }
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { profileDialog = true }) { Text("Edit") }
                }
            }
            SettingsRow(Icons.Outlined.Language, "Language") {
                Box { Text(if (language == "en") "English" else viewModel.languages.find { it.first == language }?.second ?: language, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { langExpanded = true }) { Text("Change") }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        viewModel.languages.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { viewModel.setLanguage(code); langExpanded = false })
                        }
                    }
                }
            }
            SettingsRow(Icons.Outlined.FileDownload, "Export my data") { Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingsRow(Icons.Outlined.Logout, "Sign out") {
                TextButton(onClick = { signOutDialog = true }) { Text("Sign out", color = DermoColors.SoftCoral) }
            }
            Spacer(Modifier.height(32.dp))
            Text("DermoAI v1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Text("This app is an educational and awareness tool and does not replace a dermatologist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, trailing: @Composable () -> Unit) {
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = DermoColors.TealAccent)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

private val GENDERS = listOf("Male", "Female", "Non-binary", "Prefer not to say")
private val SKIN_TYPES = listOf("Normal", "Oily", "Dry", "Combination", "Sensitive")
private val SKIN_TONES = listOf("Very Fair", "Fair", "Medium", "Olive", "Brown", "Dark Brown")
private val SUN_EXPOSURES = listOf("Indoors mostly", "15-30 min daily", "1-2 hours daily", "Outdoor worker")

/** Edits the demographics the scan rule layer uses to refine estimates. */
@Composable
private fun SkinProfileDialog(
    initial: UserProfileDetailsEntity?,
    onDismiss: () -> Unit,
    onSave: (age: String, gender: String, skinType: String, skinTone: String, sunExposure: String) -> Unit,
) {
    var age by remember(initial) { mutableStateOf(initial?.age?.takeIf { it > 0 }?.toString() ?: "") }
    var gender by remember(initial) { mutableStateOf(initial?.gender ?: "") }
    var skinType by remember(initial) { mutableStateOf(initial?.skinType ?: "") }
    var skinTone by remember(initial) { mutableStateOf(initial?.skinTone ?: "") }
    var sunExposure by remember(initial) { mutableStateOf(initial?.sunExposure ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skin profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Used to refine scan estimates — stored only on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ProfileDropdown("Gender", GENDERS, gender) { gender = it }
                ProfileDropdown("Skin type", SKIN_TYPES, skinType) { skinType = it }
                ProfileDropdown("Skin tone", SKIN_TONES, skinTone) { skinTone = it }
                ProfileDropdown("Sun exposure", SUN_EXPOSURES, sunExposure) { sunExposure = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(age, gender, skinType, skinTone, sunExposure) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileDropdown(label: String, options: List<String>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.ifBlank { "Not set" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { TextButton(onClick = { expanded = true }) { Text("Change") } },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}
