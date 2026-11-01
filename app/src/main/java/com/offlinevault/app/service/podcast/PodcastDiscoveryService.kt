package com.offlinevault.app.service.podcast

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DiscoveredPodcast(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val genre: String?,
    val episodeCount: Int,
    val releaseDateEpochMs: Long = 0L,
    val releaseDateFormatted: String = ""
)

enum class PodcastSortOrder(val label: String) {
    RECENTLY_UPDATED("Newest Episodes First"),
    POPULARITY("Most Popular / Relevant"),
    EPISODE_COUNT("Most Episodes")
}

enum class PodcastDateFilter(val label: String, val minDaysAgo: Int?) {
    ALL("All Time", null),
    PAST_MONTH("Active in Past 30 Days", 30),
    PAST_6_MONTHS("Active in Past 6 Months", 180),
    THIS_YEAR("Active This Year (2026)", 365),
    PAST_2_YEARS("Active in 2024-2026", 730)
}

class PodcastDiscoveryService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "PodcastDiscovery"
        private const val SEARCH_URL = "https://itunes.apple.com/search?term=%s&entity=podcast&limit=%d"
        private const val TOP_PODCASTS_URL = "https://itunes.apple.com/us/rss/toppodcasts/limit=50/json"
        private const val LOOKUP_URL = "https://itunes.apple.com/lookup?id=%s&entity=podcast"
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    /**
     * Searches iTunes directory with full query and parses release date for sorting & filtering
     */
    suspend fun searchPodcasts(query: String, limit: Int = 50): List<DiscoveredPodcast> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = String.format(SEARCH_URL, encodedQuery, limit)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OfflineVault/1.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Search failed with HTTP code: ${response.code}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            parseItunesResults(body)
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering podcasts for query '$query'", e)
            emptyList()
        }
    }

    /**
     * Fetches current Top Trending Podcasts from Apple Charts
     */
    suspend fun getTopPodcasts(limit: Int = 30): List<DiscoveredPodcast> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(TOP_PODCASTS_URL)
                .header("User-Agent", "OfflineVault/1.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val feed = json.optJSONObject("feed") ?: return@withContext emptyList()
            val entries = feed.optJSONArray("entry") ?: return@withContext emptyList()

            val ids = mutableListOf<String>()
            for (i in 0 until entries.length().coerceAtMost(limit)) {
                val entry = entries.optJSONObject(i) ?: continue
                val idObj = entry.optJSONObject("id")
                val idAttr = idObj?.optJSONObject("attributes")
                val imId = idAttr?.optString("im:id", "") ?: ""
                if (imId.isNotBlank()) ids.add(imId)
            }

            if (ids.isEmpty()) return@withContext emptyList()

            // Lookup full metadata including feedUrl and releaseDate
            val lookupUrl = String.format(LOOKUP_URL, ids.joinToString(","))
            val lookupReq = Request.Builder().url(lookupUrl).build()
            val lookupResp = client.newCall(lookupReq).execute()
            if (!lookupResp.isSuccessful) return@withContext emptyList()

            val lookupBody = lookupResp.body?.string() ?: return@withContext emptyList()
            parseItunesResults(lookupBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top podcasts", e)
            emptyList()
        }
    }

    private fun parseItunesResults(jsonString: String): List<DiscoveredPodcast> {
        val json = JSONObject(jsonString)
        val results = json.optJSONArray("results") ?: return emptyList()

        val list = mutableListOf<DiscoveredPodcast>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val feedUrl = item.optString("feedUrl", "")
            if (feedUrl.isBlank()) continue

            val title = item.optString("collectionName", item.optString("trackName", "Untitled Podcast"))
            val author = item.optString("artistName", "Unknown Host")
            val artworkUrl = item.optString("artworkUrl600", item.optString("artworkUrl100", ""))
            val genre = item.optString("primaryGenreName", "Podcast")
            val episodeCount = item.optInt("trackCount", 0)

            val rawDate = item.optString("releaseDate", "")
            var dateEpoch = 0L
            var formattedDate = ""

            if (rawDate.isNotBlank()) {
                try {
                    val parsed = isoDateFormat.parse(rawDate)
                    if (parsed != null) {
                        dateEpoch = parsed.time
                        formattedDate = displayDateFormat.format(parsed)
                    }
                } catch (_: Exception) {
                    formattedDate = rawDate.take(10)
                }
            }

            list.add(
                DiscoveredPodcast(
                    title = title,
                    author = author,
                    feedUrl = feedUrl,
                    artworkUrl = artworkUrl.ifBlank { null },
                    genre = genre.ifBlank { null },
                    episodeCount = episodeCount,
                    releaseDateEpochMs = dateEpoch,
                    releaseDateFormatted = formattedDate
                )
            )
        }
        return list
    }
}
