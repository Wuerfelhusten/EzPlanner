package com.moddingforge.ezplanner.ui.screens.pots

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.components.BackTopBar
import com.moddingforge.ezplanner.ui.components.ChoiceChips
import com.moddingforge.ezplanner.ui.components.MoneyField
import kotlinx.coroutines.launch
import java.time.LocalDate

class PotForm : ViewModel() {
    var loaded = false
    var existing by mutableStateOf<PotEntity?>(null)
    var name by mutableStateOf("")
    var kind by mutableStateOf(PotKind.SAVINGS)
    var person by mutableStateOf("")
    var direction by mutableStateOf(DebtDirection.OWES_ME)
    var target by mutableStateOf("")
    var initial by mutableStateOf("")
    /** Balance when the pot was opened for editing. */
    var loadedBalance by mutableStateOf<Long?>(null)
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
}

@Composable
fun PotEditScreen(nav: NavController, id: Long, initialKind: PotKind) {
    val repo = LocalRepository.current
    val form: PotForm = viewModel(key = "pot-$id-$initialKind")
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        if (form.loaded) return@LaunchedEffect
        form.loaded = true
        form.kind = initialKind
        if (id != 0L) {
            val p = repo.getPot(id) ?: return@LaunchedEffect
            val balance = repo.getPotBalance(p)
            form.loadedBalance = balance
            form.initial = Money.toInput(balance).ifEmpty { "0,00" }
            form.existing = p
            form.name = p.name
            form.kind = p.kind
            form.person = p.person.orEmpty()
            form.direction = p.direction ?: DebtDirection.OWES_ME
            form.target = p.targetCents?.let(Money::toInput).orEmpty()
            form.note = p.note
        }
    }

    val existing = form.existing
    val isDebt = form.kind == PotKind.DEBT

    fun save() {
        val initial = if (form.initial.isBlank()) 0L else Money.parse(form.initial)
        val target = if (form.target.isBlank()) null else Money.parse(form.target)
        val nameOk = form.name.isNotBlank() || (isDebt && form.person.isNotBlank())
        if (!nameOk || initial == null || (form.target.isNotBlank() && target == null)) {
            form.showErrors = true
            return
        }
        val pot = (existing ?: PotEntity(name = "", kind = form.kind)).copy(
            name = form.name.trim().ifEmpty { form.person.trim() },
            kind = form.kind,
            person = if (isDebt) form.person.trim() else null,
            direction = if (isDebt) form.direction else null,
            targetCents = if (isDebt) null else target,
            note = form.note.trim(),
        )
        scope.launch {
            if (existing != null) {
                // Only correct the balance when the user changed it.
                repo.savePotWithBalance(pot, initial.takeIf { form.initial.isNotBlank() && it != form.loadedBalance })
                nav.popBackStack()
                return@launch
            }
            val potId = repo.savePot(pot)
            if (initial != 0L) {
                // Starting amount: pot only, the account is not touched.
                repo.saveTransaction(
                    TransactionEntity(
                        date = LocalDate.now(),
                        amountCents = initial,
                        kind = TxKind.POT_ADJUSTMENT,
                        title = "Anfangsbestand",
                        potId = potId,
                    ),
                )
            }
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                when {
                    existing != null -> "Topf bearbeiten"
                    isDebt -> "Neuer Schuldentopf"
                    else -> "Neuer Spartopf"
                },
                onBack = { nav.popBackStack() },
            )
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
            if (existing == null) {
                ChoiceChips(
                    title = "Art",
                    options = PotKind.entries,
                    selected = form.kind,
                    onSelect = { form.kind = it },
                    label = { if (it == PotKind.SAVINGS) "Spartopf" else "Schuldentopf" },
                )
            }
            if (isDebt) {
                ChoiceChips(
                    title = "Richtung",
                    options = DebtDirection.entries,
                    selected = form.direction,
                    onSelect = { form.direction = it },
                    label = { if (it == DebtDirection.OWES_ME) "Jemand schuldet mir" else "Ich schulde jemandem" },
                )
                OutlinedTextField(
                    value = form.person,
                    onValueChange = { form.person = it },
                    label = { Text("Person") },
                    singleLine = true,
                    isError = form.showErrors && form.person.isBlank() && form.name.isBlank(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { form.name = it },
                label = { Text(if (isDebt) "Bezeichnung (optional, z. B. „Konzertkarten“)" else "Name (z. B. Notgroschen)") },
                singleLine = true,
                isError = form.showErrors && form.name.isBlank() && !(isDebt && form.person.isNotBlank()),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isDebt) {
                MoneyField(form.target, { form.target = it }, "Sparziel (optional)")
            }
            if (existing != null) {
                val diff = (Money.parse(form.initial) ?: form.loadedBalance ?: 0L) - (form.loadedBalance ?: 0L)
                MoneyField(
                    form.initial,
                    { form.initial = it },
                    if (isDebt) "Aktuell offener Betrag" else "Aktueller Stand",
                    allowNegative = true,
                    supportingText = if (diff == 0L) {
                        "Ändert nur den Topf – kein Einkommen, keine Ausgabe, Konto bleibt gleich."
                    } else {
                        "Korrektur nur im Topf: ${Money.formatSigned(diff)}"
                    },
                )
            } else {
                MoneyField(
                    form.initial,
                    { form.initial = it },
                    if (isDebt) "Aktuell offener Betrag (optional)" else "Aktueller Stand (optional)",
                    supportingText = "Wird nur im Topf erfasst, das Konto bleibt unverändert.",
                )
            }
            OutlinedTextField(
                value = form.note,
                onValueChange = { form.note = it },
                label = { Text("Notiz (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isDebt) {
                Text(
                    "Verleihen und Rückzahlungen buchst du danach direkt im Topf – so bleibt der Verlauf erhalten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Speichern") }
        }
    }
}
