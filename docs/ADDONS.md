# Writing a Shamash add-on

An add-on is an optional content source: a `Provider` that is off until a profile turns it on in **Settings → Add-ons**,
built from one registration line, with its own folders that can be deleted cleanly. Add-ons are compiled in (no dynamic
code loading, PLT-07). The working template is `app/src/main/java/io/shamash/addons/example`: copy it, rename it, and
follow the steps below. Specs: [platform.md](specs/platform.md) (PLT-04..07), [providers-extra.md](specs/providers-extra.md)
(PEX-01..08, PEX-30, PEX-31).

## 1. Layout

```
app/src/main/java/io/shamash/addons/<name>/   your code (or data/<name> + feature/<name>)
app/src/main/res/values/strings_<name>.xml     English strings      values-ar/strings_<name>.xml  Arabic
app/src/test/java/io/shamash/addons/<name>/    tests, named after spec IDs
app/src/test/resources/fixtures/<name>/        recorded or hand-made answers (no real keys)
docs/specs/addon-<name>.md                     your spec, with its own IDs
```

## 2. Register it (`AddOn`, PEX-01)

```kotlin
object MyAddOn {
    const val ID = "mine"                                   // [A-Za-z0-9.-]+, also the row-id prefix
    val addOn = AddOn(ID, R.string.mine_name, R.string.mine_desc, fields, ::create)
    fun create(deps: Deps): MyProvider? {                   // null = left out (fail soft)
        val http = deps.service(Http::class.java) ?: return null
        return MyProvider(deps.credentials, ServerHttp(http), say = { /* string resource */ "" })
    }
}
```

Then add **one** fully qualified line to `Sources.addOns` in `app/src/main/java/io/shamash/data/Sources.kt`:
`io.shamash.addons.mine.MyAddOn.addOn,` (no import, so removal is one line). Being on is per profile
(`Profile.addons`, PEX-02); never turn yourself on by default.

**`Deps`** is everything you get from the app: `credentials`, `isInstalled(pkg)`, `profile()`, and shared services by
type through `service(...)`: `Http`, `Preferences`, `Context` (each may be null; return null from `create` then).
Providers are built once at start; read credentials when you fetch, not in `create`.

## 3. Credentials (`CredentialField`, `SecretCredentials`, PEX-03/04)

Describe what you need as data; the shared settings panel draws the fields, Save, **Test connection** and **Remove**:

```kotlin
val fields = listOf(
    CredentialField("url", R.string.mine_url, CredentialKind.URL, hintRes = R.string.mine_url_hint),
    CredentialField("token", R.string.mine_token, CredentialKind.TOKEN, required = false),
)
```

`deps.credentials` is a `SecretCredentials`: values live on the TV only, per profile, in the secret store
(`secret_<profile>_<addOn>_<key>`), never in the profile, backups or the HTTP cache. `SECRET` and `TOKEN` are masked.
`get(key)` returns null for blank; `toString()` redacts. Rules:

- Never log a value, a URL that carries one, or a response body. `Logger.w` keeps only the exception's class;
  name servers with `Redact.host(url)`.
- Send tokens in a header (`Authorization`) rather than the query string where the service allows it.
- A password exchanged for a token is forgotten after the exchange (see `data/jellyfin`).

## 4. Fetching (`ServerProvider`, `ServerHttp`, `HttpPrivacy`)

Extend `ServerProvider` unless you need something else; it already does PEX-06/07 and ARCH-05: on only when the profile
enables it, the rows the user chose, a 2 h cache (empty answers retried after 10 min), 8 s per row, the
`ContentGate` on every card, `featured` from cards with wide art, and empty lists when anything throws.
You write `rowSources()`, `build(profile, wanted)` and `test()`.

- Parse user addresses with `ServerUrl.parse(raw, HttpPolicy.PRIVATE_ONLY)`: https always, http only on the home
  network. Do not add cleartext or network-security-config changes.
- Fetch with `ServerHttp` (`json`, `text`, `read`): bounded time and size, gzip understood, and every request is tagged
  `HttpPrivacy.PRIVATE`, so OkHttp never stores it in the disk cache. If you call `Http` directly with credentials,
  add `HttpPrivacy.PRIVATE to "1"` to the headers yourself (`Authorization`, `X-Api-Key`, `token=`... are caught too).
- No network on the main thread: everything is `suspend` and `ServerHttp` runs on `Dispatchers.IO`.
- Parse leniently (`jStr`, `jArr`... in `data/ServerJson.kt`): drop an entry you can't show or open, never throw on
  odd data. XML from the internet goes through `SafeXml` (no DTDs, no entities). Text is plain, never HTML.

