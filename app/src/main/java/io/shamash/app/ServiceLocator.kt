package io.shamash.app

import android.content.Context
import io.shamash.data.History
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.Recording
import io.shamash.data.signals.PersonalStore
import io.shamash.data.tmdb.TmdbClient
import io.shamash.domain.PersonalBridge
import io.shamash.feature.personalization.Personalization
import io.shamash.feature.personalization.WherePicker
import java.io.File
import io.shamash.data.Sources
import io.shamash.data.TvApps
import io.shamash.feature.profiles.ProfileSession
import io.shamash.feature.profiles.SecurityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The object graph, built once per process (manual DI). SPEC: ARCH-02 */
class ServiceLocator private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val http = Http(appContext.cacheDir)
    val prefs = Preferences(appContext)
    val profiles get() = prefs.profiles
    val apps = TvApps(appContext)
    val history = History(appContext)
    val providers by lazy { Sources.providers(http, prefs, apps, history, appContext) }
    val youtubeAuth by lazy { Sources.youtubeAuth(http, prefs) }
    val geocoder by lazy { Sources.geocoder(http) }
    val matches by lazy { Sources.matches(http) }
    val weather by lazy { Sources.weather(http) }
    val links by lazy { Sources.links(http) }
    val session by lazy {
        ProfileSession(prefs, SecurityStore(prefs.stores), CoroutineScope(SupervisorJob() + Dispatchers.Default), appContext.packageName)
    }
    val personal by lazy {
        Personalization(
            PersonalStore(File(appContext.filesDir, "personal")) { Recording.enabled }, prefs.profile,
            { providers.filterIsInstance<PersonalBridge>() }, CoroutineScope(SupervisorJob() + Dispatchers.Default),
            recordingOn = { Recording.enabled },
        ).also { it.start(); io.shamash.feature.trakt.TrackerWriteBack.start(it.state, providers) }
    }
    val opener by lazy {
        Opener(appContext, apps, links, history, prefs).also {
            it.policy = session
            it.personal = personal
            it.where = WherePicker(TmdbClient(http), prefs, apps::isInstalled, personal)
        }
    }

    companion object {
        @Volatile private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator =
            instance ?: synchronized(this) { instance ?: ServiceLocator(context).also { instance = it } }
    }
}
