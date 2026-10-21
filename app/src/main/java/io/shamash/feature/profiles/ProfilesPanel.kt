package io.shamash.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.ProfileStore
import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.feature.settings.Explain
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/** What the panel is editing: an existing profile, or a new one. */
internal data class Editing(val profile: Profile?)

/**
 * Settings > Profiles (PRF-16): the owner's PIN first (when one is set), then the list with add, edit and delete, and
 * the TV-wide switches (ask on launch, Guest and its level). SPEC: PRF-03, PRF-04, PRF-08..11, PRF-16
 */
@Composable
fun ProfilesPanel(locator: ServiceLocator) {
    val session = locator.session
    val profiles by session.profiles.collectAsState()
    val ownerHasPin = profiles.firstOrNull()?.pinHash != null
    var verified by remember { mutableStateOf(session.ownerRecentlyVerified()) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
    editing?.let { e ->
        ProfileEditor(locator, e, onDone = { editing = null })
        return
    }
    if (!ownerHasPin) Explain(stringResource(R.string.pin_nudge))
    Explain(stringResource(R.string.profile_usage_note).takeIf { !ProfileSession.USAGE_MEASURED }.orEmpty())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        profiles.forEachIndexed { index, p ->
            ListItem(
                selected = false, onClick = { editing = Editing(p) },
                headlineContent = { Text(p.name.ifBlank { stringResource(R.string.profile_guest) }) },
                supportingContent = { Text(summary(p, index == 0)) },
                leadingContent = {
                    Box(Modifier.size(32.dp).background(Color(p.avatarColor), CircleShape), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1).uppercase(), color = Palette.Night, fontSize = 15.sp)
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (profiles.size < ProfileStore.MAX_PROFILES) {
            Button(onClick = { editing = Editing(null) }) { Text(stringResource(R.string.profile_add)) }
        } else Explain(stringResource(R.string.profile_full))
        Spacer(Modifier.height(20.dp))
        LaunchSwitches(locator)
    }
}

@Composable
private fun summary(p: Profile, owner: Boolean): String {
    val kind = stringResource(if (p.kind == ProfileKind.KIDS) R.string.kind_kids else R.string.kind_adult)
    val parts = listOfNotNull(
        if (owner) stringResource(R.string.profile_owner_tag) else null,
        kind,
        levelLabel(p.contentLevel),
        if (p.pinHash != null) stringResource(R.string.pin_has) else null,
    )
    return parts.joinToString(" · ")
}

@Composable
internal fun levelLabel(level: ContentLevel) = stringResource(
    when (level) {
        ContentLevel.ALL -> R.string.level_all
        ContentLevel.FAMILY -> R.string.level_family
        ContentLevel.KIDS -> R.string.level_kids
    },
)

@Composable
private fun LaunchSwitches(locator: ServiceLocator) {
    val security = locator.session.security
    val ask by security.askOnLaunch.collectAsState(initial = true)
    val guest by security.guestEnabled.collectAsState(initial = false)
    val level by security.guestLevel.collectAsState(initial = ContentLevel.FAMILY)
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.profile_ask_launch), color = Palette.Dim, fontSize = 18.sp)
    OnePicker(listOf(true, false), ask, { scope.launch { security.setAskOnLaunch(it) } }) { stringResource(onOff(it)) }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.profile_guest_on), color = Palette.Dim, fontSize = 18.sp)
    OnePicker(listOf(true, false), guest, { scope.launch { security.setGuestEnabled(it) } }) { stringResource(onOff(it)) }
    if (guest) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.profile_guest_level), color = Palette.Dim, fontSize = 18.sp)
        OnePicker(listOf(ContentLevel.FAMILY, ContentLevel.KIDS), level, { scope.launch { security.setGuestLevel(it) } }) { levelLabel(it) }
    }
}

private fun onOff(on: Boolean) = if (on) R.string.profile_on else R.string.profile_off
