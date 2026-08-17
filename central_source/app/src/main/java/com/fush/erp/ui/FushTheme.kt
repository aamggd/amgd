package com.fush.erp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Central visual language for FUSH ERP Mobile.
 *
 * Keep product screens on MaterialTheme tokens instead of hard-coded colors so future
 * UI branches can be merged safely without changing business logic.
 */
private val FushLightColors = lightColorScheme(
    primary = Color(0xFF0E4A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EEFA),
    onPrimaryContainer = Color(0xFF052F43),
    secondary = Color(0xFF0E766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFEA),
    onSecondaryContainer = Color(0xFF073D39),
    tertiary = Color(0xFF8B5E14),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3AE),
    onTertiaryContainer = Color(0xFF4B3108),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF17202A),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFE7EDF2),
    onSurfaceVariant = Color(0xFF46535D),
    outline = Color(0xFF71808B),
    outlineVariant = Color(0xFFC5CED5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F8),
    surfaceContainer = Color(0xFFEBF0F4),
    surfaceContainerHigh = Color(0xFFE5EBEF),
    surfaceContainerHighest = Color(0xFFDFE6EB),
)

private val FushDarkColors = darkColorScheme(
    primary = Color(0xFF9DD5EE),
    onPrimary = Color(0xFF00354B),
    primaryContainer = Color(0xFF0B4B66),
    onPrimaryContainer = Color(0xFFD0EEFB),
    secondary = Color(0xFF7FD8CF),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFF9AF5EA),
    tertiary = Color(0xFFF2C276),
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF604200),
    onTertiaryContainer = Color(0xFFFFDEA5),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E4E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E4E8),
    surfaceVariant = Color(0xFF3F484E),
    onSurfaceVariant = Color(0xFFBFC8CE),
    outline = Color(0xFF899299),
    outlineVariant = Color(0xFF3F484E),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539),
)

private val FushTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val FushShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(FushRadius.extraSmall),
    small = androidx.compose.foundation.shape.RoundedCornerShape(FushRadius.small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(FushRadius.medium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(FushRadius.large),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(FushRadius.extraLarge),
)

@Composable
fun FushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FushDarkColors else FushLightColors,
        typography = FushTypography,
        shapes = FushShapes,
        content = content,
    )
}
