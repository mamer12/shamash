package io.shamash.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Opens preference stores by name under one directory (`<name>.preferences_pb`, the layout Android's own
 * `preferencesDataStore` uses, so existing files are read in place). One instance per file, as DataStore requires.
 * SPEC: PRF-02
 */
class DataStores(private val dir: File, private val scope: CoroutineScope) {
    private val open = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun store(name: String): DataStore<Preferences> = open.getOrPut(name) {
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "$name.preferences_pb").also { dir.mkdirs() } }
    }
}
