package io.shamash.domain

/**
 * Whether Home fetches a provider at all (LAY-04): hidden rows are not fetched when nothing else needs them. Pure.
 * SPEC: LAY-04, LAY-12
 */
object LayoutFetch {
    /**
     * True when the billboard may use [providerId]'s featured titles (not switched off in the carousel mix, LAY-12),
     * when [otherReader] (another tab reads its rows), when its rows aren't known yet ([rowIds] empty), or when one of
     * them is shown with [layout].
     */
    fun needed(providerId: String, rowIds: List<String>, layout: Layout, otherReader: Boolean = false): Boolean {
        if (otherReader || providerId !in layout.mixOff || rowIds.isEmpty()) return true
        return LayoutOps.visible(rowIds.distinct(), layout).isNotEmpty()
    }
}
