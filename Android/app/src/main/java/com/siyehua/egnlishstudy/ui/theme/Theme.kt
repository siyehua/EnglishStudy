package com.siyehua.egnlishstudy.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = StudyGreen,
    onPrimary = Color.White,
    primaryContainer = StudyGreenDark,
    onPrimaryContainer = StudyMint,
    secondary = StudyBlue,
    onSecondary = Color.White,
    secondaryContainer = StudyBlueSoft,
    tertiary = StudyYellow,
    onTertiary = StudyInk,
    background = StudyDarkBackground,
    onBackground = Color(0xFFEAF2EA),
    surface = StudyDarkSurface,
    onSurface = Color(0xFFEAF2EA),
    surfaceVariant = Color(0xFF223027),
    onSurfaceVariant = Color(0xFFB8C6BA),
    outline = StudyDarkLine,
    error = StudyCoral
)

private val LightColorScheme = lightColorScheme(
    primary = StudyGreen,
    onPrimary = Color.White,
    primaryContainer = StudyMint,
    onPrimaryContainer = StudyGreenDark,
    secondary = StudyBlue,
    onSecondary = Color.White,
    secondaryContainer = StudyBlueSoft,
    onSecondaryContainer = StudyBlue,
    tertiary = StudyYellow,
    onTertiary = StudyInk,
    tertiaryContainer = StudyYellowSoft,
    onTertiaryContainer = StudyInk,
    background = StudyBackground,
    onBackground = StudyInk,
    surface = StudySurface,
    onSurface = StudyInk,
    surfaceVariant = Color(0xFFEAF0E5),
    onSurfaceVariant = StudyMuted,
    outline = StudyLine,
    error = Color(0xFFB3261E)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun EgnlishStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
