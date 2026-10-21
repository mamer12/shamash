package io.shamash.feature.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.Provider
import io.shamash.domain.RefreshableProvider
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.ui.Palette
import io.shamash.ui.ProviderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "From your phone" row (the queue, CMP-11): one card per queued link or title, hidden when empty, redrawn as
 * soon as the phone adds one. OK on a card shows Open now / Dismiss; opening goes through the same CMP-04 rules as
 * "Open now" on the phone and takes the entry off the queue. SPEC: CMP-11
 */
class QueueProvider(private val store: QueueStore, private val ownPackage: String) : Provider, RefreshableProvider, ProviderPage {
    override val id = QueueOps.PROVIDER
    override val changes: Flow<Unit> = store.changes.map { }
    override val entryUri = QueueOps.SCHEME
    override val titleResource = R.string.cmp_queue_row

    override fun isEnabled(profile: Profile) = profile.kind != ProfileKind.GUEST

    override suspend fun shelves(profile: Profile): List<Shelf> {
        val cards = store.list(profile.id).map { QueueOps.card(it, ownPackage) }
        return if (cards.isEmpty()) emptyList() else listOf(Shelf(ROW_ID, ROW_TITLE, ShelfStyle.WIDE, cards))
    }

    override suspend fun featured(profile: Profile): List<MediaItem> = emptyList()

    override suspend fun search(query: String, profile: Profile): List<MediaItem> = emptyList()

    override fun accepts(uri: String) = QueueOps.idOf(uri) != null

    override fun available(profile: Profile) = isEnabled(profile)

    @Composable
    override fun Content(uri: String, profile: Profile, onSettings: () -> Unit, onBack: () -> Unit) {
        val context = LocalContext.current
        val entry = remember(uri, profile.id) { store.list(profile.id).firstOrNull { it.id == QueueOps.idOf(uri) } }
        val first = remember { FocusRequester() }
        LaunchedEffect(entry) { runCatching { first.requestFocus() } }
        Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (entry == null) {
                Text(stringResource(R.string.cmp_queue_gone), color = Palette.Ink, fontSize = 24.sp)
                Button(onClick = onBack, modifier = Modifier.focusRequester(first)) { Text(stringResource(R.string.cmp_queue_back)) }
                return@Column
            }
            Text(entry.title, color = Palette.Ink, fontSize = 30.sp)
            Button(onClick = {
                store.remove(profile.id, entry.id)
                onBack()
                val actions = PhoneActions(ServiceLocator.get(context))
                background.launch { actions.handle(PhoneLink.classify(entry.text)) }
            }, modifier = Modifier.focusRequester(first)) { Text(stringResource(R.string.cmp_queue_open)) }
            OutlinedButton(onClick = { store.remove(profile.id, entry.id); onBack() }) {
                Text(stringResource(R.string.cmp_queue_dismiss))
            }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.cmp_queue_back)) }
        }
    }

    companion object {
        const val ROW_ID = "queue"
        const val ROW_TITLE = "cmp_queue_row"           // a string resource name, as shelf keys are
        private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** The registration line's provider for this TV. */
        fun of(context: android.content.Context) = QueueProvider(QueueStore.get(context), context.packageName)
    }
}
