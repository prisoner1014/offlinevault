package com.offlinevault.app.ui.screens.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockEngine {

    // Common ad, telemetry, and tracking hosts to intercept & drop
    private val blockedHostSuffixes = setOf(
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "adservice.google.com",
        "facebook.net",
        "scorecardresearch.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "adnxs.com",
        "advertising.com",
        "amazon-adsystem.com",
        "popads.net",
        "propellerads.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "applovin.com",
        "adjust.com",
        "appsflyer.com"
    )

    fun isAdOrTracker(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val host = try {
                java.net.URI(url).host?.lowercase()
            } catch (e: Exception) {
                null
            } ?: run {
                val withoutProtocol = url.substringAfter("://")
                withoutProtocol.substringBefore("/").substringBefore("?").substringBefore(":").lowercase()
            }
            if (host.isBlank()) return false
            blockedHostSuffixes.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }
}
