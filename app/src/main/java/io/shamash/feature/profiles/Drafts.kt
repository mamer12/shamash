package io.shamash.feature.profiles

import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind

/** Pure rules for making and editing a profile in Settings. */
object Drafts {
    /** Avatar colours (ARGB) to pick from. */
    val COLOURS = listOf(0xFFF4C450, 0xFFFF7A6B, 0xFF6BB8FF, 0xFF7ADBA8, 0xFFB38CFF, 0xFFFF8FB8).map { it.toInt() }

    val DAILY_CHOICES = listOf(0, 30, 60, 90, 120, 180)

    /** Bedtime presets as (start, end) minutes of the day; applied to every day. */
    val BEDTIMES = listOf(20 * 60 + 30 to 6 * 60 + 30, 21 * 60 to 6 * 60 + 30, 22 * 60 to 7 * 60, 23 * 60 to 7 * 60)

    /**
     * A new profile. KIDS starts at the KIDS level; [copyFrom] brings the setup answers (place, services, teams,
     * genres, layout) so the person skips setup; without it the profile runs setup on first use. SPEC: PRF-08, PRF-10
     */
    fun create(name: String, kind: ProfileKind, colour: Int, copyFrom: Profile?): Profile {
        val base = Profile(
            kind = kind,
            avatarColor = colour,
            name = name.trim(),
            contentLevel = if (kind == ProfileKind.KIDS) ContentLevel.KIDS else ContentLevel.ALL,
        )
        val from = copyFrom ?: return base
        return base.copy(
            onboarded = true,
            city = from.city, latitude = from.latitude, longitude = from.longitude, region = from.region,
            subscriptions = from.subscriptions, teams = from.teams, competitions = from.competitions,
            genres = from.genres, follows = from.follows, layout = from.layout, addons = from.addons,
        )
    }

    fun everyDay(startMin: Int, endMin: Int): Map<Int, Window> = (1..7).associateWith { Window(startMin, endMin) }

    /** The bedtime preset index the limits match, or -1 (off). */
    fun bedtimeIndex(limits: ProfileLimits): Int {
        val w = limits.bedtime[1] ?: return -1
        return BEDTIMES.indexOfFirst { it.first == w.startMin && it.second == w.endMin }
    }

    fun clock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** A name is required; a blank one keeps the old or falls back. */
    fun isValidName(name: String) = name.trim().isNotEmpty()
}
