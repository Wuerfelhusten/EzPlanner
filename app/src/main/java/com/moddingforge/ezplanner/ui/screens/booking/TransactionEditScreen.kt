package com.moddingforge.ezplanner.ui.screens.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.actionLabel
import com.moddingforge.ezplanner.ui.components.BackTopBar
import com.moddingforge.ezplanner.ui.components.ConfirmDialog
import com.moddingforge.ezplanner.ui.components.DateField
import com.moddingforge.ezplanner.ui.components.MoneyField
import com.moddingforge.ezplanner.ui.formatDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Form state; a ViewModel so it survives rotation. */
class TxForm : ViewModel() {
    var loaded = false
    var existing by mutableStateOf<TransactionEntity?>(null)
    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")
    var cls by mutableStateOf(Classification())
    var showErrors by mutableStateOf(false)
}

@Composable
fun TransactionEditScreen(nav: NavController, id: Long, initialKind: TxKind, initialPotId: Long?) {
    val repo = LocalRepository.current
    val form: TxForm = viewModel(key = "tx-$id-$initialKind-$initialPotId")
    val scope = rememberCoroutineScope()
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val allPots by repo.pots.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var orderTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (form.loaded) return@LaunchedEffect
        form.loaded = true
        if (id != 0L) {
            val tx = repo.getTransaction(id)
            if (tx == null) {
                nav.popBackStack()
                return@LaunchedEffect
            }
            form.existing = tx
            form.title = tx.title
            form.amount = Money.toInput(tx.amountCents)
            form.date = tx.date
            form.note = tx.note
            form.cls = Classification.of(tx.kind, tx.expenseTypeId, tx.categoryId, tx.incomeType, tx.potId)
        } else {
            form.cls = Classification.of(initialKind, null, null, null, initialPotId)
        }
    }
    LaunchedEffect(form.existing?.standingOrderId) {
        orderTitle = form.existing?.standingOrderId?.let { repo.getStandingOrder(it)?.title }
    }

    val existing = form.existing
    val isAdjustment = existing?.kind == TxKind.ADJUSTMENT
    val kind = if (isAdjustment) TxKind.ADJUSTMENT else form.cls.kind
    val signedAllowed = kind == TxKind.ADJUSTMENT || kind == TxKind.POT_ADJUSTMENT
    val pots = allPots.filter { !it.archived || it.id == form.cls.potId }
    val selectedPot = pots.firstOrNull { it.id == form.cls.potId }

    val screenTitle = when {
        isAdjustment -> "Kontostand-Anpassung"
        existing != null -> "Buchung bearbeiten"
        else -> "Neue Buchung"
    }

    fun save() {
        val cents = Money.parse(form.amount)
        val amountOk = cents != null && cents != 0L && (signedAllowed || cents > 0)
        val potOk = form.cls.group != KindGroup.POT || selectedPot != null
        if (!amountOk || !potOk) {
            form.showErrors = true
            return
        }
        val cls = form.cls
        val defaultTitle = when (kind) {
            TxKind.EXPENSE -> categories.firstOrNull { it.id == cls.categoryId }?.name
                ?: types.firstOrNull { it.id == cls.expenseTypeId }?.name ?: "Ausgabe"
            TxKind.INCOME -> "Einkommen"
            TxKind.ADJUSTMENT -> "Kontostand-Anpassung"
            else -> "${selectedPot?.name}: ${selectedPot.actionLabel(kind)}"
        }
        val base = existing ?: TransactionEntity(date = form.date, amountCents = 0, kind = kind, title = "")
        val tx = base.copy(
            date = form.date,
            amountCents = cents!!,
            kind = kind,
            title = form.title.trim().ifEmpty { defaultTitle },
            note = form.note.trim(),
            expenseTypeId = if (kind == TxKind.EXPENSE) cls.expenseTypeId else null,
            categoryId = if (kind == TxKind.EXPENSE) cls.categoryId else null,
            incomeType = if (kind == TxKind.INCOME) cls.incomeType else null,
            potId = if (cls.group == KindGroup.POT && !isAdjustment) cls.potId else null,
        )
        scope.launch {
            repo.saveTransaction(tx)
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(screenTitle, onBack = { nav.popBackStack() }) {
                if (existing != null) {
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
            if (orderTitle != null) {
                Text(
                    "Aus Dauerauftrag „$orderTitle“ (fällig am ${existing?.dueDate?.formatDate()}). " +
                        "Änderungen gelten nur für diese Buchung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isAdjustment) {
                ClassificationFields(
                    value = form.cls,
                    onChange = { form.cls = it },
                    types = types,
                    categories = categories,
                    pots = pots,
                    allowPotAdjustment = true,
                    showError = form.showErrors,
                    onCreatePot = { nav.navigate(Routes.newPot(PotKind.SAVINGS)) },
                )
            }
            MoneyField(
                value = form.amount,
                onValueChange = { form.amount = it },
                label = "Betrag",
                allowNegative = signedAllowed,
                showError = form.showErrors,
                supportingText = if (signedAllowed) "Negativ für Abzug" else null,
            )
            OutlinedTextField(
                value = form.title,
                onValueChange = { form.title = it },
                label = { Text("Titel (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(form.date, { form.date = it }, "Datum")
            OutlinedTextField(
                value = form.note,
                onValueChange = { form.note = it },
                label = { Text("Notiz (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (existing != null) "Speichern" else "Buchen") }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Buchung löschen?",
            text = "„${existing.title}“ über ${Money.format(existing.amountCents)} wird gelöscht. " +
                "Kontostand und Töpfe werden entsprechend angepasst.",
            confirmLabel = "Löschen",
            onConfirm = {
                scope.launch {
                    repo.deleteTransaction(existing)
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
