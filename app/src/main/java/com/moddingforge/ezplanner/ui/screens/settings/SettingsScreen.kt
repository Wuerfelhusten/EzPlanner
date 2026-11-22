package com.moddingforge.ezplanner.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.layout.Spacer
import com.moddingforge.ezplanner.BuildConfig
import com.moddingforge.ezplanner.ui.LocalUpdater
import com.moddingforge.ezplanner.ui.components.ChangelogDialog
import com.moddingforge.ezplanner.ui.components.updateStatusText
import com.moddingforge.ezplanner.update.Changelog
import com.moddingforge.ezplanner.update.UpdateState
import com.moddingforge.ezplanner.data.Snapshot
import com.moddingforge.ezplanner.data.backup.BackupManager
import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.components.BackTopBar
import com.moddingforge.ezplanner.ui.components.ColorDot
import com.moddingforge.ezplanner.ui.components.ConfirmDialog
import com.moddingforge.ezplanner.ui.components.HSpace
import com.moddingforge.ezplanner.ui.components.MoneyField
import com.moddingforge.ezplanner.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val Palette = listOf(
    0xFF7FA7FF, 0xFF4CD08A, 0xFFFFB454, 0xFFB18CFF, 0xFF4DD0E1, 0xFFFF7A9A,
    0xFFA5D66F, 0xFFFFD54F, 0xFFFF8A65, 0xFF90A4AE, 0xFFF06292, 0xFF81C784,
)

