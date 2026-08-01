package com.dermoai.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Refined palette — "Pine & Cream" neumorphism: warm sand base, warm cream
 * raised surfaces, warm taupe shadows, pine green as the single strong accent.
 * Coral/sage/amber stay as the semantic alert family.
 */
object DermoColors {
    // Accent — pine green (was teal)
    val Teal       = Color(0xFF1E6E5C)   // pine — buttons, links, active states (6.1:1 on white)
    val TealLight  = Color(0xFFD9EDE4)   // pale pine — chips, icon wells, avatar
    val TealText   = Color(0xFF123F33)   // deep pine — small text on light fills (9.6:1 on pale pine)

    // Darker text variants — small labels must clear AA (≥4.5:1) on light fills.
    val CoralText  = Color(0xFFB33A24)   // ~5.2:1 on raised
    val AmberText  = Color(0xFF8A5A10)   // ~5.2:1 on raised
    val SageText   = Color(0xFF2F6B57)   // ~5.5:1 on raised

    val Ink        = Color(0xFF202B26)   // warm ink — primary text (~11:1 on base)
    val Slate      = Color(0xFF55645C)   // warm gray-green — secondary text (≥4.9:1)
    val Muted      = Color(0xFF8F968D)   // captions, placeholders — never on the base, bump to Slate
    val Line       = Color(0xFFC9C1B4)   // borders, dividers (matches ShadowLo)

    // Neumorphic warm ramp: depth comes from the dual shadows, not brightness.
    val Canvas     = Color(0xFFEAE4DA)   // warm sand — mid-tone page background (base)
    val CardWhite  = Color(0xFFF4EFE7)   // warm cream — raised card fill (lighter than base)
    val TintSweep  = Color(0xFFE2DCD1)   // warm sand — inset well / pressed fill (darker than base)

    val ShadowHi   = Color(0xFFFFFFFF)   // top-left light source (warm white)
    val ShadowLo   = Color(0xFFC9C1B4)   // bottom-right warm taupe shadow

    val Sky        = Color(0xFFE0F2FE)   // info banners
    val Moss       = Color(0xFFDCFCE7)   // success banners
    val Bloom      = Color(0xFFFFEDD5)   // warning banners
    val Rose       = Color(0xFFFFE4E6)   // error banners

    val Coral      = Color(0xFFE8634A)   // alerts, concern
    val Sage       = Color(0xFF65A58D)   // positive, healthy
    val Amber      = Color(0xFFD4953A)   // watch, caution

    val GlassLight = Color(0x66FFFFFF)
    val GlassDark  = Color(0x66000000)

    // Dark mode equivalents
    val DarkCanvas = Color(0xFF0F172A)
    val DarkCard   = Color(0xFF232F45)
    val DarkInk    = Color(0xFFE2E8F0)
    val DarkSlate  = Color(0xFF94A3B8)
    val DarkLine   = Color(0xFF334155)

    // Dark soft depth — subtle dual shadows on slate surfaces
    val DarkShadowHi = Color(0xFF26324B)
    val DarkShadowLo = Color(0xFF0A101F)

    // Legacy aliases — keep so feature code compiles without changes
    val TealAccent   get() = Teal
    val VioletAccent = Color(0xFF0D9488) // was purple, now teal
    val HealthGreen  get() = Sage
    val WarmAmber    get() = Amber
    val SoftCoral    get() = Coral
    val SlateNeutral get() = Slate
    val SurfaceLight get() = Canvas
    val SurfaceDark  get() = DarkCanvas
    val cardLight    get() = CardWhite
    val cardDark     get() = DarkCard
    val GlassOverlayLight get() = GlassLight
    val GlassOverlayDark  get() = GlassDark
    val headingGradientStart get() = Teal
    val headingGradientEnd   get() = VioletAccent

    /**
     * Map an accent to its darker text variant for small labels on light
     * fills. Accents like [Teal]/[Coral] fail AA at labelSmall/labelMedium
     * sizes; the *Text variants clear 4.5:1 on both Canvas and CardWhite.
     */
    fun textOnLight(accent: Color): Color = when (accent) {
        Teal, TealAccent, VioletAccent -> TealText
        Coral, SoftCoral -> CoralText
        Sage, HealthGreen -> SageText
        Amber, WarmAmber -> AmberText
        else -> accent
    }
}
