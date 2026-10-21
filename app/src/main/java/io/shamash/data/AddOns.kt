package io.shamash.data

import io.shamash.domain.AddOn
import io.shamash.domain.Deps
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.ProviderCredentials

/**
 * The add-ons the app ships, one registration line each in [Sources.addOns]; which are on comes from
 * `Profile.addons`. Removing an add-on means deleting its folders and its line. SPEC: PEX-01, PEX-02, PEX-31
 */
class AddOnRegistry(val addOns: List<AddOn>) {
    init {
        require(addOns.map { it.id }.toSet().size == addOns.size) { "duplicate add-on id" }
    }

    fun find(id: String): AddOn? = addOns.firstOrNull { it.id == id }

    fun isOn(addOn: AddOn, profile: Profile) = addOn.id in profile.addons

    fun enabled(profile: Profile): List<AddOn> = addOns.filter { isOn(it, profile) }

    /** Builds every add-on's provider; one that fails to build or isn't configured is left out (fail soft). */
    fun providers(depsFor: (AddOn) -> Deps): List<Provider> = addOns.mapNotNull { addOn ->
        try {
            addOn.create(depsFor(addOn))
        } catch (e: Exception) {
            Logger.w("add-on ${addOn.id}", e)
            null
        }
    }
}

/** What an add-on is built from: shared services by type, the TV's apps and its own credentials. */
class DataDeps(
    override val credentials: ProviderCredentials,
    private val activeProfile: suspend () -> Profile,
    private val installed: (String) -> Boolean,
    private val services: Map<Class<*>, Any>,
) : Deps {
    override fun isInstalled(appPackage: String) = installed(appPackage)

    override suspend fun profile(): Profile = activeProfile()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> service(type: Class<T>): T? = services[type] as T?
}
