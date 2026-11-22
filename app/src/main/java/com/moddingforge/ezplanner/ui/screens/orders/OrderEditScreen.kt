package com.moddingforge.ezplanner.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.actionLabel
import com.moddingforge.ezplanner.ui.components.BackTopBar
import com.moddingforge.ezplanner.ui.components.ChoiceChips
import com.moddingforge.ezplanner.ui.components.ConfirmDialog
import com.moddingforge.ezplanner.ui.components.DateField
import com.moddingforge.ezplanner.ui.components.MoneyField
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.screens.booking.Classification
import com.moddingforge.ezplanner.ui.screens.booking.ClassificationFields
import com.moddingforge.ezplanner.ui.screens.booking.KindGroup
import kotlinx.coroutines.launch
import java.time.LocalDate

class OrderForm : ViewModel() {
    var loaded = false
    var existing by mutableStateOf<StandingOrderEntity?>(null)
    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    // Standing orders are mostly fixed costs, so "Ausgabe / Fixkosten" is preselected.
    var cls by mutableStateOf(Classification(group = KindGroup.EXPENSE, expenseTypeId = DefaultData.TYPE_FIXED))
    var interval by mutableStateOf(Interval.MONTHLY)
    var startDate by mutableStateOf(LocalDate.now())
    var hasEnd by mutableStateOf(false)
    var endDate by mutableStateOf(LocalDate.now().plusYears(1))
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
}

@Composable
fun OrderEditScreen(nav: NavController, id: Long) {
    val repo = LocalRepository.current
    val form: OrderForm = viewModel(key = "order-$id")
    val scope = rememberCoroutineScope()
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val allPots by repo.pots.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (form.loaded || id == 0L) return@LaunchedEffect
        form.loaded = true
        val o = repo.getStandingOrder(id)
        if (o == null) {
            nav.popBackStack()
            return@LaunchedEffect
        }
        form.existing = o
        form.title = o.title
        form.amount = Money.toInput(o.amountCents)
        form.cls = Classification.of(o.kind, o.expenseTypeId, o.categoryId, o.incomeType, o.potId, DefaultData.TYPE_FIXED)
        form.interval = o.interval
        form.startDate = o.startDate
        form.hasEnd = o.endDate != null
        o.endDate?.let { form.endDate = it }
        form.note = o.note
    }

    val existing = form.existing
    val pots = allPots.filter { !it.archived || it.id == form.cls.potId }
    val selectedPot = pots.firstOrNull { it.id == form.cls.potId }
    val today = LocalDate.now()

    val draft = StandingOrderEntity(
        id = existing?.id ?: 0,
        title = form.title.trim(),
        amountCents = Money.parse(form.amount) ?: 0,
        kind = form.cls.kind,
        interval = form.interval,
        startDate = form.startDate,
        endDate = if (form.hasEnd) form.endDate else null,
        nextIndex = 0,
        expenseTypeId = if (form.cls.kind == TxKind.EXPENSE) form.cls.expenseTypeId else null,
        categoryId = if (form.cls.kind == TxKind.EXPENSE) form.cls.categoryId else null,
        incomeType = if (form.cls.kind == TxKind.INCOME) form.cls.incomeType else null,
        potId = if (form.cls.group == KindGroup.POT) form.cls.potId else null,
        active = existing?.active ?: true,
        note = form.note.trim(),
    )

    fun save() {
        val cents = Money.parse(form.amount)
        val titleOk = form.title.isNotBlank()
        val amountOk = cents != null && cents > 0
        val potOk = form.cls.group != KindGroup.POT || selectedPot != null
        val endOk = !form.hasEnd || !form.endDate.isBefore(form.startDate)
        if (!titleOk || !amountOk || !potOk || !endOk) {
            form.showErrors = true
            return
        }
        scope.launch {
            repo.saveStandingOrder(draft)
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(if (existing != null) "Dauerauftrag bearbeiten" else "Dauerauftrag einrichten", { nav.popBackStack() }) {
                if (existing != null) {
                    IconButton(onClick = {
                        scope.launch {
                            repo.setStandingOrderActive(existing, !existing.active)
                            form.existing = repo.getStandingOrder(existing.id)
                        }
                    }) {
                        if (existing.active) Icon(Icons.Default.Pause, "Pausieren") else Icon(Icons.Default.PlayArrow, "Fortsetzen")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen") }
                }
            }
        },
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
            if (existing != null && !existing.active) {
                Text("Dieser Dauerauftrag ist pausiert.", color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = form.title,
                onValueChange = { form.title = it },
                label = { Text("Titel") },
                singleLine = true,
                isError = form.showErrors && form.title.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(form.amount, { form.amount = it }, "Betrag", showError = form.showErrors)
            ClassificationFields(
                value = form.cls,
                onChange = { form.cls = it },
                types = types,
                categories = categories,
                pots = pots,
                allowPotAdjustment = false,
                showError = form.showErrors,
                onCreatePot = { nav.navigate(Routes.newPot(PotKind.SAVINGS)) },
            )
            ChoiceChips(
                title = "Fälligkeit",
                options = Interval.entries,
                selected = form.interval,
                onSelect = { form.interval = it },
                label = { it.label() },
            )
            DateField(form.startDate, { form.startDate = it }, "Erste Ausführung")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enddatum", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.hasEnd, onCheckedChange = { form.hasEnd = it })
            }
            if (form.hasEnd) {
                DateField(form.endDate, { form.endDate = it }, "Letzte Ausführung spätestens")
                if (form.showErrors && form.endDate.isBefore(form.startDate)) {
                    Text("Enddatum liegt vor der ersten Ausführung", color = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = form.note,
                onValueChange = { form.note = it },
                label = { Text("Notiz (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            SchedulePreview(draft, isNew = existing == null, today = today, potLabel = selectedPot.actionLabel(draft.kind))

            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (existing != null) "Speichern" else "Dauerauftrag einrichten") }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Dauerauftrag löschen?",
            text = "„${existing.title}“ wird nicht mehr ausgeführt. Bereits gebuchte Einträge bleiben im Verlauf.",
            confirmLabel = "Löschen",
            onConfirm = {
                scope.launch {
                    repo.deleteStandingOrder(existing)
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SchedulePreview(draft: StandingOrderEntity, isNew: Boolean, today: LocalDate, potLabel: String) {
    val catchUp = if (isNew) Schedule.dueOccurrences(draft, today) else emptyList()
    val upcoming = generateSequence(0) { it + 1 }
        .map { Schedule.dueDate(draft, it) }
        .filter { it.isAfter(today) }
        .takeWhile { draft.endDate == null || !it.isAfter(draft.endDate) }
        .take(3)
        .toList()

    SectionCard(title = "Ausführung") {
        Text(
            "Gebucht wird am Fälligkeitstag ab 0 Uhr – unabhängig von der Uhrzeit, zu der der Auftrag angelegt wurde.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft.kind == TxKind.TO_POT || draft.kind == TxKind.FROM_POT) {
            Text("Vorgang: $potLabel", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        when {
            catchUp.size == 1 && catchUp[0].second == today ->
                Text("Wird heute gebucht.", modifier = Modifier.padding(top = 6.dp))
            catchUp.isNotEmpty() -> Text(
                "${catchUp.size} Termine seit ${catchUp.first().second.formatDate()} werden sofort nachgebucht.",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (upcoming.isNotEmpty()) {
            Text(
                "Nächste Termine: " + upcoming.joinToString(", ") { it.formatDate() },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (catchUp.isEmpty()) {
            Text("Keine weiteren Termine.", modifier = Modifier.padding(top = 6.dp))
        }
    }
}
