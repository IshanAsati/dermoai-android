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
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _envAlertsEnabled = MutableStateFlow(true)
    val envAlertsEnabled: StateFlow<Boolean> = _envAlertsEnabled.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _darkMode = MutableStateFlow<Boolean?>(null)
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

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
    }

    fun toggleEnvAlerts() { viewModelScope.launch { val n = !_envAlertsEnabled.value; _envAlertsEnabled.value = n; prefs.setEnvAlertsEnabled(n) } }
    val languages = listOf("en" to "English", "hi" to "हिंदी", "bn" to "বাংলা", "mr" to "मराठी", "ta" to "தமிழ்", "te" to "తెలుగు")
    fun setLanguage(code: String) { viewModelScope.launch {
        _language.value = code
        prefs.setLanguage(code)
    } }
    fun toggleDarkMode() { viewModelScope.launch { val n = _darkMode.value != true; _darkMode.value = n; prefs.setDarkMode(n) } }
    fun toggleDynamicColor() { viewModelScope.launch { val n = !_dynamicColor.value; _dynamicColor.value = n; prefs.setDynamicColor(n) } }
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
    var signOutDialog by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("Your local data will remain on this device.") },
            confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader("Settings")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsRow(Icons.Outlined.Brightness6, "Dark theme") { Switch(checked = darkMode == true, onCheckedChange = { viewModel.toggleDarkMode() }, modifier = Modifier.semantics { contentDescription = "Dark theme" }) }
            SettingsRow(Icons.Outlined.Notifications, "Environmental alerts") { Switch(checked = envAlerts, onCheckedChange = { viewModel.toggleEnvAlerts() }, modifier = Modifier.semantics { contentDescription = "Environmental alerts" }) }
            SettingsRow(Icons.Outlined.Palette, "Dynamic color (Android 12+)") { Switch(checked = dynamicColor, onCheckedChange = { viewModel.toggleDynamicColor() }, modifier = Modifier.semantics { contentDescription = "Dynamic color" }) }
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
