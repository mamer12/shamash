package io.shamash.ui

import androidx.compose.runtime.Composable
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
    val Night = Color(0xFF0A0C1E)
    val Dusk = Color(0xFF14172E)
    val Card = Color(0xFF1B1F3A)
    val Gold = Color(0xFFF4C450)
    val Dawn = Color(0xFFFFE28C)
    val Ink = Color(0xFFF5F0E6)
    val Dim = Color(0xB3F5F0E6)
    val Faint = Color(0x66F5F0E6)
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
    val HeroLabel = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp, color = Palette.Gold)
    val HeroTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, color = Palette.Ink)
    val HeroMeta = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Palette.Dim)
    val HeroBody = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.Dim)
    val Button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val RowTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Ink)
    val CardTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.Ink)
    val CardSub = TextStyle(fontFamily = Sans, fontSize = 11.sp, color = Palette.Dim)
    val TileName = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
    val Clock = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Palette.Ink)
    val Small = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = Palette.Dim)
    val Tab = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Rank = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 104.sp, lineHeight = 104.sp)
    val Score = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.Ink)
    val HeroScore = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 56.sp, color = Palette.Ink)
}

@Composable
fun ShamashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.Night,
            surface = Palette.Dusk,
            onSurface = Palette.Ink,
            background = Palette.Night,
            onBackground = Palette.Ink,
        ),
        content = content,
    )
}
