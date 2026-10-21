package io.shamash.feature.tvcontrol

/** What the TV has, as far as planning the switch needs. */
data class SwitchInput(
    val brand: Brand,
    val installed: Set<String>,
    val wifiOwners: Set<String>,
    val originalHome: String?,
    val originalScreensaver: String?,
    val setupIsHome: Boolean,
)

/**
 * [turnOff]: what the switch turns off, in order. [keptOn]: listed apps left on because they own a saved Wi-Fi
 * network. [blockedBy]: apps the switch can't work without turning off but that own a saved network: then nothing
 * changes and the user is asked to forget and re-join the network first.
 */
data class SwitchPlan(val turnOff: List<String>, val keptOn: List<String>, val blockedBy: List<String>)

/** Decides which apps go off and which come back. Pure: no shell, no Android. SPEC: TVC-02, TVC-03, TVC-04 */
object SwitchPlanner {

    fun plan(input: SwitchInput): SwitchPlan {
        val required = eligible(essential(input.originalHome, input.setupIsHome), input.installed)
        val optional = eligible(
            listOfNotNull(screensaverPackage(input.originalScreensaver, input.originalHome)) + BrandProfiles.promo(input.brand),
            input.installed,
        ) - required.toSet()
        val blockedBy = required.filter { it in input.wifiOwners }
        val keptOn = optional.filter { it in input.wifiOwners }
        if (blockedBy.isNotEmpty()) return SwitchPlan(emptyList(), keptOn, blockedBy)
        return SwitchPlan((required + optional).filter { it !in input.wifiOwners }, keptOn, emptyList())
    }

    /**
     * What Original turns back on: everything the switch recorded, plus the TV's own home screen, its screensaver and
     * Google TV's setup (in case a record was lost), limited to what is off now.
     */
    fun restoreTargets(
        recorded: Collection<String>,
        originalHome: String?,
        originalScreensaver: String?,
        disabledNow: Set<String>,
    ): List<String> {
        val system = essential(originalHome, setupIsHome = true) +
            listOfNotNull(screensaverPackage(originalScreensaver, originalHome))
        return (recorded + system).distinct()
            .filter { it in disabledNow && it != TvPackages.SHAMASH && ShellSafety.isPackage(it) }
    }

    /** The TV's own home screen (and what must go with it): the switch doesn't hold without turning these off. */
    private fun essential(home: String?, setupIsHome: Boolean): List<String> {
        val homePackage = home?.takeIf { it.isNotEmpty() }?.let(ShellParsers::packageOf)
        return buildList {
            if (homePackage != null && homePackage != TvPackages.SHAMASH) add(homePackage)
            if (homePackage == TvPackages.TV_LAUNCHER) add(TvPackages.TV_RECOMMENDATIONS)
            if (setupIsHome) add(TvPackages.GOOGLE_SETUP)
        }
    }

    /** The TV's own screensaver package, when it is a separate app (not Shamash, not the home screen). */
    private fun screensaverPackage(screensaver: String?, home: String?): String? {
        val first = screensaver?.split(',')?.firstOrNull()?.takeIf { it.contains('/') } ?: return null
        val pkg = ShellParsers.packageOf(first)
        val homePackage = home?.let(ShellParsers::packageOf)
        return pkg.takeIf { it != TvPackages.SHAMASH && it != homePackage }
    }

    /** Installed, never protected (TVC-04), never Shamash, a valid name; each once. */
    private fun eligible(packages: List<String>, installed: Set<String>): List<String> = packages.distinct().filter {
        it in installed && it !in BrandProfiles.PROTECTED && ShellSafety.isPackage(it)
    }
}
