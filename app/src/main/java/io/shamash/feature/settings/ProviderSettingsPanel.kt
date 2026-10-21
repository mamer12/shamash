package io.shamash.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import io.shamash.data.Sources
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Profile
import io.shamash.domain.RowSource
import io.shamash.domain.RowSourceProvider
import io.shamash.domain.RowSources
import io.shamash.domain.TestResult
import io.shamash.domain.TestableProvider
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.launch

/** Kinds whose value is never shown once entered (masked while typing, blank afterwards). SPEC: PEX-04, PEX-16 */
internal fun CredentialField.isMasked() = kind != CredentialKind.TEXT

/**
 * The one settings screen every add-on shares: credential fields from `AddOn.requires`, "Test connection" when the
 * provider can test itself, the provider's row sources, and Remove. [extra] holds anything specific to the add-on.
 * Shown while the add-on is on. SPEC: PEX-05, PEX-06
 */
@Composable
internal fun ProviderSettingsPanel(
    addOn: AddOn,
    p: Profile,
    save: (Profile) -> Unit,
    locator: ServiceLocator,
    extra: @Composable () -> Unit = {},
) {
    val provider = remember(addOn.id, locator) { locator.providers.firstOrNull { it.id == addOn.id } }
    val scope = rememberCoroutineScope()
    val credentials = remember(addOn.id, p.id) { Sources.credentials(locator.prefs, addOn) }
    var version by remember { mutableStateOf(0) }
    Column(Modifier.padding(start = 24.dp, top = 8.dp)) {
        extra()
        if (addOn.requires.isNotEmpty()) {
            val saved by produceState(emptySet<String>(), credentials, version) {
                value = addOn.requires.filter { credentials.get(it.key) != null }.map { it.key }.toSet()
            }
            val drafts = remember(addOn.id, p.id) { mutableStateOf(emptyMap<String, String>()) }
            addOn.requires.forEach { field ->
                val shownSaved = field.key in saved
                val current by produceState("", credentials, version) {
                    value = if (field.isMasked()) "" else credentials.get(field.key).orEmpty()
                }
                TextInput(
                    label = stringResource(field.labelRes) + if (field.required) " *" else "",
                    value = drafts.value[field.key] ?: current,
                    onChange = { drafts.value = drafts.value + (field.key to it) },
                    secret = field.isMasked(),
                    hint = field.hintRes?.let { stringResource(it) }
                        ?: if (shownSaved && field.isMasked()) stringResource(R.string.addon_credential_saved) else null,
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = {
                scope.launch {
                    drafts.value.forEach { (k, v) -> credentials.set(k, v) }
                    drafts.value = emptyMap()
                    version++
                }
            }) { Text(stringResource(R.string.addon_save)) }
            Spacer(Modifier.height(12.dp))
        }
        if (provider is TestableProvider) TestConnection(provider, credentials::isComplete)
        (provider as? RowSourceProvider)?.let { RowSourceList(addOn.id, it.rowSources(), p, save) }
        Spacer(Modifier.height(12.dp))
        RemoveButton(addOn, p, save, locator)
    }
}

@Composable
private fun TestConnection(provider: TestableProvider, complete: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val missing = stringResource(R.string.addon_test_missing)
    val failed = stringResource(R.string.addon_test_failed)
    val busy = stringResource(R.string.addon_testing)
    Button(onClick = {
        if (testing) return@Button
        testing = true
        result = busy
        scope.launch {
            result = if (!complete()) missing else try {
                provider.test().message
            } catch (e: Exception) {
                failed   // the exception is not shown: it may carry an address with credentials
            }
            testing = false
        }
    }) { Text(stringResource(R.string.addon_test)) }
    result?.let { Text(it, color = Palette.Dim, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(12.dp))
}

/** The rows a provider can offer, each on/off with an optional name of the user's own. SPEC: PEX-06, LAY-02 */
@Composable
private fun RowSourceList(providerId: String, sources: List<RowSource>, p: Profile, save: (Profile) -> Unit) {
    val capped = RowSources.capped(sources)
    if (capped.isEmpty()) return
    Text(stringResource(R.string.addon_rows), color = Palette.Dim)
    capped.forEach { s ->
        val on = RowSources.isOn(p, providerId, s)
        val title = s.titleRes?.let { stringResource(it) } ?: s.titleText.orEmpty()
        ListItem(
            selected = false, onClick = { save(RowSources.withOn(p, providerId, s, !on)) },
            headlineContent = { Text(title) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
        )
        if (on) {
            TextInput(
                label = stringResource(R.string.addon_row_rename),
                value = RowSources.customTitle(p, providerId, s).orEmpty(),
                onChange = { save(RowSources.withTitle(p, providerId, s, it)) },
                width = 480.dp,
            )
        }
    }
}
