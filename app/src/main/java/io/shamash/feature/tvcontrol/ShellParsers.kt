package io.shamash.feature.tvcontrol

/** Reads what the TV's shell answers. Pure functions: everything here is unit-tested with recorded outputs. */
object ShellParsers {
    private val WIFI_OWNER = Regex("(?:cname|lname)=(\\S*)")
    private val OWNER_NAME = Regex("([A-Za-z0-9_.]+)(?::[0-9]+)?") // "android.uid.system:1000" → "android.uid.system"
    private val WIFI_DUMPED = Regex("(?m)^Wi-Fi is (enabled|disabled)\\r?$")
    private val ROLE_HOLDERS = Regex("[\\w.]+(;[\\w.]+)*")

    /** `pm list packages` lines ("package:com.x") → names. */
    fun packages(output: String): Set<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:") }
        .filter(ShellSafety::isPackage)
        .toSet()

    /**
     * Apps that created or last updated a saved Wi-Fi network, or null when the answer can't be trusted: no sign the
     * dump was read, or an owner that isn't a plain name. Then the switch stops (fail closed). SPEC: TVC-03
     */
    fun wifiOwners(output: String): Set<String>? {
        val raw = WIFI_OWNER.findAll(output).map { it.groupValues[1] }.toList()
        val owners = raw.map { OWNER_NAME.matchEntire(it)?.groupValues?.get(1)?.takeIf(ShellSafety::isPackage) }
        if (owners.any { it == null }) return null
        val read = owners.isNotEmpty() || WIFI_DUMPED.containsMatchIn(output)
        return if (read) owners.filterNotNull().toSet() else null
    }

    /**
     * The package holding HOME now, or null. Some Android 12 TVs have no `cmd role get-role-holders`: then the
     * activity HOME resolves to tells (the system's chooser, "android/…", means none is set).
     */
    fun homeHolder(roleOutput: String, resolveOutput: String): String? {
        val holder = roleOutput.trim()
        if (holder.isNotEmpty() && ROLE_HOLDERS.matches(holder)) return holder.substringBefore(';')
        val component = resolveOutput.trim().lineSequence().lastOrNull()?.trim().orEmpty()
        if (!ShellSafety.isComponent(component) || component.startsWith("android/")) return null
        return packageOf(component)
    }

    /** The TV's home screens (`query-activities --brief` lines that are components). */
    fun homeActivities(output: String): List<String> =
        output.lineSequence().map { it.trim() }.filter(ShellSafety::isComponent).toList()

    /** The home activity of [holder], unless it is Shamash itself (then nothing new to record). */
    fun currentHome(holder: String?, homes: List<String>): String? {
        if (holder == null || holder == TvPackages.SHAMASH) return null
        return homes.firstOrNull { packageOf(it) == holder }?.let(::realHome)
    }

    /**
     * Some TVs (TCL, Android 14) leave HOME with Google TV's recovery screen while launcherx answers the Home button:
     * that counts as launcherx, or Original would bring back the recovery screen.
     */
    fun realHome(component: String): String =
        if (packageOf(component) == TvPackages.GOOGLE_SETUP) TvPackages.GOOGLE_HOME else component

    fun setupIsHome(homes: List<String>): Boolean = homes.any { packageOf(it) == TvPackages.GOOGLE_SETUP }

    /** The screensaver setting: a valid component list, or "" when unset ("null"), or null when unreadable. */
    fun screensaver(output: String): String? {
        val value = output.trim()
        if (value.isEmpty() || value == "null") return ""
        return value.takeIf { v -> v.split(',').all(ShellSafety::isComponent) }
    }

    fun packageOf(component: String): String = component.substringBefore('/')

    fun disabled(output: String) = output.contains("new state: disabled-user")

    fun enabled(output: String) = output.contains("new state: enabled")

    fun success(output: String) = output.contains("Success")

    /**
     * The enabled accessibility services with Shamash's [service] added ([on]) or taken out, keeping everyone
     * else's (TalkBack…). Null when an entry looks unexpected: then the setting is left exactly as it is.
     */
    fun accessibilityServices(current: String, service: String, on: Boolean): List<String>? {
        val others = accessibilityServicesNow(current).filter { full(it) != full(service) }
        if (!others.all(ShellSafety::isComponent)) return null
        return if (on) others + service else others
    }

    fun hasAccessibilityService(current: String, service: String): Boolean =
        accessibilityServicesNow(current).any { full(it) == full(service) }

    /** "pkg/.Cls" → "pkg/pkg.Cls", so both spellings of a component compare equal. */
    private fun full(component: String): String {
        val pkg = packageOf(component)
        val cls = component.substringAfter('/', "")
        return if (cls.startsWith('.')) "$pkg/$pkg$cls" else component
    }

    /** The setting's entries as they are ("null" or empty: none). */
    fun accessibilityServicesNow(current: String): List<String> {
        val trimmed = current.trim()
        return if (trimmed.isEmpty() || trimmed == "null") emptyList() else trimmed.split(':').filter { it.isNotEmpty() }
    }
}
