package io.shamash.feature.trakt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.trakt.DeviceCode
import io.shamash.data.trakt.PollAnswer
import io.shamash.data.trakt.TrackerProvider
import io.shamash.domain.Profile
import io.shamash.ui.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trakt or Simkl: "Sign in with a code" (the TV shows a code, the user approves on a phone), Sign out, and the
 * write-back switch (off by default). Kids and Guest profiles get a line saying it's for grown-ups. SPEC: PEX-26
 */
@Composable
internal fun TrackerPanel(addOnId: String, p: Profile, locator: ServiceLocator) {
    val provider = remember(locator, addOnId) { locator.providers.firstOrNull { it.id == addOnId } as? TrackerProvider } ?: return
    if (!TrackerProvider.mayUse(p)) {
        Text(stringResource(R.string.more_trakt_adults_only), color = Palette.Dim, modifier = Modifier.padding(bottom = 12.dp))
        return
    }
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(false) }
    var writeBack by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val codeText = stringResource(R.string.more_trakt_code)
    val done = stringResource(R.string.more_trakt_done)
    val failed = stringResource(R.string.more_trakt_failed)
    val needClient = stringResource(R.string.more_trakt_need_client)
    LaunchedEffect(provider, p.id, version) {
        signedIn = provider.isSignedIn()
        writeBack = provider.writeBackOn()
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        if (signedIn) {
            Text(stringResource(R.string.more_trakt_signed_in), color = Palette.Dim)
            Button(onClick = { scope.launch { provider.signOut(); status = null; version++ } }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.more_trakt_signout))
            }
        } else {
            Button(onClick = {
                if (busy) return@Button
                busy = true
                scope.launch {
                    status = if (provider.client() == null) needClient else {
                        val code = provider.startSignIn()
                        if (code == null) failed else {
                            status = codeText.format(code.verificationUrl, code.userCode)
                            if (waitForApproval(provider, code)) done else failed
                        }
                    }
                    busy = false
                    version++
                }
            }) { Text(stringResource(R.string.more_trakt_signin)) }
        }
        status?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        ListItem(
            selected = false,
            onClick = { scope.launch { provider.setWriteBack(!writeBack); version++ } },
            headlineContent = { Text(stringResource(R.string.more_trakt_writeback)) },
            supportingContent = { Text(stringResource(R.string.more_trakt_writeback_text)) },
            trailingContent = { Switch(checked = writeBack, onCheckedChange = null) },
        )
    }
}

/** Polls at the tracker's pace until approved, refused or expired. */
private suspend fun waitForApproval(provider: TrackerProvider, code: DeviceCode): Boolean {
    var interval = code.intervalSec * 1000L
    while (System.currentTimeMillis() < code.expiresAt) {
        delay(interval)
        when (provider.poll(code)) {
            is PollAnswer.Granted -> return true
            PollAnswer.Failed -> return false
            PollAnswer.SlowDown -> interval += SLOW_DOWN_MS
            PollAnswer.Pending -> Unit
        }
    }
    return false
}

private const val SLOW_DOWN_MS = 5_000L
