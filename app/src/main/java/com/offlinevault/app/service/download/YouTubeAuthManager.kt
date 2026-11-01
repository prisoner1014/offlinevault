package com.offlinevault.app.service.download

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import java.io.File
import java.io.InputStream

/**
 * Manages YouTube account session cookies in Netscape format for yt-dlp.
 * This allows downloading age-restricted, private, and channel members-only videos
 * when authenticated to an account with an active channel membership.
 */
class YouTubeAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val authDir = File(context.filesDir, "auth").apply { mkdirs() }
    val cookieFile = File(authDir, "youtube_cookies.txt")

    companion object {
        private const val TAG = "YouTubeAuthManager"
        private const val PREFS_NAME = "offline_vault_youtube_auth"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_COOKIE_COUNT = "cookie_count"
        private const val KEY_ACCOUNT_LABEL = "account_label"

        // Essential session cookies required for YouTube authentication
        val ESSENTIAL_SESSION_COOKIES = setOf(
            "LOGIN_INFO",
            "SAPISID",
            "APISID",
            "SID",
            "HSID",
            "SSID",
            "__Secure-1PSID",
            "__Secure-3PSID",
            "__Secure-1PAPISID",
            "__Secure-3PAPISID"
        )
    }

    /**
     * Checks if a valid, non-empty cookies file exists and contains session credentials.
     */
    fun hasActiveCookies(): Boolean {
        if (!cookieFile.exists() || cookieFile.length() == 0L) {
            return false
        }
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)

    fun getCookieCount(): Int = prefs.getInt(KEY_COOKIE_COUNT, 0)

    fun getAccountLabel(): String = prefs.getString(KEY_ACCOUNT_LABEL, "YouTube Member Account") ?: "YouTube Member Account"

    /**
     * Converts a raw CookieManager header string ("key=val; key2=val2") into Netscape format
     * and saves it to internal private storage for yt-dlp.
     */
    fun saveFromRawCookieString(cookieHeader: String?, accountLabel: String = "YouTube Member Account"): Boolean {
        if (cookieHeader.isNullOrBlank()) return false

        try {
            val (formatted, count) = NetscapeCookieFormatter.rawCookiesToNetscape(cookieHeader)
            if (count == 0) return false

            val hasSessionCookie = NetscapeCookieFormatter.hasSessionTokens(cookieHeader)

            cookieFile.writeText(formatted)

            prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, hasSessionCookie || count >= 3)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .putInt(KEY_COOKIE_COUNT, count)
                .putString(KEY_ACCOUNT_LABEL, accountLabel)
                .apply()

            Log.d(TAG, "Successfully formatted and saved $count cookies to ${cookieFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save raw cookies", e)
            return false
        }
    }

    /**
     * Saves a pre-formatted Netscape cookie text (e.g. from an imported cookies.txt).
     */
    fun saveNetscapeText(content: String, accountLabel: String = "Imported cookies.txt"): Boolean {
        if (content.isBlank()) return false

        try {
            val count = NetscapeCookieFormatter.countValidCookies(content)
            if (count == 0 && !content.contains("youtube.com", ignoreCase = true)) {
                return false
            }

            cookieFile.writeText(content)

            prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .putInt(KEY_COOKIE_COUNT, count.coerceAtLeast(1))
                .putString(KEY_ACCOUNT_LABEL, accountLabel)
                .apply()

            Log.d(TAG, "Successfully imported Netscape cookies file ($count entries)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import Netscape text", e)
            return false
        }
    }

    /**
     * Reads a cookies.txt file directly from a URI (SAF document picker).
     */
    fun importFromUri(uri: Uri): Boolean {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return false
            saveNetscapeText(content, "Imported cookies.txt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cookies from URI $uri", e)
            false
        }
    }

    /**
     * Clears all saved cookies from disk, preferences, and optionally Android WebView.
     */
    fun clearCookies(clearWebViewCookies: Boolean = false) {
        if (cookieFile.exists()) {
            cookieFile.delete()
        }
        prefs.edit().clear().apply()

        if (clearWebViewCookies) {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (e: Exception) {
                Log.w(TAG, "Could not flush WebView cookies", e)
            }
        }
        Log.d(TAG, "Cleared YouTube session cookies")
    }
}

/**
 * Pure Kotlin formatter and validator for Netscape HTTP cookie files.
 * Tested independently of Android Context/Framework dependencies.
 */
object NetscapeCookieFormatter {

    fun rawCookiesToNetscape(cookieHeader: String): Pair<String, Int> {
        val pairs = cookieHeader.split(";").mapNotNull { entry ->
            val trimmed = entry.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                key to value
            } else null
        }

        if (pairs.isEmpty()) return "" to 0

        val expiry = "2147483647"
        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# https://curl.se/docs/http-cookies.html")
        sb.appendLine("# Generated by OfflineVault for YouTube Memberships")
        sb.appendLine()

        var count = 0
        pairs.forEach { (name, value) ->
            sb.appendLine(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
            if (name.startsWith("SID") || name.startsWith("HSID") || name.startsWith("SSID") ||
                name.startsWith("APISID") || name.startsWith("SAPISID") || name.startsWith("__Secure")
            ) {
                sb.appendLine(".google.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
            }
            count++
        }

        return sb.toString() to count
    }

    fun hasSessionTokens(cookieHeader: String): Boolean {
        return YouTubeAuthManager.ESSENTIAL_SESSION_COOKIES.any { cookieHeader.contains(it) }
    }

    fun countValidCookies(netscapeContent: String): Int {
        return netscapeContent.lines().count { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && trimmed.contains("\t")
        }
    }
}
