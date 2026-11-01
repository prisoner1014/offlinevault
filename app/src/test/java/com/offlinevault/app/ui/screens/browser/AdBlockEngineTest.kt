package com.offlinevault.app.ui.screens.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    @Test
    fun testBlocksKnownAdAndTrackerDomains() {
        val adUrls = listOf(
            "https://adservice.google.com/adsid/google/ui",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://www.google-analytics.com/analytics.js",
            "https://connect.facebook.net/en_US/fbevents.js",
            "https://secure.adnxs.com/seg?add=1",
            "https://a.scorecardresearch.com/beacon.js",
            "https://widgets.outbrain.com/outbrain.js",
            "https://c.amazon-adsystem.com/aax2/apstag.js"
        )

        for (url in adUrls) {
            assertTrue("Expected $url to be blocked by AdBlockEngine", AdBlockEngine.isAdOrTracker(url))
        }
    }

    @Test
    fun testAllowsLegitimateWebPagesAndContent() {
        val safeUrls = listOf(
            "https://en.wikipedia.org/wiki/Android_(operating_system)",
            "https://duckduckgo.com/html/",
            "https://github.com/junkfood02/Seal",
            "https://news.ycombinator.com/",
            "https://podcasts.google.com/"
        )

        for (url in safeUrls) {
            assertFalse("Expected $url to NOT be blocked", AdBlockEngine.isAdOrTracker(url))
        }
    }

    @Test
    fun testHandlesMalformedUrlsGracefully() {
        assertFalse(AdBlockEngine.isAdOrTracker(""))
        assertFalse(AdBlockEngine.isAdOrTracker("invalid-url-with-no-protocol"))
        assertFalse(AdBlockEngine.isAdOrTracker("javascript:void(0)"))
    }
}
