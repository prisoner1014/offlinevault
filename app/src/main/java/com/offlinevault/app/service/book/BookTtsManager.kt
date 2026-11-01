package com.offlinevault.app.service.book

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val accent: String, // "US 🇺🇸", "UK 🇬🇧", "System 🌐"
    val gender: String, // "Female", "Male", "Natural"
    val locale: Locale,
    val isOffline: Boolean = true,
    val nativeVoice: Voice? = null
)

class BookTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val prefs = context.getSharedPreferences("vault_tts_prefs", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    var isReady by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var currentParagraphIndex by mutableIntStateOf(-1)
        private set

    // Speech Rate & Pitch
    var speechRate by mutableFloatStateOf(prefs.getFloat("rate", 1.0f))
        private set
    var pitch by mutableFloatStateOf(prefs.getFloat("pitch", 1.0f))
        private set

    // Available and Selected Voices
    var availableVoices by mutableStateOf<List<TtsVoiceOption>>(emptyList())
        private set
    var selectedVoiceId by mutableStateOf(prefs.getString("voice_id", "us_female") ?: "us_female")
        private set

    private var paragraphList: List<String> = emptyList()
    var onParagraphChanged: ((Int) -> Unit)? = null
    var onChapterCompleted: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupVoices()
            applyCurrentSettings()
            setupProgressListener()
            isReady = true
        }
    }

    private fun setupVoices() {
        val voiceList = mutableListOf<TtsVoiceOption>()

        // 1. Curated Fallback & Fast-Switch Presets
        voiceList.add(
            TtsVoiceOption(
                id = "us_female",
                displayName = "US Female (Natural)",
                accent = "US 🇺🇸",
                gender = "Female",
                locale = Locale.US
            )
        )
        voiceList.add(
            TtsVoiceOption(
                id = "us_male",
                displayName = "US Male (Warm)",
                accent = "US 🇺🇸",
                gender = "Male",
                locale = Locale.US
            )
        )
        voiceList.add(
            TtsVoiceOption(
                id = "uk_female",
                displayName = "UK Female (Oxford)",
                accent = "UK 🇬🇧",
                gender = "Female",
                locale = Locale.UK
            )
        )
        voiceList.add(
            TtsVoiceOption(
                id = "uk_male",
                displayName = "UK Male (Standard)",
                accent = "UK 🇬🇧",
                gender = "Male",
                locale = Locale.UK
            )
        )
        voiceList.add(
            TtsVoiceOption(
                id = "system_default",
                displayName = "System Default",
                accent = "System 🌐",
                gender = "Natural",
                locale = Locale.getDefault()
            )
        )

        // 2. Query System Installed Offline Voices (Google TTS / Samsung TTS)
        try {
            val systemVoices = tts?.voices
            if (!systemVoices.isNullOrEmpty()) {
                val englishVoices = systemVoices.filter { voice ->
                    val lang = voice.locale?.language ?: ""
                    lang.equals("en", ignoreCase = true) && !voice.isNetworkConnectionRequired
                }

                for (v in englishVoices) {
                    val country = v.locale?.country?.uppercase() ?: ""
                    val accentLabel = when (country) {
                        "US" -> "US 🇺🇸"
                        "GB" -> "UK 🇬🇧"
                        "AU" -> "AU 🇦🇺"
                        "CA" -> "CA 🇨🇦"
                        else -> "$country 🌐"
                    }

                    val nameLower = v.name.lowercase()
                    val genderLabel = when {
                        nameLower.contains("female") || nameLower.contains("sfg") || nameLower.contains("f0") -> "Female"
                        nameLower.contains("male") || nameLower.contains("iom") || nameLower.contains("m0") -> "Male"
                        else -> "Natural"
                    }

                    val friendlyName = buildString {
                        append(accentLabel.split(" ").firstOrNull() ?: "")
                        append(" ")
                        append(genderLabel)
                        append(" #")
                        append(v.name.takeLast(6).filter { it.isLetterOrDigit() })
                    }

                    voiceList.add(
                        TtsVoiceOption(
                            id = "sys_${v.name}",
                            displayName = friendlyName,
                            accent = accentLabel,
                            gender = genderLabel,
                            locale = v.locale ?: Locale.US,
                            isOffline = true,
                            nativeVoice = v
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Keep curated presets if system voices query throws
        }

        availableVoices = voiceList
    }

    private fun applyCurrentSettings() {
        val targetVoice = availableVoices.find { it.id == selectedVoiceId }
            ?: availableVoices.firstOrNull()

        targetVoice?.let { opt ->
            if (opt.nativeVoice != null) {
                try {
                    tts?.voice = opt.nativeVoice
                } catch (_: Exception) {
                    tts?.language = opt.locale
                }
            } else {
                tts?.language = opt.locale
            }

            // Adjust base pitch if preset male/female is used without native voice
            val effectivePitch = if (opt.nativeVoice == null) {
                when (opt.gender) {
                    "Male" -> (pitch * 0.85f).coerceIn(0.5f, 1.8f)
                    "Female" -> (pitch * 1.10f).coerceIn(0.5f, 1.8f)
                    else -> pitch
                }
            } else {
                pitch
            }
            tts?.setPitch(effectivePitch)
        }

        tts?.setSpeechRate(speechRate)
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    isSpeaking = true
                    isPaused = false
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (utteranceId?.startsWith("test_preview") == true) {
                        isSpeaking = false
                        isPaused = false
                        return@launch
                    }

                    val nextIdx = currentParagraphIndex + 1
                    if (nextIdx < paragraphList.size) {
                        currentParagraphIndex = nextIdx
                        onParagraphChanged?.invoke(nextIdx)
                        speakCurrentParagraph()
                    } else {
                        isSpeaking = false
                        isPaused = false
                        onChapterCompleted?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch {
                    isSpeaking = false
                    isPaused = false
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch {
                    isSpeaking = false
                    isPaused = false
                }
            }
        })
    }

    fun selectVoice(voiceId: String) {
        selectedVoiceId = voiceId
        prefs.edit().putString("voice_id", voiceId).apply()
        applyCurrentSettings()
        if (isSpeaking) {
            speakCurrentParagraph()
        }
    }

    @JvmName("updatePitch")
    fun setPitch(newPitch: Float) {
        pitch = newPitch
        prefs.edit().putFloat("pitch", newPitch).apply()
        applyCurrentSettings()
    }

    fun setRate(newRate: Float) {
        speechRate = newRate
        prefs.edit().putFloat("rate", newRate).apply()
        tts?.setSpeechRate(newRate)
    }

    fun speakTestPreview(sampleText: String = "Hello, this is your offline reading voice. Ready for books and articles.") {
        if (!isReady) return
        applyCurrentSettings()
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "test_preview")
        tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, "test_preview")
    }

    fun startReading(paragraphs: List<String>, startIndex: Int = 0) {
        paragraphList = paragraphs
        currentParagraphIndex = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        isPaused = false
        onParagraphChanged?.invoke(currentParagraphIndex)
        speakCurrentParagraph()
    }

    private fun speakCurrentParagraph() {
        if (!isReady || currentParagraphIndex !in paragraphList.indices) {
            isSpeaking = false
            return
        }
        val text = paragraphList[currentParagraphIndex].trim()
        if (text.isBlank()) {
            val nextIdx = currentParagraphIndex + 1
            if (nextIdx < paragraphList.size) {
                currentParagraphIndex = nextIdx
                onParagraphChanged?.invoke(nextIdx)
                speakCurrentParagraph()
            } else {
                isSpeaking = false
                onChapterCompleted?.invoke()
            }
            return
        }

        applyCurrentSettings()
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "para_$currentParagraphIndex")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "para_$currentParagraphIndex")
    }

    fun pause() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            isPaused = true
        }
    }

    fun resume() {
        if (isPaused && currentParagraphIndex in paragraphList.indices) {
            isPaused = false
            speakCurrentParagraph()
        }
    }

    fun togglePlayPause() {
        if (isSpeaking) {
            pause()
        } else if (isPaused) {
            resume()
        } else if (paragraphList.isNotEmpty()) {
            startReading(paragraphList, currentParagraphIndex.coerceAtLeast(0))
        }
    }

    fun nextParagraph() {
        if (currentParagraphIndex + 1 < paragraphList.size) {
            currentParagraphIndex++
            onParagraphChanged?.invoke(currentParagraphIndex)
            if (isSpeaking) {
                speakCurrentParagraph()
            }
        }
    }

    fun previousParagraph() {
        if (currentParagraphIndex > 0) {
            currentParagraphIndex--
            onParagraphChanged?.invoke(currentParagraphIndex)
            if (isSpeaking) {
                speakCurrentParagraph()
            }
        }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        isPaused = false
        currentParagraphIndex = -1
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
