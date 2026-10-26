package com.fanjv.netproxy.feature.dashboard.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayOutboundModesTest {
    private val full = listOf("Rule", "Proxy", "Direct", "RuleAllowAds")

    @Test fun splitDefaultsToAllowAds() {
        val entries = displayOutboundModes(full, false, "SPLIT", "PROXY", "DIRECT")
        assertEquals(
            listOf(
                ModeDisplayEntry("SPLIT", "RuleAllowAds", setOf("Rule", "RuleAllowAds")),
                ModeDisplayEntry("PROXY", "Proxy", setOf("Proxy")),
                ModeDisplayEntry("DIRECT", "Direct", setOf("Direct")),
            ),
            entries,
        )
    }

    @Test fun splitWithBlockAdsWritesRule() {
        val entries = displayOutboundModes(full, true, "SPLIT", "PROXY", "DIRECT")
        assertEquals("Rule", entries.first().kernelMode)
        assertEquals(setOf("Rule", "RuleAllowAds"), entries.first().matches)
    }

    @Test fun splitFallsBackToPresentMode() {
        val entries = displayOutboundModes(listOf("Rule", "Direct"), false, "SPLIT", "PROXY", "DIRECT")
        assertEquals("Rule", entries.first().kernelMode)
    }

    @Test fun unknownModesPassThroughRaw() {
        val entries = displayOutboundModes(listOf("Rule", "Custom"), false, "SPLIT", "PROXY", "DIRECT")
        assertEquals(
            listOf(
                ModeDisplayEntry("SPLIT", "Rule", setOf("Rule", "RuleAllowAds")),
                ModeDisplayEntry("Custom", "Custom", setOf("Custom")),
            ),
            entries,
        )
    }

    @Test fun noSplitModesMeansNoSplitEntry() {
        val entries = displayOutboundModes(listOf("Proxy", "Direct"), false, "SPLIT", "PROXY", "DIRECT")
        assertEquals(
            listOf(
                ModeDisplayEntry("PROXY", "Proxy", setOf("Proxy")),
                ModeDisplayEntry("DIRECT", "Direct", setOf("Direct")),
            ),
            entries,
        )
    }
}
