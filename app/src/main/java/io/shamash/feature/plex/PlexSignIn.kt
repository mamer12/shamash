package io.shamash.feature.plex

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.ServerHttp
import io.shamash.data.Sources
import io.shamash.data.plex.PlexAuth
import io.shamash.data.plex.PlexProvider
import io.shamash.domain.AddOn
import io.shamash.domain.Profile
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/**
 * "Sign in with a code": Plex shows a code, the user types it at plex.tv/link on a phone, and the first server of the
 * account is filled in (address and its token). The typed fields below remain for a direct address. SPEC: PEX-10
 */
@Composable
internal fun PlexSignIn(addOn: AddOn, p: Profile, locator: ServiceLocator) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.pex_plex_failed)
    val done = stringResource(R.string.pex_plex_done)
    val waiting = stringResource(R.string.pex_plex_code)
    Column(Modifier.padding(bottom = 12.dp)) {
        Button(onClick = {
            if (busy) return@Button
            busy = true
            scope.launch {
                status = try {
                    val auth = PlexAuth(ServerHttp(locator.http), "shamash-${p.id.ifEmpty { "tv" }}")
                    val pin = auth.start()
                    status = waiting.format(pin.code)
                    val account = auth.poll(pin)
                    val server = account?.let { auth.servers(it).firstOrNull() }
                    if (server == null) failed else {
                        val credentials = Sources.credentials(locator.prefs, addOn)
                        credentials.set("url", server.uri)
                        credentials.set("token", server.accessToken)
                        done.format(server.name)
                    }
                } catch (e: Exception) {
                    failed   // not shown: the error may carry an address
                }
                busy = false
            }
        }) { Text(stringResource(R.string.pex_plex_signin)) }
        status?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}

/** What Settings shows for Plex above the typed fields. */
@Composable
internal fun PlexExtra(p: Profile, locator: ServiceLocator) {
    PlexSignIn(Sources.addOnRegistry.find(PlexProvider.ID) ?: return, p, locator)
}
