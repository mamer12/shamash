package io.shamash.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.FocusCard
import io.shamash.ui.Palette
import kotlinx.coroutines.delay

private const val TICK_MS = 60_000L

/**
 * Puts the picker or the lock in front of the app when they apply, counts the minute while Shamash is in front, and
 * warns when 5 minutes are left. [content] shows when the gate is open. SPEC: PRF-04, PRF-14, PRF-15
 */
@Composable
fun ProfileGate(session: ProfileSession, onAddProfile: () -> Unit, content: @Composable () -> Unit) {
    val gate by session.gate.collectAsState()
    val warning by session.warning.collectAsState()
    val dismissable by session.pickerDismissable.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.recheck()
            while (true) { delay(TICK_MS); session.tick() }
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { session.touch(); false }) {
        when (val g = gate) {
            Gate.Booting -> Unit
            Gate.Picker -> ProfilePicker(session, dismissable, onAddProfile)
            is Gate.Locked -> LockScreen(session, g.why)
            Gate.Open -> content()
        }
        warning?.let { left ->
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).background(Palette.Card, RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.lock_warning, left), color = Palette.Dawn, fontSize = 16.sp)
            }
        }
    }
}

/** The active profile's avatar and name in the top bar; OK opens the picker (switching needs no setting). SPEC: PRF-04 */
@Composable
fun ProfileChip(session: ProfileSession) {
    val p by session.active.collectAsState()
    val profile = p ?: return
    FocusCard(session::openPicker, shape = RoundedCornerShape(50), corner = 20.dp, scale = 1.05f, description = profileLabel(profile)) { focused ->
        Row(
            Modifier.background(if (focused) Palette.Ink else Palette.Ink.copy(alpha = 0.12f)).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).background(Color(profile.avatarColor), CircleShape), contentAlignment = Alignment.Center) {
                Text(profileLabel(profile).take(1).uppercase(), color = Palette.Night, fontSize = 15.sp)
            }
            Text(profileLabel(profile), color = if (focused) Palette.Night else Palette.Ink, fontSize = 15.sp, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp))
        }
    }
}
