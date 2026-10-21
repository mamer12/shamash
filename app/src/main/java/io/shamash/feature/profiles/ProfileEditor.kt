package io.shamash.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.ContentLevel
import io.shamash.domain.KeyMode
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.ui.ChipPicker
import io.shamash.ui.OnePicker
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PinStep { NEW, CONFIRM }

/**
 * Add or edit one profile: name, colour, who it is for, what it may show, PIN, keys, daily time, bedtime and the
 * apps it may open. The owner keeps ADULT and cannot be deleted. Saved on Save; Back leaves without saving.
 * SPEC: PRF-03, PRF-05, PRF-08..11, PRF-13..16
 */
@Composable
internal fun ProfileEditor(locator: ServiceLocator, editing: Editing, onDone: () -> Unit) {
    val session = locator.session
    val scope = rememberCoroutineScope()
    val owner = session.profiles.value.firstOrNull()
    val isNew = editing.profile == null
    val isOwner = !isNew && editing.profile?.id == owner?.id
    var draft by remember { mutableStateOf(editing.profile ?: Drafts.create("", ProfileKind.ADULT, Drafts.COLOURS.first(), null)) }
    var limits by remember { mutableStateOf(session.limits.value.takeIf { !isNew && editing.profile?.id == session.active.value?.id } ?: ProfileLimits()) }
    var copyFrom by remember { mutableStateOf<Profile?>(null) }
    var pinStep by remember { mutableStateOf<PinStep?>(null) }
    var firstPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mismatch = stringResource(R.string.pin_mismatch)
    val installed = remember { locator.apps.installed() }

    LaunchLimits(locator, editing, isNew) { limits = it }
    BackHandler { if (pinStep != null) pinStep = null else onDone() }

    pinStep?.let { step ->
        PinPad(
            stringResource(if (step == PinStep.NEW) R.string.pin_new else R.string.pin_confirm), pinMessage, enabled = true,
            onCancel = { pinStep = null; pinMessage = null },
            onComplete = { pin ->
                if (step == PinStep.NEW) { firstPin = pin; pinMessage = null; pinStep = PinStep.CONFIRM }
                else if (pin != firstPin) { pinMessage = mismatch; pinStep = PinStep.NEW }
                else scope.launch {
                    val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
                    draft = draft.copy(pinHash = hash); pinStep = null; pinMessage = null
                }
            },
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextInput(stringResource(R.string.profile_name), draft.name, { draft = draft.copy(name = it) })
        Label(R.string.profile_colour)
        OnePicker(Drafts.COLOURS, draft.avatarColor, { draft = draft.copy(avatarColor = it) }) { colourName(it) }
        if (isNew) {
            Label(R.string.profile_copy_from)
            val all = session.profiles.value
            OnePicker(listOf<Profile?>(null) + all, copyFrom, { copyFrom = it }) {
                it?.name ?: stringResource(R.string.profile_copy_none)
            }
        }
        if (!isOwner) {
            Label(R.string.profile_kind)
            OnePicker(listOf(ProfileKind.ADULT, ProfileKind.KIDS), draft.kind, { k ->
                draft = draft.copy(kind = k, contentLevel = if (k == ProfileKind.KIDS) ContentLevel.KIDS else draft.contentLevel)
            }) { stringResource(if (it == ProfileKind.KIDS) R.string.kind_kids else R.string.kind_adult) }
        }
        Label(R.string.profile_level)
        OnePicker(ContentLevel.entries, draft.contentLevel, { draft = draft.copy(contentLevel = it) }) { levelLabel(it) }
        PinRow(draft, onSet = { pinStep = PinStep.NEW }, onRemove = { draft = draft.copy(pinHash = null) })
        if (!isOwner) {
            Label(R.string.profile_keys)
            OnePicker(KeyMode.entries, draft.keyMode, { draft = draft.copy(keyMode = it) }) {
                stringResource(if (it == KeyMode.SHARED) R.string.keys_shared else R.string.keys_own)
            }
        }
        LimitFields(limits, { limits = it })
        if (draft.kind == ProfileKind.KIDS || draft.contentLevel != ContentLevel.ALL) {
            Label(R.string.profile_apps)
            Text(stringResource(R.string.profile_apps_explain), color = Palette.Faint, fontSize = 15.sp)
            ChipPicker(installed.map { it.packageName }, limits.allowedApps.orEmpty(), { limits = limits.copy(allowedApps = it) }) { pkg ->
                installed.firstOrNull { it.packageName == pkg }?.label ?: pkg
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (!Drafts.isValidName(draft.name)) return@Button
                scope.launch { save(locator, draft.copy(name = draft.name.trim()), limits, isNew, copyFrom); onDone() }
            }) { Text(stringResource(R.string.profile_save)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.profile_cancel)) }
            if (!isOwner && !isNew) {
                OutlinedButton(onClick = {
                    if (!confirmDelete) confirmDelete = true
                    else scope.launch { delete(locator, draft.id); onDone() }
                }) {
                    Text(if (confirmDelete) stringResource(R.string.profile_delete_sure, draft.name) else stringResource(R.string.profile_delete))
                }
            }
        }
    }
}

