package app.wayfinder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IisuPanelTest {
    @Test fun tileLayout_followsHeightThenValue() {
        assertEquals(TileLayout.INLINE, tileLayout(36f, hasValue = false))
        assertEquals(TileLayout.INLINE, tileLayout(47.9f, hasValue = true))     // under 48 dp: side by side, value dropped
        assertEquals(TileLayout.TWO_LINES, tileLayout(64f, hasValue = false))   // a long name on two lines
        assertEquals(TileLayout.NAME_VALUE, tileLayout(64f, hasValue = true))   // name on one line, value under it
        assertEquals(TileLayout.LARGE, tileLayout(72f, hasValue = false))       // the sound panel's tiles, unchanged
    }

    // iiSU's focused settings row, measured on the Thor: #E2E5E7 on a #F5F7F8 card (light), #3D3E42 on #2E3033 (dark)
    @Test fun focusTint_matchesIisusFocusedRow() {
        fun close(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) <= 2f / 255f
        assertTrue(close(focusTint(dark = false).compositeOver(Color(0xFFF5F7F8)), Color(0xFFE2E5E7)))
        assertTrue(close(focusTint(dark = true).compositeOver(Color(0xFF2E2F34)), Color(0xFF3D3E42)))
    }

    // an "on" element (the selection gradient, measured mid-stop #7780D8) with the focus: plainly darker, in both themes —
    // the plain tint moved it 9 levels, unseen on the Thor
    @Test fun focusTint_onTheGradientStaysVisible() {
        val mid = Color(0xFF7780D8)
        for (dark in listOf(false, true)) {
            val f = focusTint(dark, onFill = true).compositeOver(mid)
            assertTrue(mid.red - f.red >= 20f / 255f && mid.green - f.green >= 20f / 255f && mid.blue - f.blue >= 20f / 255f)
        }
    }
}
