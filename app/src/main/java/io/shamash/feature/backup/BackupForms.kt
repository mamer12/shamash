package io.shamash.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.data.backup.BackupLimits
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.ImportMode
import io.shamash.feature.profiles.ProfileSession
import io.shamash.feature.settings.Explain
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.delay

/** Choose a passphrase twice and what to include; nothing is written until both match and are long enough. */
@Composable
internal fun ExportForm(onCancel: () -> Unit, onMake: (String, BackupOptions) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var keys by remember { mutableStateOf(false) }
    var pins by remember { mutableStateOf(false) }
    val tooShort = pass.length < BackupLimits.MIN_PASSPHRASE
    val mismatch = pass != again
    Explain(stringResource(R.string.backup_passphrase_hint, BackupLimits.MIN_PASSPHRASE))
    TextInput(stringResource(R.string.backup_passphrase), pass, { pass = it.take(BackupLimits.MAX_PASSPHRASE) }, secret = true)
    TextInput(stringResource(R.string.backup_passphrase_again), again, { again = it.take(BackupLimits.MAX_PASSPHRASE) }, secret = true)
    if (!tooShort && mismatch) Text(stringResource(R.string.backup_mismatch), color = Palette.Gold, fontSize = 18.sp)
    Toggle(R.string.backup_include_keys, keys) { keys = it }
    Toggle(R.string.backup_include_pins, pins) { pins = it }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { if (!tooShort && !mismatch) onMake(pass, BackupOptions(includeSecrets = keys, includePins = pins)) }) {
        Text(stringResource(R.string.backup_make))
    }
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.backup_back)) }
}

@Composable
private fun Toggle(label: Int, on: Boolean, set: (Boolean) -> Unit) {
    ListItem(
        selected = on, onClick = { set(!on) },
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { Text(stringResource(if (on) R.string.backup_on else R.string.backup_off)) },
    )
}

@Composable
internal fun PassphraseForm(waitSeconds: () -> Long, onCancel: () -> Unit, onOpen: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    val wait by produceState(waitSeconds()) { while (true) { value = waitSeconds(); delay(1000) } }
    TextInput(stringResource(R.string.backup_passphrase), pass, { pass = it.take(BackupLimits.MAX_PASSPHRASE) }, secret = true)
    if (wait > 0) Text(stringResource(R.string.backup_wait, wait), color = Palette.Gold, fontSize = 18.sp)
    Button(onClick = { if (wait == 0L && pass.isNotEmpty()) onOpen(pass) }) { Text(stringResource(R.string.backup_open)) }
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.backup_back)) }
}

/** What the file holds, then Merge or Replace. Replace is explained first: it swaps the other profiles. */
@Composable
internal fun PreviewForm(
    payload: BackupPayload, actions: BackupActions, owner: ProfileSession,
    onCancel: () -> Unit, onApply: (ImportMode) -> Unit,
) {
    val preview by produceState<io.shamash.data.backup.ImportPreview?>(null, payload) { value = actions.preview(payload) }
    val p = preview ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.backup_preview_title), color = Palette.Gold, fontSize = 24.sp)
        p.profiles.forEach { row ->
            ListItem(
                selected = false, onClick = {},
                headlineContent = { Text(row.name.ifBlank { stringResource(R.string.profile_guest) }) },
                supportingContent = {
                    Text(stringResource(R.string.backup_preview_row, row.rows, row.follows) +
                        if (row.exists) " " + stringResource(R.string.backup_exists) else "")
                },
            )
        }
        if (p.hasSecrets) Explain(stringResource(R.string.backup_has_keys))
        if (p.hasPins) Explain(stringResource(R.string.backup_has_pins))
        Explain(stringResource(R.string.backup_replace_warn))
    }
    Button(onClick = { onApply(ImportMode.MERGE) }) { Text(stringResource(R.string.backup_merge)) }
    Button(onClick = { onApply(ImportMode.REPLACE) }) { Text(stringResource(R.string.backup_replace)) }
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.backup_back)) }
}
