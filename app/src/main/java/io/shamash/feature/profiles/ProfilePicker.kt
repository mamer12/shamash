package io.shamash.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.ui.FocusCard
import io.shamash.ui.Palette
import kotlinx.coroutines.launch

/** A profile entering: the requirements still to pass, in order. */
private data class Entering(val target: Profile, val steps: List<Requirement>)

/** The name to show: Guest is localized, everyone else is as typed. */
@Composable
internal fun profileLabel(p: Profile): String = if (p.kind == ProfileKind.GUEST) stringResource(R.string.profile_guest) else p.name

/**
 * "Who is watching?": one avatar per profile, Guest when offered, and Add profile for those who can reach Settings.
 * Picking one runs the steps it needs (its PIN, the owner's PIN or a held OK when leaving Kids) and opens it.
 * SPEC: PRF-04, PRF-05, PRF-10
 */
@Composable
fun ProfilePicker(session: ProfileSession, dismissable: Boolean, onAddProfile: () -> Unit) {
    val profiles by session.profiles.collectAsState()
    val guest by session.guestEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf<Entering?>(null) }
    val current = entering
    BackHandler(enabled = current != null || dismissable) {
        if (current != null) entering = null else scope.launch { session.closePicker() }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Dusk, Palette.Night))),
        contentAlignment = Alignment.Center,
    ) {
        if (current == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.profile_picker_title), color = Palette.Ink, fontSize = 36.sp)
                Spacer(Modifier.height(40.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                    profiles.forEach { p ->
                        Avatar(profileLabel(p), Color(p.avatarColor), p.pinHash != null) {
                            scope.launch {
                                val steps = session.requirements(p)
                                if (steps.isEmpty()) session.switchTo(p.id) else entering = Entering(p, steps)
                            }
                        }
                    }
                    if (guest) Avatar(stringResource(R.string.profile_guest), Palette.Faint, false) { scope.launch { session.startGuest() } }
                    if (session.restrictions.settings && profiles.size < ProfileStoreLimit.MAX) {
                        Avatar(stringResource(R.string.profile_add), Palette.Card, false, plus = true) {
                            scope.launch { session.closePicker() }
                            onAddProfile()
                        }
                    }
                }
            }
        } else {
            StepScreen(session, current, onBack = { entering = null }, onStepDone = {
                val rest = current.steps.drop(1)
                if (rest.isEmpty()) scope.launch { session.switchTo(current.target.id) } else entering = current.copy(steps = rest)
            })
        }
    }
}

internal object ProfileStoreLimit { const val MAX = io.shamash.data.ProfileStore.MAX_PROFILES }

@Composable
private fun StepScreen(session: ProfileSession, e: Entering, onBack: () -> Unit, onStepDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    when (e.steps.first()) {
        Requirement.HOLD_OK -> HoldToConfirm(stringResource(R.string.hold_ok_leave_kids), onStepDone)
        Requirement.NONE -> onStepDone()
        Requirement.TARGET_PIN, Requirement.OWNER_PIN -> {
            val owner = e.steps.first() == Requirement.OWNER_PIN
            var message by remember(e) { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val title = if (owner) stringResource(R.string.pin_owner) else stringResource(R.string.pin_for, profileLabel(e.target))
            PinPad(title, message, enabled = true, onCancel = onBack, onComplete = { pin ->
                scope.launch {
                    val id = if (owner) session.ownerProfile()?.id.orEmpty() else e.target.id
                    val result = session.checkPin(id, pin)
                    message = context.pinMessage(result)
                    if (result == PinCheck.Ok) onStepDone()
                }
            })
        }
    }
}

@Composable
private fun Avatar(label: String, colour: Color, locked: Boolean, plus: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(onClick, shape = CircleShape, corner = 60.dp) {
            Box(Modifier.size(120.dp).background(colour), contentAlignment = Alignment.Center) {
                Text(if (plus) "+" else label.take(1).uppercase(), color = Palette.Night, fontSize = 52.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(label + if (locked) "  •" else "", color = Palette.Ink, fontSize = 20.sp, maxLines = 1)
    }
}
