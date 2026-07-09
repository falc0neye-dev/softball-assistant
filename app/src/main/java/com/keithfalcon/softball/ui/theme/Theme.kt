package com.keithfalcon.softball.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = FieldGreen,
    onPrimary = ChalkCard,
    primaryContainer = GreenTint,
    onPrimaryContainer = FieldGreen,
    secondary = InfieldClay,
    onSecondary = ChalkCard,
    secondaryContainer = ClayTint,
    onSecondaryContainer = InfieldClay,
    tertiary = Amber,
    onTertiary = ChalkCard,
    tertiaryContainer = AmberTint,
    onTertiaryContainer = AmberDeep,
    error = OutRed,
    onError = ChalkCard,
    errorContainer = RedTint,
    onErrorContainer = OutRed,
    background = Chalk,
    onBackground = Ink,
    surface = ChalkCard,
    onSurface = Ink,
    surfaceVariant = ChalkDim,
    onSurfaceVariant = TextMuted,
    outline = ChalkBorder,
    outlineVariant = ChalkBorder,
)

private val DarkColors = darkColorScheme(
    primary = FieldGreenBright,
    onPrimary = NightBg,
    primaryContainer = GreenDark,
    onPrimaryContainer = GreenOnDark,
    secondary = InfieldClay,
    onSecondary = NightText,
    secondaryContainer = NightCard,
    onSecondaryContainer = ClayLight,
    tertiary = Amber,
    onTertiary = NightBg,
    tertiaryContainer = NightCard,
    onTertiaryContainer = Amber,
    error = OutRedLight,
    onError = NightBg,
    errorContainer = NightCard,
    onErrorContainer = OutRedLight,
    background = NightBg,
    onBackground = NightText,
    surface = NightCard,
    onSurface = NightText,
    surfaceVariant = NightBorder,
    onSurfaceVariant = NightMuted,
    outline = NightBorder,
    outlineVariant = NightBorder,
)

@Composable
fun SoftballTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SoftballTypography,
        content = content,
    )
}
