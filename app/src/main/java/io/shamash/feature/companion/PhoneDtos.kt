package io.shamash.feature.companion

import io.shamash.domain.ContentLevel
import io.shamash.domain.FeedKind
import io.shamash.domain.FeedStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What the phone API reads and writes (JSON). Requests are parsed strictly: an unknown field, a wrong type or a
 * missing required field is a 400, never a guess. Nothing here ever carries a PIN, a passphrase or a field's value
 * back to the phone. SPEC: CMP-06..15
 */

/** The active profile as the page shows it, and what the phone needs to edit it. SPEC: CMP-06 */
@Serializable
data class StateOut(
    val profile: String,
    val kind: String,
    val edit: String,
    val backup: String,
    val queue: Int,
    val fields: Int,
)

/** One Home row of the editor model (LAY-02); [title] is the user's name, null for the default. SPEC: CMP-07 */
@Serializable
data class RowOut(
    val id: String,
    val title: String?,
    val defaultTitle: String,
    val hidden: Boolean,
    val pinned: Boolean,
    val order: Int,
)

@Serializable
data class RowsOut(val rows: List<RowOut>, val maxRows: Int, val maxTitle: Int)

@Serializable
data class RowIn(
    val id: String,
    val title: String? = null,
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val order: Int = 0,
)

@Serializable
data class RowsIn(val rows: List<RowIn>)

/** A row the TV could show, as the backend knows it: id and its default title. */
data class RowChoice(val id: String, val defaultTitle: String)

@Serializable
data class CompetitionOut(val id: String, val name: String, val sport: String)

/** SPEC: CMP-09 */
@Serializable
data class TeamsOut(
    val teams: List<String>,
    val competitions: List<String>,
    val catalog: List<CompetitionOut>,
    val maxTeams: Int,
    val maxName: Int,
)

@Serializable
data class TeamsIn(val teams: List<String>, val competitions: List<String>)

/** A feed as the phone sees it: the host only, since an address can carry a login. SPEC: CMP-10, PEX-16 */
@Serializable
data class FeedOut(
    val id: String,
    val title: String,
    val host: String,
    val kind: FeedKind,
    val style: FeedStyle,
    val level: ContentLevel,
)

@Serializable
data class FeedsOut(val feeds: List<FeedOut>, val max: Int)

/** add, remove (by [id]) or try. SPEC: CMP-10 */
@Serializable
data class FeedIn(
    val action: String,
    val id: String? = null,
    val title: String = "",
    val url: String = "",
    val kind: FeedKind = FeedKind.RSS,
    val style: FeedStyle = FeedStyle.WIDE,
    val level: ContentLevel = ContentLevel.ALL,
    val allowHttp: Boolean = false,
)

/** A text field waiting on the TV; never its value. SPEC: CMP-08 */
@Serializable
data class FieldOut(val code: String, val label: String, val secret: Boolean)

@Serializable
data class FieldsOut(val fields: List<FieldOut>)

@Serializable
data class FieldIn(val code: String, val value: String)

/** SPEC: CMP-11 */
@Serializable
data class QueueIn(val text: String)

/** SPEC: CMP-13, CMP-14 */
@Serializable
data class ExportIn(val passphrase: String, val includeKeys: Boolean = false, val includePins: Boolean = false)

/** [file] is the backup's text (the envelope is JSON). SPEC: CMP-15 */
@Serializable
data class ImportIn(val passphrase: String, val file: String)

/** The answer to a change: [code] is a key the page turns into its own words. */
@Serializable
data class Answer(
    val ok: Boolean,
    val code: String,
    val who: String? = null,
    val seconds: Long? = null,
    val added: Int? = null,
    val replaced: Int? = null,
    val titles: List<String>? = null,
)

internal object PhoneJson {
    /** Requests: known fields only. */
    val strict = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = false }

    /** Answers: compact, nulls left out. */
    val out = Json { explicitNulls = false; encodeDefaults = true }
}