## 5. Rows (`RowSource`, `RowSourceProvider`, PEX-06)

```kotlin
override fun rowSources() = listOf(
    RowSource("new", R.string.mine_row_new),                        // on by default
    RowSource("popular", R.string.mine_row_popular, defaultOn = false),
)
```

At most `RowSources.MAX` (12). Home shows them as shelves with id `"<addOnId>:<sourceId>"`; the user's on/off and
renames are saved as `RowPref`s through `RowSources.withOn/withTitle`, and Remove clears them (`RowSources.cleared`).
Inside `build`, wrap each row in `source(s, style) { ... }` so one slow or failing row doesn't sink the rest.

## 6. Test connection (`TestableProvider`, PEX-05)

`test(): TestResult(ok, message)` checks the saved credentials and returns a human sentence from your strings: bad
address, signed out, empty, failed, or "Connected: N titles". Never echo the address, a token or an exception message.

## 7. Content levels (`ContentGate`, PRF-12)

Set `MediaItem.rating` to the title's certification ("PG-13", "TV-Y") when the service knows it. `ServerProvider`
passes every shelf through `ContentGate.filter`: ALL shows everything, FAMILY and KIDS show only known ratings on their
list, so an unrated item is hidden there. If you implement `Provider` directly, call `ContentGate.filter(items, profile)`
yourself, in `shelves`, `featured` and `search`.

## 8. Opening items (ARCH-11)

Every card has an `OpenTarget`: `AppPage(uri, appPackage)` for a deep link, `App(pkg)`, `Video(youtubeId)`,
`AppScreen(...)`. The Opener falls back to launching the app. Keep `id` unique (`"<addOnId>:<your id>"`) and
`providerId = ID`.

## 9. Strings (PEX-30)

Every name, description, field label, hint, row title and test message goes in your own `strings_<name>.xml`, in
`values/` and `values-ar/`. Never edit the shared `strings.xml`. Arabic is required, not optional.

## 10. Tests (ARCH-09)

JVM unit tests, named after spec IDs (`` `MINE_03 drops items without a link` ``), with fixtures under
`src/test/resources/fixtures/<name>/`. Useful helpers in `app/src/test/java/io/shamash/data/ServerTestKit.kt`:
`FakeServer` (a MockWebServer answering by path), `MemorySecrets`, `say`. Checklist, as in
`app/src/test/java/io/shamash/addons/example/ExampleAddOnTest.kt`:

- [ ] registered once in `Sources.addOns`, off for a new `Profile()`, `create` null without its services
- [ ] parsing: a fixture in, cards out; bad entries dropped; caps respected
- [ ] rows: default on/off, ids `"<id>:<source>"`, turning a row on shows it
- [ ] ContentGate: a KIDS and a FAMILY profile see only what they should
- [ ] fail soft: off, unconfigured, HTTP 500 and bad JSON give empty lists, no exception
- [ ] secrets: the token is a header, not in the URL; `test()` messages and `toString()` don't contain it
- [ ] `test()`: each outcome gives its message

Run `./gradlew testDebugUnitTest assembleRelease`; both must pass.

## 11. Removing an add-on (PEX-31)

Delete its folders (`data/<name>`, `feature/<name>`, `addons/<name>`, their tests and fixtures), its
`strings_<name>.xml` files, and its line in `Sources.addOns`. That must compile and pass the tests.
`AddOnRemovabilityTest` (`app/src/test/java/io/shamash/addons/AddOnRemovabilityTest.kt`) enforces it: outside its
folders an add-on's package may appear only on its registration line and on one line in each documented hook:

| Hook | Why |
|---|---|
| `feature/settings/AddonsPanel.kt` | a `when (addOn.id)` branch for extra settings beside the shared panel (Plex sign-in, IPTV favorites, feed editor) |
| `feature/home/HomeActions.kt` (Cinemana only) | known debt: resume in Watchme+; delete the import and its branches with Cinemana |
| `app/ServiceLocator.kt` (Trakt only) | starts Trakt / Simkl write-back |
| `feature/companion/` (My feed only) | known debt (`DEBT` in the test): the phone's feed editor uses My feed's types; never add to it |

Add a hook only when there is no other way, list it in that test and here. Prefer optional interfaces the core already
understands (`RowSourceProvider`, `TestableProvider`, `PreviewSource`, `RefreshableProvider`, `PersonalBridge`).

## Rules in short (PLT-06)

No network on the main thread · no secrets in logs, URLs or the HTTP cache · ContentLevel honored · en + ar strings ·
fails soft · removable (PEX-31) · its own spec `docs/specs/addon-<name>.md` · off by default.
