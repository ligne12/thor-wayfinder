package app.wayfinder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class IisuPanelTest {
    @Test fun tileLayout_followsHeightThenValue() {
        assertEquals(TileLayout.INLINE, tileLayout(36f, hasValue = false))
        assertEquals(TileLayout.INLINE, tileLayout(47.9f, hasValue = true))     // under 48 dp: side by side, value dropped
        assertEquals(TileLayout.TWO_LINES, tileLayout(64f, hasValue = false))   // a long name on two lines
        assertEquals(TileLayout.NAME_VALUE, tileLayout(64f, hasValue = true))   // name on one line, value under it
        assertEquals(TileLayout.LARGE, tileLayout(72f, hasValue = false))       // the sound panel's tiles, unchanged
    }
}
