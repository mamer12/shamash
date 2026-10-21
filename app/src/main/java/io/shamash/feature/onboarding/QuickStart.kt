package io.shamash.feature.onboarding

import android.content.Context
import io.shamash.data.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One first-run preset (a .json file in assets/presets): the apps it assumes (by region, "*" for every region), the Home
 * layout preset, add-ons to turn on and apps to keep running. Everything it fills in stays editable. SPEC: PLT-17, PLT-18
 */
@Serializable
data class QuickStartPreset(
    val id: String,
    val packages: Map<String, List<String>> = emptyMap(),
    val layout: String? = null,
    val addons: List<String> = emptyList(),
    val keepRunning: List<String> = emptyList(),
) {
    /** The packages for [region]: its own list when the preset has one, else the "*" list. */
    fun packagesFor(region: String): List<String> = packages[region.uppercase()] ?: packages["*"].orEmpty()
}

object QuickStart {
    private val json = Json { ignoreUnknownKeys = true }
    private const val DIR = "presets"

    /** The shipped order of the cards. */
    val ORDER = listOf("netflix_youtube", "shahid_osn", "sports_first", "basics")

    /** One preset from its JSON; null (and a log line) when it is not valid, so a bad file hides one card only. */
    fun parse(text: String): QuickStartPreset? =
        try {
            json.decodeFromString(QuickStartPreset.serializer(), text)
        } catch (e: Exception) {
            Logger.w("quick start preset", e)
            null
        }

    /** The presets in assets, in [ORDER] (unknown ids last). Fails soft to an empty list. */
    fun load(context: Context): List<QuickStartPreset> =
        try {
            val files = context.assets.list(DIR).orEmpty().filter { it.endsWith(".json") }
            files.mapNotNull { name -> context.assets.open("$DIR/$name").use { parse(it.bufferedReader().readText()) } }
                .sortedBy { ORDER.indexOf(it.id).let { i -> if (i < 0) ORDER.size else i } }
        } catch (e: Exception) {
            Logger.w("quick start presets", e)
            emptyList()
        }

    /**
     * Applies [preset] over [answers]: only the apps in [installed] are ticked (an absent app is ignored silently),
     * the layout preset is picked, add-ons and keep-running are set. What the person already ticked is kept.
     */
    fun apply(preset: QuickStartPreset, region: String, installed: Set<String>, answers: Answers): Answers {
        val apps = preset.packagesFor(region).filter { it in installed }
        val keep = preset.keepRunning.filter { it in installed }
        return answers.copy(
            subscriptions = answers.subscriptions + apps,
            preset = preset.layout ?: answers.preset,
            addons = if (preset.addons.isEmpty()) answers.addons else (answers.addons ?: emptySet()) + preset.addons,
            keepRunning = if (keep.isEmpty()) answers.keepRunning else keep.toSet(),
        )
    }
}
