package io.shamash.feature.tvcontrol

/** A package or component name that isn't safe to put in a shell command. */
class UnsafeInputException(value: String) : IllegalArgumentException("refused shell argument: $value")

/** Strict checks for anything that goes into a shell command (names only: letters, digits, '_', '.', '$'). */
object ShellSafety {
    private val PACKAGE = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    private val COMPONENT = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*/\\.?[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*")
    private const val MAX_LENGTH = 255

    fun isPackage(value: String) = value.length <= MAX_LENGTH && PACKAGE.matches(value)

    fun isComponent(value: String) = value.length <= MAX_LENGTH && COMPONENT.matches(value)

    fun pkg(value: String): String = if (isPackage(value)) value else throw UnsafeInputException(value)

    fun component(value: String): String = if (isComponent(value)) value else throw UnsafeInputException(value)

    /** [value], unless it is on the protected list: those are never turned off nor stripped of HOME. SPEC: TVC-04 */
    fun unprotected(value: String): String =
        if (value in BrandProfiles.PROTECTED) throw UnsafeInputException(value) else value

    /** In single quotes, so the shell takes it as is ('$' in an inner class name included; names never hold '). */
    fun quoted(value: String): String = "'$value'"
}

/**
 * Every shell command TV control runs, built only from validated names. Never `pm uninstall`, never `pm clear`:
 * apps are only turned off for user 0 and back on. SPEC: TVC-02, TVC-05
 */
object Commands {
    const val LIST_PACKAGES = "pm list packages"
    const val LIST_DISABLED = "pm list packages -d"

    /**
     * Creator and last updater of each saved network (TVC-03); grep keeps a long dump off the wire. The "Wi-Fi is …"
     * line proves the dump was read, so an empty or failed answer is not taken for "none".
     */
    const val WIFI_OWNERS =
        "dumpsys wifi 2>&1 | grep -oE '(cname|lname)=[^ ]*|Wi-Fi is [a-z]+|Can.t find service: wifi'"

    const val HOME_ROLE_HOLDERS = "cmd role get-role-holders --user 0 android.app.role.HOME"
    const val RESOLVE_HOME =
        "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"
    const val QUERY_HOMES =
        "cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"

    const val GET_SCREENSAVER = "settings get secure screensaver_components"
    const val DELETE_SCREENSAVER = "settings delete secure screensaver_components"

    const val GET_ACCESSIBILITY_SERVICES = "settings get secure enabled_accessibility_services"
    const val DELETE_ACCESSIBILITY_SERVICES = "settings delete secure enabled_accessibility_services"
    const val GET_ACCESSIBILITY_ENABLED = "settings get secure accessibility_enabled"

    /** The app-ops the switch grants (and Original takes back). */
    val APP_OPS = listOf("GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW")

    /** Refuses protected packages (TVC-04), whatever list asked for it. */
    fun disable(pkg: String) = "pm disable-user --user 0 ${ShellSafety.pkg(ShellSafety.unprotected(pkg))}"

    fun enable(pkg: String) = "pm enable ${ShellSafety.pkg(pkg)}"

    fun setHome(component: String) = "cmd package set-home-activity ${ShellSafety.quoted(ShellSafety.component(component))}"

    fun addHomeRole(pkg: String) = "cmd role add-role-holder --user 0 android.app.role.HOME ${ShellSafety.pkg(pkg)}"

    /** Only Shamash gives up HOME this way; a protected package is never stripped of it (TVC-04). */
    fun removeHomeRole(pkg: String): String {
        val allowed = if (pkg == TvPackages.SHAMASH) pkg else ShellSafety.unprotected(pkg)
        return "cmd role remove-role-holder --user 0 android.app.role.HOME ${ShellSafety.pkg(allowed)}"
    }

    /** One or more screensaver components, comma-separated (as the setting stores them). */
    fun putScreensaver(components: String): String {
        components.split(',').forEach { ShellSafety.component(it) }
        return "settings put secure screensaver_components ${ShellSafety.quoted(components)}"
    }

    fun appOp(pkg: String, op: String, allow: Boolean): String {
        require(op in APP_OPS) { "unknown app-op $op" }
        return "appops set ${ShellSafety.pkg(pkg)} $op ${if (allow) "allow" else "default"}"
    }

    fun putAccessibilityServices(services: List<String>): String {
        services.forEach { ShellSafety.component(it) }
        return "settings put secure enabled_accessibility_services ${ShellSafety.quoted(services.joinToString(":"))}"
    }

    fun accessibilityEnabled(on: Boolean) = "settings put secure accessibility_enabled ${if (on) 1 else 0}"
}
