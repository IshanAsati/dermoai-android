package com.dermoai.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Dual-shadow pair that drives all neumorphic depth.
 * Provided by [DermoAITheme]: light mode uses the white/bone pair,
 * dark mode uses the darker slate pair for soft depth on OLED.
 */
data class NeuShadowColors(
    val shadowHi: Color,
    val shadowLo: Color,
)

val LocalNeuShadowColors = staticCompositionLocalOf {
    NeuShadowColors(shadowHi = DermoColors.ShadowHi, shadowLo = DermoColors.ShadowLo)
}
