package com.dermoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.theme.DermoAITheme
import com.dermoai.navigation.DermoAppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: com.dermoai.core.data.preferences.UserPreferencesDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Observe language changes and recreate instantly
        scope.launch {
            prefs.languageCode.collect { lang ->
                val current = getLanguageSync(baseContext)
                if (lang != current) {
                    getSharedPreferences("dermoai_prefs_sync", MODE_PRIVATE)
                        .edit().putString("language", lang).apply()
                    recreate()
                }
            }
        }

        setContent {
            val isDark by prefs.darkModeEnabled.collectAsState(initial = null)
            val useDynamic by prefs.dynamicColorEnabled.collectAsState(initial = true)
            DermoAITheme(
                darkTheme = isDark ?: isSystemInDarkTheme(),
                dynamicColor = useDynamic,
            ) {
                NeuSurface(modifier = Modifier.fillMaxSize()) {
                    DermoAppRoot()
                }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = getLanguageSync(newBase)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun getLanguageSync(ctx: android.content.Context): String {
        return ctx.getSharedPreferences("dermoai_prefs_sync", android.content.Context.MODE_PRIVATE)
            .getString("language", "en") ?: "en"
    }
}
