package com.offlinevault.app.service.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AiBriefingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val summaryText: String,
    val sourceUrl: String? = null,
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object AiBriefingManager {
    private const val FILE_NAME = "ai_briefings.json"

    fun getBriefings(context: Context): List<AiBriefingItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val defaults = getDefaultBriefings(context)
            saveBriefings(context, defaults)
            return defaults
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<AiBriefingItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AiBriefingItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        subtitle = obj.optString("subtitle", "AI Executive Summary"),
                        summaryText = obj.getString("summaryText"),
                        sourceUrl = if (obj.has("sourceUrl") && !obj.isNull("sourceUrl")) obj.getString("sourceUrl") else null,
                        audioPath = if (obj.has("audioPath") && !obj.isNull("audioPath")) obj.getString("audioPath") else null,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) getDefaultBriefings(context) else list
        } catch (e: Exception) {
            getDefaultBriefings(context)
        }
    }

    private fun getDefaultBriefings(context: Context): List<AiBriefingItem> {
        return listOf(
            AiBriefingItem(
                id = "default_tech_dispatch",
                title = "⚡ Morning Tech & AI Dispatch",
                subtitle = "On-Device Neural Processing & Edge AI",
                summaryText = "Executive Driving Briefing: First, mobile neural accelerators now execute 4-bit quantized language models completely offline at over 25 tokens per second. Second, local intelligence eliminates cloud server latency while guaranteeing complete private user confidentiality. Third, offline media caching coupled with neural summaries provides continuous knowledge access anywhere, including remote off-grid driving routes.",
                sourceUrl = "offline://internal/tech-dispatch",
                audioPath = null,
                timestamp = System.currentTimeMillis()
            ),
            AiBriefingItem(
                id = "default_acoustic_mastery",
                title = "🎙️ Offline Media & Audio Masterclass",
                subtitle = "Opus Encoding & Driving Acoustics",
                summaryText = "Driving Acoustics Summary: First, 24 kilobit Opus voice codecs achieve broadcast-level speech clarity while consuming under 11 megabytes per hour. Second, automatic audio focus ducking protects navigational voice safety prompts without losing playback state. Third, synthesized text-to-speech narration provides hands-free book learning directly through vehicle car speakers.",
                sourceUrl = "offline://internal/acoustic-mastery",
                audioPath = null,
                timestamp = System.currentTimeMillis() - 3600000L
            )
        )
    }

    fun addBriefing(context: Context, item: AiBriefingItem) {
        val existing = getBriefings(context).toMutableList()
        existing.removeAll { it.id == item.id }
        existing.add(0, item)
        saveBriefings(context, existing)
    }

    private fun saveBriefings(context: Context, list: List<AiBriefingItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("summaryText", item.summaryText)
                    put("sourceUrl", item.sourceUrl ?: "")
                    put("audioPath", item.audioPath ?: "")
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            File(context.filesDir, FILE_NAME).writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun synthesizeBriefingAudio(
        context: Context,
        briefing: AiBriefingItem,
        tts: TextToSpeech,
        onComplete: (File) -> Unit
    ) {
        val cacheDir = File(context.cacheDir, "briefing_audio").apply { mkdirs() }
        val outputFile = File(cacheDir, "briefing_${briefing.id}.wav")

        if (outputFile.exists() && outputFile.length() > 1024L) {
            onComplete(outputFile)
            return
        }

        val utteranceId = "utterance_${briefing.id}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && outputFile.exists()) {
                    onComplete(outputFile)
                }
            }
            override fun onError(id: String?) {
                if (id == utteranceId && outputFile.exists()) {
                    onComplete(outputFile)
                }
            }
        })

        val params = Bundle()
        tts.synthesizeToFile(briefing.summaryText, params, outputFile, utteranceId)
    }
}
