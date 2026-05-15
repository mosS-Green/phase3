package com.phase3.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── MD3 Light Color Scheme ──────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = SeaGreen40,
    onPrimary            = Color.White,
    primaryContainer     = SeaGreen90,
    onPrimaryContainer   = SeaGreen10,

    secondary            = Secondary40,
    onSecondary          = Color.White,
    secondaryContainer   = Secondary90,
    onSecondaryContainer = Secondary10,

    tertiary             = Tertiary40,
    onTertiary           = Color.White,
    tertiaryContainer    = Tertiary90,
    onTertiaryContainer  = Tertiary10,

    error                = Error40,
    onError              = Color.White,
    errorContainer       = Error90,
    onErrorContainer     = Error10,

    background           = Neutral99,
    onBackground         = Neutral10,
    surface              = Neutral99,
    onSurface            = Neutral10,

    surfaceVariant       = NeutralVar90,
    onSurfaceVariant     = NeutralVar30,
    outline              = NeutralVar50,
    outlineVariant       = NeutralVar80,

    inverseSurface       = Neutral20,
    inverseOnSurface     = Neutral95,
    inversePrimary       = SeaGreen80,

    surfaceTint          = SeaGreen40,
)

// ── MD3 Dark Color Scheme ───────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = SeaGreen80,
    onPrimary            = SeaGreen20,
    primaryContainer     = SeaGreen30,
    onPrimaryContainer   = SeaGreen90,

    secondary            = Secondary80,
    onSecondary          = Secondary20,
    secondaryContainer   = Secondary30,
    onSecondaryContainer = Secondary90,

    tertiary             = Tertiary80,
    onTertiary           = Tertiary20,
    tertiaryContainer    = Tertiary30,
    onTertiaryContainer  = Tertiary90,

    error                = Error80,
    onError              = Error20,
    errorContainer       = Error30,
    onErrorContainer     = Error90,

    background           = Neutral6,
    onBackground         = Neutral90,
    surface              = Neutral6,
    onSurface            = Neutral90,

    surfaceVariant       = NeutralVar30,
    onSurfaceVariant     = NeutralVar80,
    outline              = NeutralVar60,
    outlineVariant       = NeutralVar30,

    inverseSurface       = Neutral90,
    inverseOnSurface     = Neutral20,
    inversePrimary       = SeaGreen40,

    surfaceTint          = SeaGreen80,
)

@Composable
fun Phase3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOffline: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // When offline, swap the primary tones from sea-green to grey
    val colorScheme = if (isOffline) {
        if (darkTheme) {
            baseScheme.copy(
                primary = OfflineGrey80,
                onPrimary = OfflineGrey20,
                primaryContainer = OfflineGrey30,
                onPrimaryContainer = OfflineGrey90,
                surfaceTint = OfflineGrey80,
                inversePrimary = OfflineGrey40
            )
        } else {
            baseScheme.copy(
                primary = OfflineGrey40,
                onPrimary = Color.White,
                primaryContainer = OfflineGrey90,
                onPrimaryContainer = OfflineGrey10,
                surfaceTint = OfflineGrey40,
                inversePrimary = OfflineGrey80
            )
        }
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Phase3Typography,
        content = content
    )
}

