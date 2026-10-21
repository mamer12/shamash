package io.shamash.feature.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.data.Sources
import io.shamash.data.backup.BackupAttempts
import io.shamash.data.backup.BackupCodec
import io.shamash.data.backup.BackupError
import io.shamash.data.backup.BackupLimits
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.BackupResult
import io.shamash.data.backup.ImportMode
import io.shamash.data.backup.LocalBackups
import io.shamash.feature.profiles.PinCheck
import io.shamash.feature.profiles.PinPad
import io.shamash.feature.profiles.pinMessage
import io.shamash.feature.settings.Explain
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Where the panel is. Secrets typed here live only in this state and are dropped when it changes. */
private sealed interface Step {
    data object Home : Step
    data object ExportForm : Step
    data class Saving(val bytes: ByteArray, val name: String) : Step
    data class Passphrase(val file: ByteArray) : Step
    data class Preview(val payload: BackupPayload) : Step
    data class Message(val text: Int, val arg: String? = null) : Step
}

/**
 * Settings > Backup (PLT-01): export to an encrypted file and import from one, on the TV. The owner's PIN comes
 * first when one is set. Wrong passphrases are limited to three tries per minute. SPEC: PLT-01, CMP-13..15
 */
@Composable
fun BackupPanel(locator: ServiceLocator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = locator.session
    val actions = remember(locator) {
        BackupActions(locator.prefs, session.security, Sources.addOns, LocalBackups(File(context.noBackupFilesDir, "local-backups")))
    }
    val attempts = remember { BackupAttempts() }
    var step by remember { mutableStateOf<Step>(Step.Home) }
    var verified by remember { mutableStateOf(session.ownerRecentlyVerified()) }
    val ownerHasPin = session.profiles.value.firstOrNull()?.pinHash != null

    if (ownerHasPin && !verified) {
        var message by remember { mutableStateOf<String?>(null) }
        PinPad(stringResource(R.string.pin_owner_to_edit), message, enabled = true, onCancel = null, onComplete = { pin ->
            scope.launch {
                val r = session.verifyOwner(pin)
                message = context.pinMessage(r)
                if (r == PinCheck.Ok) verified = true
            }
        })
        return
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val current = step as? Step.Saving ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = uri != null && BackupFiles.write(context, uri, current.bytes)
            step = if (ok) Step.Message(R.string.backup_saved) else Step.Message(R.string.backup_cancelled)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { step = loaded(BackupFiles.read(context, uri)) }
    }

    fun pickToSave(s: Step.Saving) {
        step = s
        try { saveLauncher.launch(s.name) } catch (e: ActivityNotFoundException) {
            scope.launch {
                val f = BackupFiles.saveInFolder(context, s.name, s.bytes)
                step = if (f != null) Step.Message(R.string.backup_saved_to, f.absolutePath) else Step.Message(R.string.backup_cancelled)
            }
        }
    }

    fun pickToOpen() {
        try { openLauncher.launch(arrayOf("*/*")) } catch (e: ActivityNotFoundException) {
            val newest = BackupFiles.inFolder(context).firstOrNull()
            if (newest == null) step = Step.Message(R.string.backup_no_files)
            else scope.launch { step = loaded(BackupFiles.read(newest)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier) {
        when (val s = step) {
            Step.Home -> {
                Explain(stringResource(R.string.backup_intro))
                Button(onClick = { step = Step.ExportForm }) { Text(stringResource(R.string.backup_export)) }
                Button(onClick = ::pickToOpen) { Text(stringResource(R.string.backup_import)) }
            }
            Step.ExportForm -> ExportForm(
                onCancel = { step = Step.Home },
                onMake = { pass, options ->
                    scope.launch {
                        step = Step.Message(R.string.backup_working)
                        when (val r = actions.export(pass, options)) {
                            is BackupResult.Ok -> pickToSave(Step.Saving(r.value, BackupCodec.fileName(LocalDate.now().toString())))
                            is BackupResult.Failed -> step = Step.Message(BackupActions.message(r.error))
                        }
                    }
                },
            )
            is Step.Saving -> Explain(stringResource(R.string.backup_working))
            is Step.Passphrase -> PassphraseForm(
                waitSeconds = { (attempts.waitLeftMs() + 999) / 1000 },
                onCancel = { step = Step.Home },
                onOpen = { pass ->
                    scope.launch {
                        if (attempts.waitLeftMs() > 0) { step = Step.Passphrase(s.file); return@launch }
                        when (val r = actions.open(s.file, pass)) {
                            is BackupResult.Ok -> { attempts.succeeded(); step = Step.Preview(r.value) }
                            is BackupResult.Failed -> {
                                if (r.error == BackupError.WRONG_PASSPHRASE_OR_DAMAGED) attempts.failed()
                                step = Step.Message(BackupActions.message(r.error))
                            }
                        }
                    }
                },
            )
            is Step.Preview -> PreviewForm(
                payload = s.payload, actions = actions, owner = locator.session,
                onCancel = { step = Step.Home },
                onApply = { mode ->
                    scope.launch {
                        step = Step.Message(R.string.backup_working)
                        step = when (val r = actions.apply(s.payload, mode)) {
                            is ApplyResult.Done -> Step.Message(R.string.backup_done, "${r.added} / ${r.replaced}")
                            ApplyResult.Failed -> Step.Message(R.string.backup_failed)
                        }
                    }
                },
            )
            is Step.Message -> {
                Explain(if (s.arg == null) stringResource(s.text) else stringResource(s.text, s.arg))
                Button(onClick = { step = Step.Home }) { Text(stringResource(R.string.backup_back)) }
            }
        }
    }
}

private fun loaded(bytes: ByteArray?): Step = when {
    bytes == null -> Step.Message(R.string.backup_err_not)
    bytes.size > BackupLimits.MAX_FILE_BYTES -> Step.Message(R.string.backup_err_large)
    else -> Step.Passphrase(bytes)
}
