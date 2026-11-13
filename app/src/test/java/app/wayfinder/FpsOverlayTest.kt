package app.wayfinder

import app.wayfinder.ui.TokenArgb
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsOverlayTest {
    // iiSU fork (part 6): the counter sits on iiSU's menu card — every colour reads on it, light (#F1F6F8) and dark (#2E2F34)
    @Test fun colours_readOnTheMenuCard() {
        for ((dark, card) in listOf(false to 0xFFF1F6F8L, true to 0xFF2E2F34L)) {
            for (fps in listOf(0, 10, 30, 60)) {
                val c = TokenArgb.contrast(TokenArgb.of(FpsOverlay.rateColour(fps, dark)), card)
                assertTrue("rate $fps dark=$dark: $c", c >= 3.0)
            }
            val r = TokenArgb.contrast(TokenArgb.of(FpsOverlay.readingsColour(dark)), card)
            assertTrue("readings dark=$dark: $r", r >= 4.5)
        }
    }
}
