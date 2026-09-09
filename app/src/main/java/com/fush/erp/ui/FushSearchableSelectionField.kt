package com.fush.erp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Central searchable selector for dynamic/master-data records.
 *
 * The typed text is search input only; a record is committed exclusively through [onSelected].
 * Editing after a committed selection MUST invalidate the backing record through [onCleared].
 * This callback is mandatory so visible search text can never diverge from a stale selected object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FushSearchableSelectionField(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    searchTerms: (T) -> List<String> = { listOf(optionText(it)) },
    placeholder: String = "اكتب للبحث...",
    supportingText: ((T) -> String?)? = null,
    onCleared: () -> Unit,
    allowClear: Boolean = false,
    onClearSelected: (() -> Unit)? = null,
    maxResults: Int = 30,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(selectedText) }

    LaunchedEffect(selectedText) {
        query = selectedText
    }

    val normalized = query.trim().lowercase(Locale.ROOT)
    val selectedNormalized = selectedText.trim().lowercase(Locale.ROOT)
    val effectiveSearch = if (selectedText.isNotBlank() && normalized == selectedNormalized) "" else normalized
    val filtered = remember(options, effectiveSearch, maxResults) {
        val base = if (effectiveSearch.isBlank()) options else options.filter { option ->
            searchTerms(option).any { term -> term.lowercase(Locale.ROOT).contains(effectiveSearch) }
        }
        base.take(maxResults.coerceAtLeast(1))
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand -> expanded = shouldExpand },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                if (selectedText.isNotBlank() && value != selectedText) onCleared()
                query = value
                expanded = true
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            if (allowClear && onClearSelected != null) {
                DropdownMenuItem(
                    text = { Text("بدون / مسح الاختيار") },
                    onClick = {
                        onClearSelected()
                        query = ""
                        expanded = false
                    },
                )
                HorizontalDivider()
            }
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("لا توجد نتائج مطابقة") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(optionText(option))
                                supportingText?.invoke(option)?.takeIf { it.isNotBlank() }?.let { secondary ->
                                    Text(
                                        secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelected(option)
                            query = optionText(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
