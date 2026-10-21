package io.shamash.feature.companion

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Sources
import io.shamash.data.backup.ImportMode
import io.shamash.data.backup.LocalBackups
import io.shamash.feature.backup.BackupActions
import io.shamash.feature.backup.PreviewForm
import io.shamash.feature.profiles.PinCheck
import io.shamash.feature.profiles.PinPad
import io.shamash.feature.profiles.pinMessage
import io.shamash.ui.Palette
import io.shamash.ui.PhoneEntryHook
import kotlinx.coroutines.launch
import java.io.File

/*
 * What the TV shows for the phone: the backup questions (CMP-13, CMP-15) over whatever is on screen, and the code
 * next to each text field the phone can type into (CMP-08). Public surface: PhoneOnTv. SPEC: CMP-08, CMP-13, CMP-15
 */
object PhoneOnTv {
    /** Lets every TextInput offer phone entry; called once the companion starts. */
    fun installFieldOffer() {
        PhoneEntryHook.offer = { label, secret, onValue -> FieldOffer(label, secret, onValue) }
    }

    /** The overlay for questions from the phone; place it once, over the app's content. */
    @Composable
    fun Prompts(locator: ServiceLocator) {
        val prompt by PhonePrompts.current.collectAsState()
        when (val p = prompt) {
            null -> Unit
            is PhonePrompt.Export -> Dialog(onDismissRequest = { p.answer.complete(false) }) { ExportAsk(p) }
            is PhonePrompt.Import -> Dialog(onDismissRequest = { p.answer.complete(null) }) { ImportAsk(locator, p) }
        }
    }
}

/** Registers the field while it is on screen and shows its code; the value is handed over on the main thread. */
@Composable
private fun FieldOffer(label: String, secret: Boolean, onValue: (String) -> Unit) {
    if (!Companion.isRunning) return
    val latest by rememberUpdatedState(onValue)
    var code by remember { mutableStateOf<String?>(null) }
    DisposableEffect(label, secret) {
        val main = Handler(Looper.getMainLooper())
        val offered = PhoneFields.shared.offer(label, secret) { value -> main.post { latest(value) } }
        code = offered
        onDispose { offered?.let(PhoneFields.shared::withdraw) }
    }
    code?.let { Text(stringResource(R.string.cmp_field_code, it), color = Palette.Faint, fontSize = 15.sp) }
}

private val DialogShape = RoundedCornerShape(24.dp)

@Composable
private fun PromptCard(content: @Composable () -> Unit) {
    Column(
        Modifier.width(720.dp).background(Palette.Dusk, DialogShape).padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun ExportAsk(p: PhonePrompt.Export) {
    val no = remember { FocusRequester() }
    LaunchedEffect(p) { runCatching { no.requestFocus() } }
    PromptCard {
        Text(stringResource(R.string.cmp_prompt_export_title), color = Palette.Gold, fontSize = 26.sp)
        Text(stringResource(R.string.cmp_prompt_export_text), color = Palette.Ink, fontSize = 18.sp)
        Button(onClick = { p.answer.complete(true) }) { Text(stringResource(R.string.cmp_prompt_yes)) }
        OutlinedButton(onClick = { p.answer.complete(false) }, modifier = Modifier.focusRequester(no)) {
            Text(stringResource(R.string.cmp_prompt_no))
        }
    }
}

/** The file's preview, then Merge, or Replace after the owner's PIN when one is set. SPEC: CMP-15 */
@Composable
private fun ImportAsk(locator: ServiceLocator, p: PhonePrompt.Import) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = locator.session
    val actions = remember(locator) {
        BackupActions(locator.prefs, session.security, Sources.addOns, LocalBackups(File(context.noBackupFilesDir, "local-backups")))
    }
    var askOwner by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    PromptCard {
        Text(stringResource(R.string.cmp_prompt_import_title), color = Palette.Gold, fontSize = 26.sp)
        if (askOwner) {
            PinPad(stringResource(R.string.pin_owner_to_edit), message, enabled = true, onCancel = { askOwner = false }, onComplete = { pin ->
                scope.launch {
                    val r = session.verifyOwner(pin)
                    message = context.pinMessage(r)
                    if (r == PinCheck.Ok) p.answer.complete(ImportMode.REPLACE)
                }
            })
            return@PromptCard
        }
        PreviewForm(
            payload = p.payload, actions = actions, owner = session,
            onCancel = { p.answer.complete(null) },
            onApply = { mode ->
                val ownerHasPin = session.profiles.value.firstOrNull()?.pinHash != null
                if (mode == ImportMode.REPLACE && ownerHasPin && !session.ownerRecentlyVerified()) askOwner = true
                else p.answer.complete(mode)
            },
        )
    }
}
