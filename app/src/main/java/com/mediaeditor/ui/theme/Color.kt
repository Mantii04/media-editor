package com.mediaeditor.ui.theme

import androidx.compose.ui.graphics.Color

// Primary palette — deep indigo-based
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Professional editor palette
val EditorPrimary = Color(0xFF7C4DFF)
val EditorPrimaryDark = Color(0xFF651FFF)
val EditorPrimaryLight = Color(0xFFB388FF)
val EditorSecondary = Color(0xFF00E5FF)
val EditorSecondaryDark = Color(0xFF00B8D4)
val EditorAccent = Color(0xFFFF4081)

// Surfaces
val SurfaceDark = Color(0xFF0D0D0D)
val SurfaceDarkAlt = Color(0xFF1A1A2E)
val SurfaceCard = Color(0xFF1E1E2E)
val SurfaceCardHover = Color(0xFF2A2A3E)
val SurfaceOverlay = Color(0xFF252538)

// Text
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFB0B0C0)
val TextTertiary = Color(0xFF707080)

// Functional
val SuccessGreen = Color(0xFF00E676)
val WarningOrange = Color(0xFFFFAB40)
val ErrorRed = Color(0xFFFF5252)
val InfoBlue = Color(0xFF448AFF)

// Timeline colors
val TimelineBg = Color(0xFF1A1A2E)
val TimelineClip = Color(0xFF7C4DFF)
val TimelineClipAlt = Color(0xFF00E5FF)
val TimelineCursor = Color(0xFFFF4081)
val TimelineSelection = Color(0x407C4DFF)

// Effect colors
val EffectGlow = Color(0xFF7C4DFF)
val EffectGlowSecondary = Color(0xFF00E5FF)
val GradientStart = Color(0xFF7C4DFF)
val GradientEnd = Color(0xFFFF4081)
val GradientSecondary = Color(0xFF00E5FF)

// Glassmorphism (for blur backgrounds)
object GlassColors {
    val background = Color(0x0DFFFFFF)
    val stroke = Color(0x1AFFFFFF)
    val shadow = Color(0x33000000)
}

// Dynamic theme support — fallback dark colors
object DarkColors {
    val primary = EditorPrimary
    val onPrimary = Color.White
    val primaryContainer = Color(0xFF4A148C)
    val secondary = EditorSecondary
    val onSecondary = Color.Black
    val background = SurfaceDark
    val onBackground = TextPrimary
    val surface = SurfaceCard
    val onSurface = TextPrimary
    val surfaceVariant = SurfaceOverlay
    val error = ErrorRed
    val onError = Color.White
    val outline = Color(0xFF3E3E50)
}
