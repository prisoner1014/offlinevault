package com.offlinevault.app.service.crawler

import android.webkit.WebResourceResponse
import java.io.File

object OfflineArchiveSchemeHandler {

    const val VIRTUAL_DOMAIN = "https://offline.vault.local"

    /**
     * Converts a local disk file path (or file:// URI) into the virtual https domain.
     * Serving offline assets through a secure virtual origin avoids Chromium's strict
     * file:// Same-Origin Policy (SOP) restrictions, enabling full HTML5 audio, styles,
     * and scripts to load offline.
     */
    fun toVirtualUrl(archivesDir: File, localPath: String): String {
        val cleanPath = localPath.removePrefix("file://").removePrefix("file:")
        val archivePrefix = archivesDir.absolutePath.replace('\\', '/').trimEnd('/')
        val normalizedPath = cleanPath.replace('\\', '/')

        return if (normalizedPath.startsWith(archivePrefix)) {
            val relative = normalizedPath.removePrefix(archivePrefix).trimStart('/')
            "$VIRTUAL_DOMAIN/$relative"
        } else {
            // If already virtual or external
            if (localPath.startsWith(VIRTUAL_DOMAIN) || localPath.startsWith("http://") || localPath.startsWith("https://")) {
                localPath
            } else {
                "file://$cleanPath"
            }
        }
    }

    /**
     * Determines proper MIME types for web assets, offline audio streams, and MHTML archives.
     */
    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "weba" -> "audio/webm"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mht", "mhtml" -> "multipart/related"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * Intercepts virtual domain and file:// asset requests and serves them directly from disk
     * with appropriate MIME headers and CORS allowances.
     */
    fun handleRequest(archivesDir: File, url: String): WebResourceResponse? {
        val cleanUrl = url.substringBefore("#")

        val targetFile: File? = if (cleanUrl.startsWith("$VIRTUAL_DOMAIN/")) {
            val rel = cleanUrl.removePrefix("$VIRTUAL_DOMAIN/").substringBefore("?")
            val direct = File(archivesDir, rel)
            if (direct.exists() && !direct.isDirectory) {
                direct
            } else {
                // Check in sibling subdirectories in case of relative asset resolution
                val parent = direct.parentFile
                val name = direct.name
                if (parent != null && parent.exists()) {
                    File(parent, "media/$name").takeIf { it.exists() && !it.isDirectory }
                        ?: File(parent, "images/$name").takeIf { it.exists() && !it.isDirectory }
                        ?: File(parent, "css/$name").takeIf { it.exists() && !it.isDirectory }
                        ?: File(parent, "js/$name").takeIf { it.exists() && !it.isDirectory }
                } else null
            }
        } else if (cleanUrl.startsWith("file://")) {
            val filePath = cleanUrl.removePrefix("file://").substringBefore("?")
            val direct = File(filePath)
            if (direct.exists() && !direct.isDirectory) direct else null
        } else {
            null
        }

        if (targetFile != null && targetFile.exists() && !targetFile.isDirectory) {
            val mimeType = getMimeType(targetFile.name)
            val encoding = if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType == "multipart/related") "utf-8" else null
            val response = WebResourceResponse(mimeType, encoding, targetFile.inputStream())

            response.responseHeaders = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                "Access-Control-Allow-Headers" to "*",
                "Accept-Ranges" to "bytes",
                "Cache-Control" to "no-cache"
            )
            return response
        }

        return null
    }
}
