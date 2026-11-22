package com.moddingforge.ezplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Rounded card with an optional title row. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val shape = RoundedCornerShape(20.dp)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.padding(top = 8.dp))
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = inner)
    }
}

/** Label left, amount right. */
@Composable
fun AmountRow(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emphasized: Boolean = false,
    indent: Boolean = false,
    signed: Boolean = false,
) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .padding(start = if (indent) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = style,
            color = if (indent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (signed) Money.formatSigned(cents) else Money.format(cents),
            style = style,
            color = color,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
        )
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, CircleShape),
    )
}

@Composable
fun BudgetBar(ratio: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { ratio.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

/** Amount input with decimal keyboard. Shows an error once something unparsable was typed. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    showError: Boolean = false,
    supportingText: String? = null,
) {
    val parsed = Money.parse(value)
    val invalid = value.isNotBlank() && (parsed == null || (!allowNegative && parsed < 0))
    val isError = invalid || (showError && (parsed == null || parsed == 0L))
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it in ",.-" }) },
        label = { Text(label) },
        suffix = { Text("€") },
        singleLine = true,
        isError = isError,
        supportingText = when {
            invalid -> ({ Text("Ungültiger Betrag") })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only field that opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    // A read-only text field does not report taps reliably, so a transparent layer on top opens the picker.
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date.formatDate(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Datum wählen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clickable { open = true },
        )
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Single choice as a row of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    title: String? = null,
    leading: (@Composable (T) -> Unit)? = null,
) {
    Column(modifier) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    leadingIcon = leading?.let { { it(option) } },
                )
            }
        }
    }
}

/** << < Month Year > >> like the old app. */
@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(month.minusYears(1)) }) {
            Icon(Icons.Default.KeyboardDoubleArrowLeft, "Ein Jahr zurück")
        }
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.Default.ChevronLeft, "Ein Monat zurück")
        }
        Text(
            month.label(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable { onChange(YearMonth.now()) },
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.Default.ChevronRight, "Ein Monat vor")
        }
        IconButton(onClick = { onChange(month.plusYears(1)) }) {
            Icon(Icons.Default.KeyboardDoubleArrowRight, "Ein Jahr vor")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
        },
        actions = { Row { actions() } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))
