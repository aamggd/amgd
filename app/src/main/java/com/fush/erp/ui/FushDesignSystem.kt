package com.fush.erp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stable UI tokens for FUSH ERP Mobile.
 *
 * Keep presentation constants here so screens and shared components do not invent their own
 * spacing, touch-target, field, dialog, radius or elevation values. These tokens are UI-only and
 * must not be used to encode business rules or data constraints.
 */
object FushSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

object FushRadius {
    val extraSmall: Dp = 6.dp
    val small: Dp = 10.dp
    val medium: Dp = 16.dp
    val large: Dp = 22.dp
    val extraLarge: Dp = 30.dp
}

object FushDimensions {
    /** Minimum interactive target used by shared controls. */
    val minTouchTarget: Dp = 48.dp

    /** Visual minimum for form fields; touch target remains at least [minTouchTarget]. */
    val fieldMinHeight: Dp = 56.dp

    val avatarSize: Dp = 40.dp
    val brandCompactSize: Dp = 40.dp
    val brandSize: Dp = 52.dp

    /** Long form dialogs scroll inside this height rather than overflowing compact screens. */
    val dialogFormMaxHeight: Dp = 560.dp
}

object FushElevation {
    val subtle: Dp = 1.dp
}

enum class FushStatusTone { Neutral, Info, Success, Warning, Danger }

data class FushToneColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

/** One status-color mapping shared by pills, notices, states and metrics. */
@Composable
fun fushToneColors(tone: FushStatusTone): FushToneColors = when (tone) {
    FushStatusTone.Neutral -> FushToneColors(
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.outline,
    )
    FushStatusTone.Info -> FushToneColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        accent = MaterialTheme.colorScheme.primary,
    )
    FushStatusTone.Success -> FushToneColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        accent = MaterialTheme.colorScheme.secondary,
    )
    FushStatusTone.Warning -> FushToneColors(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        accent = MaterialTheme.colorScheme.tertiary,
    )
    FushStatusTone.Danger -> FushToneColors(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        accent = MaterialTheme.colorScheme.error,
    )
}
