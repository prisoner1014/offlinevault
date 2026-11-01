package com.offlinevault.app.service.download

import org.junit.Assert.*
import org.junit.Test

class YouTubeAuthManagerTest {

    @Test
    fun testEssentialSessionCookiesAreRecognized() {
        val testHeader1 = "LOGIN_INFO=AFmmF2kwRA...; VISITOR_INFO1_LIVE=xyz123"
        assertTrue(NetscapeCookieFormatter.hasSessionTokens(testHeader1))

        val testHeader2 = "SAPISID=sec_token_val; PREF=f1=50000000"
        assertTrue(NetscapeCookieFormatter.hasSessionTokens(testHeader2))

        val testHeader3 = "VISITOR_INFO1_LIVE=xyz123; PREF=f1=50000000"
        assertFalse(NetscapeCookieFormatter.hasSessionTokens(testHeader3))
    }

    @Test
    fun testRawCookiesToNetscapeFormatting() {
        val rawCookies = "VISITOR_INFO1_LIVE=abc123; PREF=tz=UTC; LOGIN_INFO=token_abc; SID=google_sid_123"
        val (netscapeText, count) = NetscapeCookieFormatter.rawCookiesToNetscape(rawCookies)

        assertEquals(4, count)
        assertTrue(netscapeText.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(netscapeText.contains(".youtube.com\tTRUE\t/\tTRUE\t2147483647\tLOGIN_INFO\ttoken_abc"))
        assertTrue(netscapeText.contains(".youtube.com\tTRUE\t/\tTRUE\t2147483647\tSID\tgoogle_sid_123"))
        // Cross-domain mapping for Google SID
        assertTrue(netscapeText.contains(".google.com\tTRUE\t/\tTRUE\t2147483647\tSID\tgoogle_sid_123"))
    }

    @Test
    fun testCountValidNetscapeCookies() {
        val validNetscape = """
            # Netscape HTTP Cookie File
            # Ignored comment
            .youtube.com	TRUE	/	TRUE	2147483647	LOGIN_INFO	val1
            .youtube.com	TRUE	/	TRUE	2147483647	SAPISID	val2
            .youtube.com	TRUE	/	TRUE	2147483647	SID	val3
        """.trimIndent()

        val count = NetscapeCookieFormatter.countValidCookies(validNetscape)
        assertEquals(3, count)
    }

    @Test
    fun testEmptyOrInvalidRawCookieHandling() {
        val (emptyRes, emptyCount) = NetscapeCookieFormatter.rawCookiesToNetscape("")
        assertEquals(0, emptyCount)
        assertEquals("", emptyRes)

        val (invalidRes, invalidCount) = NetscapeCookieFormatter.rawCookiesToNetscape("invalid; no_equal_sign; ;")
        assertEquals(0, invalidCount)
        assertEquals("", invalidRes)
    }
}
