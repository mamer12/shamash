package io.shamash.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.Palette
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

/**
 * The lock for a time limit or bedtime (PRF-14, PRF-15): says why, and only the owner's PIN (or, with no owner PIN,
 * a held OK) lifts it. Switching to another profile stays possible. Right-to-left safe: no fixed sides.
 */
@Composable
fun LockScreen(session: ProfileSession, why: LockState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val owner by session.profiles.collectAsState()
    val ownerHasPin = owner.firstOrNull()?.pinHash != null
    var message by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val text = when (why) {
        is LockState.Bedtime -> stringResource(
            R.string.lock_bedtime,
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date.from(why.endsAt.atZone(ZoneId.systemDefault()).toInstant())),
        )
        else -> stringResource(R.string.lock_daily)
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Dusk, Palette.Night))),
        contentAlignment = Alignment.Center,
    ) {
        if (asking && ownerHasPin) {
            PinPad(stringResource(R.string.lock_unlock), message, enabled = true, onCancel = { asking = false }, onComplete = { pin ->
                scope.launch { message = context.pinMessage(session.unlockWithOwnerPin(pin)) }
            })
        } else if (asking) {
            HoldToConfirm(stringResource(R.string.lock_unlock_hold), { scope.launch { session.unlockByHold() } })
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text, color = Palette.Ink, fontSize = 34.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { asking = true }) { Text(stringResource(R.string.lock_unlock)) }
                OutlinedButton(onClick = session::openPicker) { Text(stringResource(R.string.profile_switch)) }
            }
        }
    }
}