/** Loads the stored limits of the profile being edited (the active one is read from the session already). */
@Composable
private fun LaunchLimits(locator: ServiceLocator, editing: Editing, isNew: Boolean, onLoaded: (ProfileLimits) -> Unit) {
    val id = editing.profile?.id
    androidx.compose.runtime.LaunchedEffect(id) {
        if (!isNew && id != null) onLoaded(locator.session.security.limits(id).first())
    }
}

@Composable
private fun Label(res: Int) = Text(stringResource(res), color = Palette.Dim, fontSize = 18.sp)

@Composable
private fun PinRow(p: Profile, onSet: () -> Unit, onRemove: () -> Unit) {
    Label(if (p.pinHash == null) R.string.pin_none else R.string.pin_has)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onSet) { Text(stringResource(if (p.pinHash == null) R.string.pin_set else R.string.pin_change)) }
        if (p.pinHash != null) OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.pin_remove)) }
    }
}

@Composable
private fun LimitFields(limits: ProfileLimits, onChange: (ProfileLimits) -> Unit) {
    Label(R.string.profile_limit_daily)
    OnePicker(Drafts.DAILY_CHOICES, limits.dailyMinutes, { onChange(limits.copy(dailyMinutes = it)) }) {
        if (it == 0) stringResource(R.string.profile_limit_none) else stringResource(R.string.profile_limit_minutes, it)
    }
    Label(R.string.profile_bedtime)
    val index = Drafts.bedtimeIndex(limits)
    OnePicker(listOf(-1) + Drafts.BEDTIMES.indices.toList(), index, { i ->
        onChange(limits.copy(bedtime = if (i < 0) emptyMap() else Drafts.BEDTIMES[i].let { (s, e) -> Drafts.everyDay(s, e) }))
    }) { i ->
        if (i < 0) stringResource(R.string.profile_bedtime_off)
        else Drafts.BEDTIMES[i].let { (s, e) -> stringResource(R.string.profile_bedtime_window, Drafts.clock(s), Drafts.clock(e)) }
    }
}

@Composable
private fun colourName(argb: Int): String = stringResource(
    when (Drafts.COLOURS.indexOf(argb)) {
        0 -> R.string.colour_gold
        1 -> R.string.colour_coral
        2 -> R.string.colour_sky
        3 -> R.string.colour_mint
        4 -> R.string.colour_violet
        else -> R.string.colour_rose
    },
)

private suspend fun save(locator: ServiceLocator, p: Profile, limits: ProfileLimits, isNew: Boolean, copyFrom: Profile?) {
    val store = locator.prefs.profiles
    val saved = if (isNew) {
        val base = Drafts.create(p.name, p.kind, p.avatarColor, copyFrom)
        store.add(base.copy(contentLevel = p.contentLevel, pinHash = p.pinHash, keyMode = p.keyMode))
    } else { store.save(p); p }
    saved?.let { locator.session.security.setLimits(it.id, limits) }
}

private suspend fun delete(locator: ServiceLocator, id: String) {
    if (locator.prefs.profiles.delete(id)) locator.session.security.forget(id)
}
