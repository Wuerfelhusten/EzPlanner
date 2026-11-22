package com.moddingforge.ezplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moddingforge.ezplanner.BuildConfig
import com.moddingforge.ezplanner.update.Changelog
import com.moddingforge.ezplanner.update.ChangelogEntry
import com.moddingforge.ezplanner.update.UpdateState
import com.moddingforge.ezplanner.update.Updater

/** Floating card: "EzPlanner x.y ist verfügbar [Aktualisieren]", then download progress and install. */
@Composable
fun UpdateBanner(updater: Updater, modifier: Modifier = Modifier) {
    val state by updater.state.collectAsStateWithLifecycle()
    var dismissedFor by rememberSaveable { mutableStateOf("") }
    val visible = when (val s = state) {
        is UpdateState.Available -> s.release.version != dismissedFor
        is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.Failed -> true
        else -> false
    }
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            UpdateCardContent(state, updater, onDismiss = { (state as? UpdateState.Available)?.let { dismissedFor = it.release.version } })
        }
    }
}

@Composable
private fun UpdateCardContent(state: UpdateState, updater: Updater, onDismiss: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(updateStatusText(state), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Release notes of the new version (the GitHub release text = its CHANGELOG.md section).
                if (state is UpdateState.Available && state.release.notes.isNotBlank()) {
                    var showNotes by remember { mutableStateOf(false) }
                    Text(
                        "Was ist neu?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showNotes = true }.padding(vertical = 4.dp),
                    )
                    if (showNotes) {
                        ChangelogDialog(
                            "Neu in ${state.release.version}",
                            listOf(ChangelogEntry(state.release.version, state.release.notes)),
                            onDismiss = { showNotes = false },
                        )
                    }
                }
            }
            when (state) {
                is UpdateState.Available -> {
                    FilledTonalButton(onClick = { updater.download(state.release) }) { Text("Aktualisieren") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Schließen") }
                }
                is UpdateState.Downloading -> TextButton(onClick = updater::cancel) { Text("Abbrechen") }
                is UpdateState.Failed -> {
                    TextButton(onClick = { state.release?.let(updater::download) ?: updater.check() }) { Text("Erneut") }
                    IconButton(onClick = updater::reset) { Icon(Icons.Outlined.Close, "Schließen") }
                }
                is UpdateState.Installing -> TextButton(onClick = updater::reset) { Text("Schließen") }
                else -> Unit
            }
        }
        if (state is UpdateState.Downloading) {
            val p = state.progress
            val m = Modifier.fillMaxWidth().padding(end = 10.dp, top = 2.dp, bottom = 6.dp)
            if (p >= 0f) LinearProgressIndicator(progress = { p }, modifier = m) else LinearProgressIndicator(modifier = m)
        }
    }
}

/** One-line status, shared with the settings screen. */
fun updateStatusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Nach Updates suchen"
    UpdateState.Checking -> "Suche nach Updates …"
    UpdateState.UpToDate -> "EzPlanner ist aktuell"
    is UpdateState.Available -> "EzPlanner ${state.release.version} ist verfügbar"
    is UpdateState.Downloading ->
        if (state.progress >= 0f) "Lade Update … ${(state.progress * 100).toInt()} %" else "Lade Update …"
    is UpdateState.Installing -> "Installation bestätigen …"
    is UpdateState.Failed -> "Update fehlgeschlagen: ${state.message}"
}

/** Changelog entries in a dialog: one block per version, newest first. */
@Composable
fun ChangelogDialog(title: String, entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (entries.isEmpty()) Text("Keine Einträge.")
                entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    if (entries.size > 1) {
                        Text("Version ${e.version}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(Changelog.plain(e.text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Shown once after every update with what changed since the version the user last saw. */
@Composable
fun WhatsNewPopup(updater: Updater) {
    val entries by updater.whatsNew.collectAsStateWithLifecycle()
    val list = entries ?: return
    val version = list.firstOrNull()?.version ?: BuildConfig.VERSION_NAME
    ChangelogDialog("Neu in $version", list, onDismiss = updater::markWhatsNewSeen)
}
