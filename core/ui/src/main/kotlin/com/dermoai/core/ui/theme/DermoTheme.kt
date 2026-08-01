package com.dermoai.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Neumorphic radius set — 14dp medium matches the style's 12–16dp embossed band. */
private val NeuShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * "Pine & Cream" neumorphism (light). Warm sand base `Canvas #EAE4DA`, warm
 * cream raised fill `CardWhite #F4EFE7` (lighter than base), warm sand inset
 * wells `TintSweep #E2DCD1`, warm taupe dual shadows, pine accent.
 * Text tokens clear WCAG AA on base and raised fills (ink ~11:1, slate ≥4.9:1,
 * pine 4.8–5.3:1).
 */
private val LightColorScheme = lightColorScheme(
    primary = DermoColors.Teal,
    onPrimary = Color.White,
    primaryContainer = DermoColors.TealLight,
    onPrimaryContainer = DermoColors.TealText,
    secondary = DermoColors.Slate,
    onSecondary = Color.White,
    secondaryContainer = DermoColors.TintSweep,
    onSecondaryContainer = DermoColors.Ink,
    tertiary = DermoColors.Sage,
    onTertiary = Color.White,
    background = DermoColors.Canvas,
    onBackground = DermoColors.Ink,
    surface = DermoColors.Canvas,
    onSurface = DermoColors.Ink,
    surfaceVariant = DermoColors.TintSweep,
    onSurfaceVariant = DermoColors.Slate,
    surfaceContainerHigh = DermoColors.CardWhite,
    surfaceContainerLow = DermoColors.TintSweep,
    error = DermoColors.Coral,
    onError = Color.White,
    outline = DermoColors.Line,
    outlineVariant = DermoColors.Line.copy(alpha = 0.5f),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = DermoColors.TealText,
    primaryContainer = DermoColors.Teal,
    onPrimaryContainer = DermoColors.TealLight,
    secondary = Color(0xFF94A3B8),
    onSecondary = DermoColors.Ink,
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF064E3B),
    background = DermoColors.DarkCanvas,
    onBackground = DermoColors.DarkInk,
    surface = DermoColors.DarkCanvas,
    onSurface = DermoColors.DarkInk,
    surfaceVariant = DermoColors.DarkLine,
    onSurfaceVariant = DermoColors.DarkSlate,
    surfaceContainerHigh = DermoColors.DarkCard,
    surfaceContainerLow = Color(0xFF1A2438),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    outline = DermoColors.DarkLine,
    outlineVariant = DermoColors.DarkLine.copy(alpha = 0.5f),
)

@Composable
fun DermoAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DermoTypography,
        shapes = NeuShapes,
        content = {
            val neuShadows = if (darkTheme) {
                NeuShadowColors(
                    shadowHi = DermoColors.DarkShadowHi,
                    shadowLo = DermoColors.DarkShadowLo,
                )
            } else {
                NeuShadowColors(
                    shadowHi = DermoColors.ShadowHi,
                    shadowLo = DermoColors.ShadowLo,
                )
            }
            CompositionLocalProvider(LocalNeuShadowColors provides neuShadows) {
                content()
            }
        },
    )
}
