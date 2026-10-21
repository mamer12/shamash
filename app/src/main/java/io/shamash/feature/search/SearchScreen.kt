package io.shamash.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.ui.Dimens
import io.shamash.ui.Palette
import io.shamash.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FIELD_SHAPE = RoundedCornerShape(20.dp)
private val FIELD_WIDTH = 340.dp
private val FIELD_HEIGHT = 40.dp
private val FIELD_TEXT = Type.RowTitle.copy(fontSize = 17.sp)
private const val FOCUS_ATTEMPTS = 10

/** One search across providers. SPEC: SRCH-01..05 */
@Composable
fun SearchScreen(locator: ServiceLocator, initialQuery: String?, onBack: () -> Unit) =
    io.shamash.feature.personalization.WithItemMenu(locator) { SearchBody(locator, initialQuery, onBack) }

@Composable
private fun SearchBody(locator: ServiceLocator, initialQuery: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { SearchModel(locator.providers, locator.prefs::current, scope) { locator.personal.state.value.data.muteIndex } }
    val state by model.state.collectAsState()
    val appNames by produceState(emptyMap<String, String>()) {
        value = withContext(Dispatchers.IO) { locator.apps.installed().associate { it.packageName to it.label } }
    }
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val voice = rememberVoiceLauncher { heard -> model.submit(heard) }   // PLT-08

    BackHandler(onBack = onBack)
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNullOrBlank()) focusWhenComposed(field) else model.submit(initialQuery)
    }
    LaunchedEffect(state.focusResults, state.rows.isNotEmpty()) {
        if (state.focusResults && state.rows.isNotEmpty()) {
            keyboard?.hide()
            focusWhenComposed(firstResult)
            model.focusTaken()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Night)
            .padding(start = Dimens.Margin, top = Dimens.Margin / 2)
            .onPreviewKeyEvent { e ->   // the remote's voice key
                val hit = e.type == KeyEventType.KeyDown && VoiceQuery.isVoiceKey(e.nativeKeyEvent.keyCode)
                if (hit) voice.start()
                hit
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = state.query,
                onType = model::type,
                onEnter = { keyboard?.hide(); model.submit() },
                modifier = Modifier.focusRequester(field),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = voice::start) { Text(stringResource(R.string.voice_button)) }
        }
        StatusLine(state)
        LazyColumn(contentPadding = PaddingValues(bottom = Dimens.Margin)) {
            itemsIndexed(state.rows, key = { _, row -> row.key }) { index, row ->
                ResultRow(
                    title = row.appPackage?.let(appNames::get) ?: providerName(row.providerId),
                    row = row,
                    firstCardFocus = firstResult.takeIf { index == 0 },
                    onOpen = { item -> scope.launch { locator.opener.open(item) } },
                )
            }
        }
    }
}

/** A target exists only once it is laid out (the first card arrives with its row): try for a few frames. */
private suspend fun focusWhenComposed(target: FocusRequester) {
    repeat(FOCUS_ATTEMPTS) {
        withFrameNanos { }
        if (runCatching { target.requestFocus() }.isSuccess) return
    }
}

@Composable
private fun SearchField(query: String, onType: (String) -> Unit, onEnter: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = query,
        onValueChange = onType,
        modifier = modifier.width(FIELD_WIDTH).height(FIELD_HEIGHT),
        singleLine = true,
        textStyle = FIELD_TEXT,
        cursorBrush = SolidColor(Palette.Gold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onEnter() }, onDone = { onEnter() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier.background(Palette.Dusk, FIELD_SHAPE)
                    .border(2.dp, if (focused) Palette.Ink else Palette.Dusk, FIELD_SHAPE)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_hint), style = FIELD_TEXT, color = Palette.Faint)
                }
                inner()
            }
        },
    )
}

@Composable
private fun StatusLine(state: SearchUiState) {
    val text = when {
        state.searching && state.rows.isEmpty() -> stringResource(R.string.search_searching)
        state.searched && state.rows.isEmpty() -> stringResource(R.string.search_nothing, state.query.trim())
        state.query.isBlank() -> stringResource(R.string.search_start)
        else -> ""
    }
    Text(text, style = Type.HeroMeta, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
private fun providerName(providerId: String): String = when (providerId) {
    "tmdb" -> stringResource(R.string.provider_tmdb)
    "youtube" -> stringResource(R.string.provider_youtube)
    "cinemana" -> stringResource(R.string.provider_cinemana)
    "tod" -> stringResource(R.string.provider_tod)
    "espn" -> stringResource(R.string.provider_espn)
    else -> providerId
}
