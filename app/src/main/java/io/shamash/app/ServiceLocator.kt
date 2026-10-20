package io.shamash.app

import android.content.Context
import io.shamash.data.History
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.Sources
import io.shamash.data.TvApps

/** The object graph, built once per process (manual DI). SPEC: ARCH-02 */
class ServiceLocator private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val http = Http(appContext.cacheDir)
    val prefs = Preferences(appContext)
    val apps = TvApps(appContext)
    val history = History(appContext)
    val providers by lazy { Sources.providers(http, prefs, apps) }
    val matches by lazy { Sources.matches(http) }
    val weather by lazy { Sources.weather(http) }
    val links by lazy { Sources.links(http) }
    val opener by lazy { Opener(appContext, apps, links, history, prefs) }

    companion object {
        @Volatile private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator =
            instance ?: synchronized(this) { instance ?: ServiceLocator(context).also { instance = it } }
    }
}
