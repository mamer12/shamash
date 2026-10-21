package io.shamash.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.shamash.app.ServiceLocator
import io.shamash.data.softly
import io.shamash.domain.TvApp
import io.shamash.domain.Profile
import io.shamash.feature.companion.Companion
import io.shamash.feature.tvcontrol.RemoteButton
import io.shamash.feature.tvcontrol.TvControl
import io.shamash.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Sign in with a code shown on the TV, entered on the phone. SPEC: PRV-06 */
@Composable
internal fun YouTubePanel(p: Profile, locator: ServiceLocator) {
    if (!p.hasYoutubeClient) {
        Explain(stringResource(R.string.settings_youtube_no_client))
        return
    }
    val scope = rememberCoroutineScope()
    val signIn = remember { YouTubeSignIn(locator.youtubeAuth.asDeviceSignIn(), scope) }
    LaunchedEffect(signIn) { signIn.refresh() }
    when (val s = signIn.state.collectAsState().value) {
        SignInState.SignedIn -> {
            Explain(stringResource(R.string.settings_youtube_in))
            OutlinedButton(onClick = signIn::signOut) { Text(stringResource(R.string.settings_sign_out)) }
        }
        is SignInState.Waiting -> {
            Explain(stringResource(R.string.settings_youtube_code, s.code.verificationUrl))
            Text(s.code.userCode, color = Palette.Gold, fontSize = 56.sp, letterSpacing = 6.sp)
        }
        SignInState.SignedOut, SignInState.Failed -> {
            Explain(stringResource(if (s == SignInState.Failed) R.string.settings_youtube_failed else R.string.settings_youtube_out))
            Button(onClick = signIn::signIn) { Text(stringResource(R.string.settings_sign_in)) }
        }
    }
}

/** The phone page's address and PIN. SPEC: CMP-01, CMP-03 */
@Composable
internal fun PhonePanel(locator: ServiceLocator) {
    val url = remember { Companion.url() }
    val pin by produceState("", locator) {
        locator.prefs.profile.collect { value = Companion.pin(locator.prefs) }
    }
    if (url == null) {
        Explain(stringResource(R.string.settings_phone_offline))
        return
    }
    Explain(stringResource(R.string.settings_phone_text))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_phone_address), color = Palette.Dim, fontSize = 18.sp)
        Text(url, color = Palette.Ink, fontSize = 36.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_phone_pin), color = Palette.Dim, fontSize = 18.sp)
        Text(pin, color = Palette.Gold, fontSize = 48.sp, letterSpacing = 8.sp)
    }
}

/**
 * What each remote app button opens: OK steps through its own app, nothing, then the streaming apps installed.
 * SPEC: TVC-07
 */
@Composable
internal fun ButtonsPanel(locator: ServiceLocator) {
    val tv = remember { TvControl.get(locator.appContext) }
    val scope = rememberCoroutineScope()
    val apps by produceState(emptyList<TvApp>(), locator) {
        value = withContext(Dispatchers.IO) { softly("settings apps", emptyList()) { locator.apps.installed() } }
    }
    var mapping by remember { mutableStateOf<Map<RemoteButton, String>>(emptyMap()) }
    LaunchedEffect(Unit) { mapping = tv.remoteButtons() }
    Explain(stringResource(R.string.settings_buttons_text))
    val own = stringResource(R.string.button_own)
    val nothing = stringResource(R.string.button_nothing)
    RemoteButton.entries.forEach { button ->
        val current = mapping[button] ?: button.defaultApp
        val choices = ButtonChoices.of(button, apps.filter { it.streaming }.map { it.packageName })
        val label = when {
            current == button.defaultApp && current.isNotEmpty() -> own
            current.isEmpty() -> nothing
            else -> apps.firstOrNull { it.packageName == current }?.label ?: current
        }
        ListItem(
            selected = false,
            onClick = {
                scope.launch {
                    tv.setRemoteButton(button, ButtonChoices.next(choices, current, button.defaultApp))
                    mapping = tv.remoteButtons()
                }
            },
            headlineContent = { Text(stringResource(buttonName(button))) },
            trailingContent = { Text(label, color = Palette.Gold) },
        )
    }
}

private fun buttonName(button: RemoteButton) = when (button) {
    RemoteButton.NETFLIX -> R.string.button_netflix
    RemoteButton.YOUTUBE -> R.string.button_youtube
    RemoteButton.PRIME -> R.string.button_prime
    RemoteButton.TCL_CHANNEL -> R.string.button_channel
}

/** The order OK steps through for one button. */
object ButtonChoices {
    /** The button's own app (when it has one), nothing, then [streaming] apps, each once. */
    fun of(button: RemoteButton, streaming: List<String>): List<String> =
        (listOf(button.defaultApp, "") + streaming).distinct()

    /** What to store after [current]: null means "its own app" (the default), "" nothing, else a package. */
    fun next(choices: List<String>, current: String, default: String): String? {
        val i = choices.indexOf(current)
        val pick = choices[(i + 1).mod(choices.size)]
        return if (pick == default) null else pick
    }
}
