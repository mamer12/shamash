package io.shamash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.shamash.app.ServiceLocator
import io.shamash.domain.Accents
import io.shamash.domain.Digits
import io.shamash.domain.Layout
import io.shamash.domain.Look
import io.shamash.domain.factor
import io.shamash.feature.accessibility.A11y
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Shamash's colors: night blue, and the Sun's gold for what's focused and important. */
object Palette {
    /** The user's accent (LAY-14); a state, so what reads [Gold] redraws when it changes. */
    private var accent by mutableStateOf(Color(Accents.GOLD))

    fun setAccent(argb: Int) { accent = Color(argb) }

    val Night = Color(0xFF0A0C1E)
    val Dusk = Color(0xFF14172E)
    val Card = Color(0xFF1B1F3A)
    val Gold: Color get() = accent
    val Dawn = Color(0xFFFFE28C)
    val Ink = Color(0xFFF5F0E6)
    /** Secondary text; solid ink in high contrast (PLT-12). */
    val Dim: Color get() = if (A11y.highContrast) Ink else Color(0xB3F5F0E6)
    val Faint: Color get() = if (A11y.highContrast) Color(0xCCF5F0E6) else Color(0x66F5F0E6)
    val Live = Color(0xFFFF453A)
    val MineCard = Color(0xFF1A2440)
}

/**
 * The screen in dp (a 1080p TV is 960 × 540 dp): margins, the billboard's text column, the apps row's place and the
 * card sizes per row style. Halves of the prototype's 1920-space numbers.
 */
object Dimens {
    val Margin = 48.dp
    val TopBarHeight = 76.dp
    val HeroTextWidth = 490.dp
    val AppsTop = 392.dp
    val RowTitleGap = 10.dp
    val RowGap = 18.dp
    val CardGap = 14.dp
    val Radius = 8.dp

    val WideW = 196.dp
    val WideH = 110.dp
    val PosterW = 108.dp
    val PosterH = 162.dp
    val RankW = 64.dp
    val AppW = 156.dp
    val AppH = 88.dp
    val TileW = 344.dp
    val TileH = 193.5.dp
    val LabelRoom = 26.dp
    val TileInfo = 118.dp
}

/** Type for a screen seen from three meters: large display, semibold for what's read at a glance. */
object Type {
    private val Sans = FontFamily.SansSerif

    val Greeting = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 21.sp, color = Palette.Ink)
    /** Read at draw time: the accent can change while Home is showing (LAY-14). */
    val HeroLabel get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp, color = Palette.Gold)
    val HeroTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, color = Palette.Ink)
    val HeroMeta get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Palette.Dim)
    val HeroBody get() = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.Dim)
    val Button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val RowTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Ink)
    val CardTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.Ink)
    val CardSub get() = TextStyle(fontFamily = Sans, fontSize = 11.sp, color = Palette.Dim)
    val TileName = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
    val Clock = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Palette.Ink)
    val Small get() = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = Palette.Dim)
    val Tab = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Rank = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 104.sp, lineHeight = 104.sp)
    val Score = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.Ink)
    val HeroScore = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 56.sp, color = Palette.Ink)
}

/** Row card sizes scaled by the profile's S / M / L choice (LAY-15). */
@Immutable
class CardDims(val scale: Float) {
    val wideW = Dimens.WideW * scale
    val wideH = Dimens.WideH * scale
    val posterW = Dimens.PosterW * scale
    val posterH = Dimens.PosterH * scale
    val rankW = Dimens.RankW * scale
    val appW = Dimens.AppW * scale
    val appH = Dimens.AppH * scale
    val tileW = Dimens.TileW * scale
    val tileH = Dimens.TileH * scale
    val labelRoom = Dimens.LabelRoom * scale
    val tileInfo = Dimens.TileInfo * scale
    val matchH = MatchCardHeight * scale
}

val LocalCardDims = compositionLocalOf { CardDims(1f) }

/** The profile's look (clock, date, weather, digits); the defaults follow the TV. */
val LocalLook = compositionLocalOf { Look() }

/** Provides the active profile's look: accent, card size and direction. SPEC: LAY-14, LAY-15, LAY-17 */
@Composable
fun ShamashTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember(context) { A11y.load(context) }
    val layout by remember(context) {
        ServiceLocator.get(context).prefs.profile.map { it.layout }.distinctUntilChanged()
    }.collectAsState(initial = Layout())
    val look = layout.look
    Palette.setAccent(look.accent)
    val rtl = Digits.mirrored(Locale.getDefault().language, look)
    val density = LocalDensity.current
    val scaled = remember(density, A11y.effective.textScale) {
        Density(density.density, A11y.settings.fontScale(density.fontScale))   // PLT-11
    }
    val direction = if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalLook provides look,
        LocalCardDims provides remember(layout.cardSize) { CardDims(layout.cardSize.factor) },
        LocalLayoutDirection provides direction,
        LocalDensity provides scaled,
    ) { Themed(look, content) }
}

@Composable
private fun Themed(look: Look, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Color(Accents.textOn(look.accent)),
            surface = Palette.Dusk,
            onSurface = Palette.Ink,
            background = Palette.Night,
            onBackground = Palette.Ink,
        ),
        content = content,
    )
}
