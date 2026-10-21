package io.shamash.domain

/** The sports the Match Centre covers. SPEC: MC-07 */
enum class Sport { SOCCER, F1, TENNIS, BASKETBALL, CRICKET }

/** One competition the user can pick: data, not code. [path] is ESPN's `<sport>/<league>`. SPEC: MC-07 */
data class CompetitionDef(val id: String, val sport: Sport, val path: String, val name: String)

object Competitions {
    val catalog: List<CompetitionDef> = listOf(
        CompetitionDef("uefa.champions", Sport.SOCCER, "soccer/uefa.champions", "Champions League"),
        CompetitionDef("uefa.europa", Sport.SOCCER, "soccer/uefa.europa", "Europa League"),
        CompetitionDef("eng.1", Sport.SOCCER, "soccer/eng.1", "Premier League"),
        CompetitionDef("esp.1", Sport.SOCCER, "soccer/esp.1", "LaLiga"),
        CompetitionDef("ger.1", Sport.SOCCER, "soccer/ger.1", "Bundesliga"),
        CompetitionDef("ita.1", Sport.SOCCER, "soccer/ita.1", "Serie A"),
        CompetitionDef("fra.1", Sport.SOCCER, "soccer/fra.1", "Ligue 1"),
        CompetitionDef("ksa.1", Sport.SOCCER, "soccer/ksa.1", "Saudi Pro League"),
        CompetitionDef("f1", Sport.F1, "racing/f1", "Formula 1"),
        CompetitionDef("atp", Sport.TENNIS, "tennis/atp", "ATP Tour"),
        CompetitionDef("wta", Sport.TENNIS, "tennis/wta", "WTA Tour"),
        CompetitionDef("nba", Sport.BASKETBALL, "basketball/nba", "NBA"),
        CompetitionDef("cricket", Sport.CRICKET, "cricket", "Cricket (live and upcoming)"),
    )

    private val byId = catalog.associateBy { it.id }

    fun find(id: String): CompetitionDef? = byId[id]

    /** A code that isn't in the catalog is a soccer league, as before (profiles saved before MC-07). */
    fun resolve(id: String): CompetitionDef = byId[id] ?: CompetitionDef(id, Sport.SOCCER, "soccer/$id", id)

    fun grouped(): Map<Sport, List<CompetitionDef>> = catalog.groupBy { it.sport }
}
