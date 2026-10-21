package io.shamash.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.shamash.domain.ContentLevel
import io.shamash.domain.Follow
import io.shamash.domain.KeyMode
import io.shamash.domain.Layout
import io.shamash.domain.LayoutPreset
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.RowPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class ProfileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var stores: DataStores
    private val ids = AtomicInteger()

    @Before fun setUp() { stores = DataStores(folder.newFolder("datastore"), scope) }
    @After fun tearDown() { scope.cancel() }

    private fun store(newId: () -> String = { "id-${ids.incrementAndGet()}" }) = ProfileStore(stores, newId, { 1L })

    /** Loads `resources/profiles/v1_store.json` into the v1 "profile" store, as the old app left it. */
    private fun seedV1() = runBlocking {
        val text = checkNotNull(javaClass.classLoader?.getResource("profiles/v1_store.json")).readText()
        val root: JsonObject = Json.parseToJsonElement(text).jsonObject
        stores.store(ProfileStore.LEGACY).edit { s ->
            root["strings"]?.jsonObject?.forEach { (k, v) -> s[stringPreferencesKey(k)] = v.jsonPrimitive.content }
            root["booleans"]?.jsonObject?.forEach { (k, v) -> s[booleanPreferencesKey(k)] = v.jsonPrimitive.boolean }
            root["doubles"]?.jsonObject?.forEach { (k, v) -> s[doublePreferencesKey(k)] = v.jsonPrimitive.double }
            root["stringSets"]?.jsonObject?.forEach { (k, v) ->
                s[stringSetPreferencesKey(k)] = v.jsonArray.map { it.jsonPrimitive.content }.toSet()
            }
        }
    }

    @Test fun PRF_17_v1_store_becomes_one_owner_profile_with_nothing_lost() = runBlocking {
        seedV1()
        val store = store()
        val p = store.current()
        assertEquals("id-1", p.id)
        assertEquals(ProfileKind.ADULT, p.kind)
        assertEquals(KeyMode.SHARED, p.keyMode)
        assertEquals(LayoutPreset.CINEMA, p.layout.presetId)
        assertTrue(p.layout.rows.isEmpty())
        assertTrue(p.onboarded)
        assertEquals("Sara", p.name)
        assertEquals("Baghdad", p.city)
        assertEquals("IQ", p.region)
        assertEquals(33.31, p.latitude!!, 0.0)
        assertEquals(44.36, p.longitude!!, 0.0)
        assertEquals(setOf("com.netflix.ninja"), p.subscriptions)
        assertEquals(setOf("Real Madrid"), p.teams)
        assertEquals(setOf("esp.1"), p.competitions)
        assertEquals(setOf("drama"), p.genres)
        assertEquals("FIXTURE-NOT-A-KEY", p.tmdbKey)
        assertEquals("yt-id", p.youtubeClientId)
        assertEquals("yt-secret", p.youtubeClientSecret)
        assertEquals(setOf("cinemana"), p.addons)
        assertTrue(p.cinemanaAddon)
        assertEquals(setOf("com.netflix.ninja"), p.keepRunning)
        assertEquals(listOf(p), store.profiles.first())
        assertEquals("id-1", store.ownerId())
        assertEquals("id-1", store.activeId.first())
        assertFalse(store.isLegacyMode())
    }

    @Test fun PRF_18_stored_values_keep_their_meaning_and_secrets_stay_put() = runBlocking {
        seedV1()
        val store = store()
        assertEquals(ContentLevel.FAMILY, store.current().contentLevel)
        val old = stores.store(ProfileStore.LEGACY).data.first()
        assertEquals("SHAMASH", old[stringPreferencesKey("secret_mode")])
        assertEquals("1234", old[stringPreferencesKey("secret_phone_pin")])
        assertNull(old[stringPreferencesKey("name")])           // the old profile keys are gone once read back equal
        assertNull(old[booleanPreferencesKey("cinemana_addon")])
    }

    @Test fun PRF_17_migrating_twice_changes_nothing() = runBlocking {
        seedV1()
        val first = store().current()
        val second = store().current()        // a new process: the same files, a new store object
        assertEquals(first, second)
        assertEquals(1, store().profiles.first().size)
    }

    @Test fun PRF_18_a_fresh_install_gets_one_default_owner() = runBlocking {
        val store = store()
        val p = store.current()
        assertFalse(p.onboarded)
        assertEquals(ContentLevel.ALL, p.contentLevel)
        assertEquals(Profile().keepRunning, p.keepRunning)
        assertEquals(1, store.profiles.first().size)
    }

    @Test fun PRF_17_failure_keeps_the_old_store_and_runs_in_legacy_mode() = runBlocking {
        seedV1()
        val store = store(newId = { error("no ids today") })
        val p = store.current()
        assertEquals("Sara", p.name)
        assertTrue(store.isLegacyMode())
        store.save(p.copy(name = "Sara B"))
        assertEquals("Sara B", store.current().name)
        assertNotNull(stores.store(ProfileStore.LEGACY).data.first()[stringPreferencesKey("name")])
    }

    @Test fun PRF_02_save_round_trips_every_new_field() = runBlocking {
        val store = store()
        val p = store.current().copy(
            pinHash = "salt:hash", keyMode = KeyMode.OWN, addons = setOf("cinemana", "plex"),
            follows = setOf(Follow("team", "Real Madrid"), Follow("sport", "F1")), avatarColor = 0xFF112233.toInt(),
            layout = Layout("sports", listOf(RowPref("matches", pinned = true, order = 1), RowPref("tmdb:top10", hidden = true, title = "Mine"))),
        )
        store.save(p)
        assertEquals(p, store.current())
    }

    @Test fun LAY_19_corrupt_layout_json_falls_back_to_the_default_layout() = runBlocking {
        val store = store()
        val id = store.current().id
        stores.store("profile_$id").edit { it[stringPreferencesKey("layout")] = "{not json" }
        assertEquals(Layout(), store.current().layout)
    }

    @Test fun PRF_03_the_owner_stays_an_adult_and_cannot_be_deleted() = runBlocking {
        val store = store()
        val owner = store.current()
        store.save(owner.copy(kind = ProfileKind.KIDS))
        assertEquals(ProfileKind.ADULT, store.current().kind)
        assertFalse(store.delete(owner.id))
        assertEquals(1, store.profiles.first().size)
    }

    @Test fun PRF_01_PRF_03_profiles_get_fresh_ids_up_to_six() = runBlocking {
        val store = store()
        val added = (1..5).map { n -> checkNotNull(store.add(Profile(name = "P$n", kind = ProfileKind.KIDS))) }
        assertEquals(5, added.map { it.id }.toSet().size)
        assertNull(store.add(Profile(name = "seventh")))
        assertEquals(6, store.profiles.first().size)
        assertEquals(ProfileKind.ADULT, store.profiles.first().first().kind)   // the owner first
    }

    @Test fun PRF_02_switching_profile_changes_what_the_rest_of_the_app_reads() = runBlocking {
        val store = store()
        val owner = store.current()
        val kid = store.add(Profile(name = "Kid", kind = ProfileKind.KIDS, contentLevel = ContentLevel.KIDS))!!
        assertTrue(store.setActive(kid.id))
        assertEquals("Kid", store.current().name)
        store.save(store.current().copy(city = "Basra"))
        assertEquals("Basra", store.current().city)
        assertTrue(store.setActive(owner.id))
        assertNotEquals("Basra", store.current().city)
        assertFalse(store.setActive("nobody"))
        assertTrue(store.delete(kid.id))
        assertEquals(1, store.profiles.first().size)
    }

    @Test fun PRF_02_deleting_the_active_profile_activates_the_owner() = runBlocking {
        val store = store()
        val owner = store.current()
        val guest = store.add(Profile(name = "G", kind = ProfileKind.GUEST))!!
        store.setActive(guest.id)
        assertTrue(store.delete(guest.id))
        assertEquals(owner.id, store.activeId.first())
    }
}
