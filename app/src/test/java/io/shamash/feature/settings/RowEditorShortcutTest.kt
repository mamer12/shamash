package io.shamash.feature.settings

import io.shamash.feature.home.HomeActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Menu on a Home row opens Settings at the row editor. SPEC: LAY-02 */
class RowEditorShortcutTest {
    @Test fun `LAY_02 the row editor link names the Layout section`() {
        val section = HomeActions.ROUTE_ROW_EDITOR.substringAfter("section=")
        assertEquals("settings", HomeActions.ROUTE_ROW_EDITOR.substringBefore('?'))
        assertEquals(Section.LAYOUT, Section.named(section))
        assertEquals(Section.LAYOUT, Section.named("Layout"))
        assertNull(Section.named(null))
        assertNull(Section.named("nope"))
    }
}
