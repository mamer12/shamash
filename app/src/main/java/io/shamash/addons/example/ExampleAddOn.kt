package io.shamash.addons.example

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.ServerHttp
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Deps
import io.shamash.domain.RowSource

/**
 * The template add-on's registration: what Settings -> Add-ons lists, the credentials it asks for and how its
 * provider is built. Off until a profile turns it on (`Profile.addons`). Copy this package to start your own; see
 * docs/ADDONS.md. SPEC: PLT-04, PLT-05, PEX-01, PEX-03
 */
object ExampleAddOn {
    const val ID = "example"
    const val URL = "url"
    const val TOKEN = "token"

    /** The values the shared settings panel asks for (PEX-03); the token is masked and never shown again. */
    val fields = listOf(
        CredentialField(URL, R.string.example_url, CredentialKind.URL, hintRes = R.string.example_url_hint),
        CredentialField(TOKEN, R.string.example_token, CredentialKind.TOKEN, required = false),
    )

    /** The rows it can offer on Home (PEX-06): "<id>:new" on by default, "<id>:popular" off. */
    val rows = listOf(
        RowSource("new", R.string.example_row_new),
        RowSource("popular", R.string.example_row_popular, defaultOn = false),
    )

    /** The registration (PEX-01): one line in `Sources.addOns`. */
    val addOn = AddOn(ID, R.string.example_name, R.string.example_desc, fields, ::create)

    /** Null (left out, fail soft) when the app doesn't offer what it needs. */
    fun create(deps: Deps): ExampleProvider? {
        val http = deps.service(Http::class.java) ?: return null
        val context = deps.service(android.content.Context::class.java)
        return ExampleProvider(deps.credentials, ServerHttp(http), say = { id -> context?.getString(id).orEmpty() })
    }
}
