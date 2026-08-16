package com.fush.erp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fush.erp.R

enum class FushStatusTone { Neutral, Info, Success, Warning, Danger }

val LocalFushSnackbar = staticCompositionLocalOf<((String) -> Unit)?> { null }

private fun fushMessageIsSuccess(message: String): Boolean {
    val normalized = message.trim()
    return normalized.startsWith("تم ") ||
        normalized.startsWith("تمت ") ||
        normalized.startsWith("بدأ") ||
        normalized.startsWith("سيتم ") ||
        normalized.startsWith("Saved", ignoreCase = true) ||
        normalized.startsWith("Created", ignoreCase = true) ||
        normalized.startsWith("Updated", ignoreCase = true) ||
        normalized.startsWith("Posted", ignoreCase = true) ||
        normalized.startsWith("Completed", ignoreCase = true)
}

@Composable
fun FushBrand(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(if (compact) 40.dp else 52.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_fush_logo),
                contentDescription = null,
                modifier = Modifier.padding(if (compact) 7.dp else 9.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.brand_name),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!compact) {
                Text(
                    stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FushUserAvatar(name: String, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: stringResource(R.string.user_fallback_initial)
    val userDescription = stringResource(R.string.user_content_description, name)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { contentDescription = userDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun FushSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FushStatusPill(
    text: String,
    tone: FushStatusTone = FushStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val colors = when (tone) {
        FushStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        FushStatusTone.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        FushStatusTone.Success -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        FushStatusTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        FushStatusTone.Danger -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        color = colors.first,
        contentColor = colors.second,
        shape = CircleShape,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
fun FushMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    tone: FushStatusTone = FushStatusTone.Neutral,
    onClick: (() -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.open_item_content_description, label)
    val interactive = if (onClick != null) {
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    } else {
        modifier.semantics(mergeDescendants = true) {}
    }
    ElevatedCard(
        modifier = interactive,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tone != FushStatusTone.Neutral) {
                    val dot = when (tone) {
                        FushStatusTone.Info -> MaterialTheme.colorScheme.primary
                        FushStatusTone.Success -> MaterialTheme.colorScheme.secondary
                        FushStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
                        FushStatusTone.Danger -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dot)
                            .clearAndSetSemantics {},
                    )
                }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            helper?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun FushModuleCard(
    title: String,
    subtitle: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.open_item_content_description, title)
    val interactive = if (onClick != null) {
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
    } else {
        modifier.semantics(mergeDescendants = true) {}
    }
    ElevatedCard(
        modifier = interactive,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.size(10.dp))
                    FushStatusPill(badge, FushStatusTone.Info)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FushSystemState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FushBrand(compact = true)
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FushContentStateCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    tone: FushStatusTone = FushStatusTone.Neutral,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val containerColor = when (tone) {
        FushStatusTone.Danger -> MaterialTheme.colorScheme.errorContainer
        FushStatusTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        FushStatusTone.Info -> MaterialTheme.colorScheme.primaryContainer
        FushStatusTone.Success -> MaterialTheme.colorScheme.secondaryContainer
        FushStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (tone) {
        FushStatusTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
        FushStatusTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        FushStatusTone.Info -> MaterialTheme.colorScheme.onPrimaryContainer
        FushStatusTone.Success -> MaterialTheme.colorScheme.onSecondaryContainer
        FushStatusTone.Neutral -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (tone == FushStatusTone.Danger) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun FushEmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = FushContentStateCard(title, detail, modifier, FushStatusTone.Neutral, actionLabel = actionLabel, onAction = onAction)

@Composable
fun FushLoadingState(
    title: String? = null,
    detail: String? = null,
    modifier: Modifier = Modifier,
) = FushContentStateCard(
    title = title ?: stringResource(R.string.common_loading),
    detail = detail ?: stringResource(R.string.common_loading_detail),
    modifier = modifier,
    tone = FushStatusTone.Info,
    loading = true,
)

@Composable
fun FushErrorState(
    title: String? = null,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = FushContentStateCard(
    title = title ?: stringResource(R.string.common_error_title),
    detail = detail,
    modifier = modifier,
    tone = FushStatusTone.Danger,
    actionLabel = actionLabel,
    onAction = onAction,
)

@Composable
fun FushInlineState(
    text: String,
    modifier: Modifier = Modifier,
    tone: FushStatusTone = FushStatusTone.Neutral,
) {
    val containerColor = when (tone) {
        FushStatusTone.Danger -> MaterialTheme.colorScheme.errorContainer
        FushStatusTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        FushStatusTone.Info -> MaterialTheme.colorScheme.primaryContainer
        FushStatusTone.Success -> MaterialTheme.colorScheme.secondaryContainer
        FushStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (tone) {
        FushStatusTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
        FushStatusTone.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        FushStatusTone.Info -> MaterialTheme.colorScheme.onPrimaryContainer
        FushStatusTone.Success -> MaterialTheme.colorScheme.onSecondaryContainer
        FushStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = if (tone == FushStatusTone.Danger) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
fun FushNotice(
    message: String,
    modifier: Modifier = Modifier,
    tone: FushStatusTone = FushStatusTone.Info,
) = FushInlineState(message, modifier, tone)

@Composable
fun FushDialogForm(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun FushOperationMessage(
    message: String?,
    modifier: Modifier = Modifier,
    onConsumed: () -> Unit = {},
) {
    if (message.isNullOrBlank()) return
    if (!fushMessageIsSuccess(message)) {
        FushNotice(message, modifier = modifier, tone = FushStatusTone.Danger)
        return
    }

    val snackbar = LocalFushSnackbar.current
    if (snackbar == null) {
        FushNotice(message, modifier = modifier, tone = FushStatusTone.Success)
    } else {
        LaunchedEffect(message) {
            snackbar(message)
            onConsumed()
        }
    }
}

@Composable
fun FushConfirmDialog(
    title: String,
    detail: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = { Text(detail) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