@Composable
fun SettingsScreen(nav: NavController) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by repo.settings.collectAsStateWithLifecycle(null)
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())

    var budgetInput by remember { mutableStateOf<String?>(null) }
    var warn by remember { mutableFloatStateOf(80f) }
    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        if (budgetInput == null) budgetInput = Money.toInput(s.monthlyBudgetCents)
        warn = s.warnPercent.toFloat()
    }

    var editType by remember { mutableStateOf<ExpenseTypeEntity?>(null) }
    var deleteType by remember { mutableStateOf<ExpenseTypeEntity?>(null) }
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var pendingImport by remember { mutableStateOf<Snapshot?>(null) }

    fun message(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = BackupManager.toJson(repo.snapshot())
                writeText(context, uri, text)
            }.onSuccess { message("Backup gespeichert") }
                .onFailure { message("Backup fehlgeschlagen: ${it.message}") }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { writeText(context, uri, BackupManager.toCsv(repo.snapshot())) }
                .onSuccess { message("CSV exportiert") }
                .onFailure { message("Export fehlgeschlagen: ${it.message}") }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { BackupManager.fromJson(readText(context, uri)) }
                .onSuccess { pendingImport = it }
                .onFailure { message(it.message ?: "Import fehlgeschlagen") }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Einstellungen", { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Monatsbudget") {
                Text(
                    "Nur Ausgaben, deren Typ „zählt zum Budget“ hat, werden vom Budget abgezogen. " +
                        "Fixkosten und Sonderausgaben (Werkstatt, Urlaub …) bleiben außen vor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val input = budgetInput.orEmpty()
                MoneyField(input, { budgetInput = it }, "Budget pro Monat", modifier = Modifier.padding(top = 8.dp))
                Text("Warnung ab ${warn.toInt()} % verbraucht", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = warn,
                    onValueChange = { warn = it },
                    valueRange = 50f..100f,
                    steps = 9,
                    onValueChangeFinished = { scope.launch { repo.setWarnPercent(warn.toInt()) } },
                )
                val parsed = if (input.isBlank()) 0L else Money.parse(input)
                Button(
                    enabled = parsed != null && parsed >= 0 && parsed != settings?.monthlyBudgetCents,
                    onClick = {
                        scope.launch {
                            repo.setBudget(parsed ?: 0)
                            message("Budget gespeichert")
                        }
                    },
                ) { Text("Budget speichern") }
            }

            SectionCard(
                title = "Ausgabentypen",
                trailing = {
                    IconButton(onClick = { editType = ExpenseTypeEntity(name = "", countsTowardBudget = false, sortOrder = types.size) }) {
                        Icon(Icons.Default.Add, "Ausgabentyp hinzufügen")
                    }
                },
            ) {
                types.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (t.countsTowardBudget) "zählt zum Budget" else "zählt nicht zum Budget",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = t.countsTowardBudget,
                            onCheckedChange = { checked -> scope.launch { repo.saveExpenseType(t.copy(countsTowardBudget = checked)) } },
                        )
                        IconButton(onClick = { editType = t }) { Icon(Icons.Default.Edit, "Umbenennen") }
                        if (!t.builtin) {
                            IconButton(onClick = { deleteType = t }) { Icon(Icons.Default.Delete, "Löschen") }
                        }
                    }
                }
            }

            SectionCard(
                title = "Kategorien",
                trailing = {
                    IconButton(onClick = {
                        editCategory = CategoryEntity(name = "", color = Palette[categories.size % Palette.size], sortOrder = categories.size)
                    }) { Icon(Icons.Default.Add, "Kategorie hinzufügen") }
                },
            ) {
                categories.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editCategory = c }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(Color(c.color.toInt()), size = 14)
                        HSpace(12)
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { deleteCategory = c }) { Icon(Icons.Default.Delete, "Löschen") }
                    }
                }
            }

            SectionCard(title = "Datensicherung") {
                Text(
                    "Alle Daten liegen nur auf diesem Gerät. Sichere sie regelmäßig, z. B. in deine Nextcloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportJson.launch("EzPlanner-Backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Backup exportieren")
                    }
                    OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Backup importieren")
                    }
                    OutlinedButton(onClick = { exportCsv.launch("EzPlanner-Buchungen-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Buchungen als CSV exportieren")
                    }
                }
            }

            val updater = LocalUpdater.current
            val updateState by updater.state.collectAsStateWithLifecycle()
            var showChangelog by remember { mutableStateOf(false) }
            SectionCard(title = "App-Version") {
                Text("EzPlanner ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                if (updater.enabled) {
                    Text(
                        updateStatusText(updateState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (updater.enabled) {
                        Button(onClick = {
                            when (val u = updateState) {
                                is UpdateState.Available -> updater.download(u.release)
                                is UpdateState.Failed -> u.release?.let(updater::download) ?: updater.check()
                                is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> Unit
                                else -> updater.check()
                            }
                        }) { Text(if (updateState is UpdateState.Available) "Aktualisieren" else "Nach Updates suchen") }
                    }
                    OutlinedButton(onClick = { showChangelog = true }) { Text("Änderungen") }
                }
            }
            if (showChangelog) {
                val entries = remember { Changelog.load(context) }
                ChangelogDialog("Änderungen", entries, onDismiss = { showChangelog = false })
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }

    editType?.let { t ->
        NameDialog(
            title = if (t.id == 0L) "Neuer Ausgabentyp" else "Ausgabentyp bearbeiten",
            initial = t.name,
            onDismiss = { editType = null },
            extra = null,
        ) { name -> scope.launch { repo.saveExpenseType(t.copy(name = name)) } }
    }
    editCategory?.let { c ->
        var color by remember(c) { mutableStateOf(c.color) }
        NameDialog(
            title = if (c.id == 0L) "Neue Kategorie" else "Kategorie bearbeiten",
            initial = c.name,
            onDismiss = { editCategory = null },
            extra = { ColorPicker(color) { color = it } },
        ) { name -> scope.launch { repo.saveCategory(c.copy(name = name, color = color)) } }
    }
    deleteType?.let { t ->
        ConfirmDialog(
            title = "Ausgabentyp löschen?",
            text = "Buchungen und Daueraufträge mit „${t.name}“ werden zu „${types.firstOrNull { it.id == DefaultData.TYPE_SPECIAL }?.name ?: "Sonderausgaben"}“ verschoben.",
            confirmLabel = "Löschen",
            onConfirm = { scope.launch { repo.deleteExpenseType(t) } },
            onDismiss = { deleteType = null },
        )
    }
    deleteCategory?.let { c ->
        ConfirmDialog(
            title = "Kategorie löschen?",
            text = "Buchungen mit „${c.name}“ behalten ihren Betrag, haben danach aber keine Kategorie mehr.",
            confirmLabel = "Löschen",
            onConfirm = { scope.launch { repo.deleteCategory(c) } },
            onDismiss = { deleteCategory = null },
        )
    }
    pendingImport?.let { snap ->
        ConfirmDialog(
            title = "Backup importieren?",
            text = "Alle aktuellen Daten werden ersetzt durch das Backup mit ${snap.transactions.size} Buchungen, " +
                "${snap.pots.size} Töpfen und ${snap.standingOrders.size} Daueraufträgen. Das kann nicht rückgängig gemacht werden.",
            confirmLabel = "Ersetzen",
            onConfirm = {
                scope.launch {
                    runCatching { repo.restore(snap) }
                        .onSuccess {
                            budgetInput = Money.toInput(snap.settings.monthlyBudgetCents)
                            repo.runDueStandingOrders()
                            message("Backup importiert")
                        }
                        .onFailure { message("Import fehlgeschlagen: ${it.message}") }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)?,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()); onDismiss() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palette.forEach { c ->
            Box(
                Modifier
                    .size(32.dp)
                    .background(Color(c.toInt()), CircleShape)
                    .then(
                        if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                    )
                    .clickable { onSelect(c) },
            )
        }
    }
}

private suspend fun writeText(context: android.content.Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        ?: error("Datei konnte nicht geöffnet werden")
}

private suspend fun readText(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("Datei konnte nicht geöffnet werden")
}
