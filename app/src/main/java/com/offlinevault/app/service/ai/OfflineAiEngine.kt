package com.offlinevault.app.service.ai

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.ChatMessageEntity
import com.offlinevault.app.data.local.entity.ChatSessionEntity
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelTag: String? = null,
    val isStreaming: Boolean = false,
    val stats: String? = null
)

class OfflineAiEngine(
    private val context: Context,
    private val modelManager: AiModelManager
) {

    private val db = AppDatabase.getInstance(context)
    private val chatDao = db.chatDao()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val messages = mutableStateListOf<ChatMessage>()
    var currentSessionId by mutableStateOf<String?>(null)
        private set
    var currentSessionTitle by mutableStateOf("New Chat")
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var currentStreamingText by mutableStateOf("")
        private set

    // All saved chat sessions flow for history drawer/list
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    // Native llama model handle
    private var activeLoadedModel: LlamaModel? = null
    private var activeLoadedPath: String? = null

    init {
        // Load default welcome message
        initWelcomeMessage()
    }

    private fun initWelcomeMessage() {
        messages.clear()
        messages.add(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                text = """
👋 **Welcome to Offline Vault AI!**

I run 100% locally and privately on your phone without sending any data to the cloud or requiring an internet connection.

👉 **How to use:**
1. Check the **Local Models** tab to download your chosen model weights (e.g. **DeepSeek-R1 Distill 1.5B**, **Llama 3.2 1B**, **SmolLM2 135M**, or **Qwen2.5 0.5B**).
2. Once downloaded, your phone runs real on-device neural network inference directly on hardware with real-time token metrics!
3. You can also view your past conversations using **Chat History** or start fresh with **New Chat**.
                """.trimIndent(),
                modelTag = "On-Device AI"
            )
        )
    }

    fun startNewChat() {
        currentSessionId = null
        currentSessionTitle = "New Chat"
        initWelcomeMessage()
    }

    fun loadSession(session: ChatSessionEntity) {
        scope.launch {
            currentSessionId = session.id
            currentSessionTitle = session.title
            val entities = withContext(Dispatchers.IO) {
                chatDao.getMessagesForSession(session.id)
            }
            messages.clear()
            if (entities.isEmpty()) {
                initWelcomeMessage()
            } else {
                entities.forEach { entity ->
                    messages.add(
                        ChatMessage(
                            id = entity.id,
                            role = when (entity.role) {
                                "USER" -> MessageRole.USER
                                "SYSTEM" -> MessageRole.SYSTEM
                                else -> MessageRole.ASSISTANT
                            },
                            text = entity.text,
                            timestamp = entity.timestamp,
                            modelTag = entity.modelTag,
                            stats = entity.stats
                        )
                    )
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        scope.launch(Dispatchers.IO) {
            chatDao.deleteSession(sessionId)
            if (currentSessionId == sessionId) {
                withContext(Dispatchers.Main) {
                    startNewChat()
                }
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        scope.launch(Dispatchers.IO) {
            chatDao.updateSessionTitle(sessionId, newTitle)
            if (currentSessionId == sessionId) {
                withContext(Dispatchers.Main) {
                    currentSessionTitle = newTitle
                }
            }
        }
    }

    fun clearChat() {
        startNewChat()
    }

    private suspend fun getOrLoadNativeModel(file: File): LlamaModel? = withContext(Dispatchers.IO) {
        if (activeLoadedModel != null && activeLoadedPath == file.absolutePath && activeLoadedModel!!.isLoaded) {
            return@withContext activeLoadedModel
        }

        activeLoadedModel?.let {
            try {
                Llama.releaseModel(it)
            } catch (e: Throwable) {
                Log.e("OfflineAiEngine", "Error releasing previous model", e)
            }
        }
        activeLoadedModel = null
        activeLoadedPath = null

        try {
            val config = LlamaConfig(
                contextSize = 2048,
                threads = 4,
                gpuLayers = 0,
                temperature = 0.7f,
                topP = 0.9f,
                topK = 40
            )
            val model = Llama.loadModel(file.absolutePath, config)
            activeLoadedModel = model
            activeLoadedPath = file.absolutePath
            model
        } catch (e: Throwable) {
            Log.e("OfflineAiEngine", "Failed to load model from ${file.name}", e)
            null
        }
    }

    fun sendMessage(userText: String, bookContext: String? = null) {
        if (userText.isBlank() || isGenerating) return

        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            role = MessageRole.USER,
            text = userText
        )
        messages.add(userMsg)

        val activeModelInfo = modelManager.getActiveModelInfo()
        val modelFile = modelManager.getModelFile(activeModelInfo?.id ?: "")

        val modelTag = if (modelFile != null && modelFile.exists() && modelFile.length() > 1_000_000L) {
            "${activeModelInfo?.name ?: "Local"} (${activeModelInfo?.quantization ?: "GGUF"})"
        } else {
            "No Model Downloaded"
        }

        isGenerating = true
        currentStreamingText = ""

        scope.launch {
            // Check or create persistent session in Room DB
            var activeSessionId = currentSessionId
            if (activeSessionId == null) {
                activeSessionId = UUID.randomUUID().toString()
                val titleWords = userText.trim().replace("\n", " ").split(Regex("\\s+")).take(5).joinToString(" ")
                val cleanTitle = if (titleWords.length > 32) titleWords.take(32) + "…" else titleWords
                currentSessionId = activeSessionId
                currentSessionTitle = cleanTitle

                val newSession = ChatSessionEntity(
                    id = activeSessionId,
                    title = cleanTitle,
                    activeModelId = activeModelInfo?.id ?: "smollm2-135m"
                )
                withContext(Dispatchers.IO) {
                    chatDao.insertSession(newSession)
                }
            } else {
                withContext(Dispatchers.IO) {
                    chatDao.touchSession(activeSessionId)
                }
            }

            // Persist user message to Room DB
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        id = userMsgId,
                        sessionId = activeSessionId,
                        role = "USER",
                        text = userText,
                        timestamp = userMsg.timestamp
                    )
                )
            }

            val streamMessageId = UUID.randomUUID().toString()
            val placeholderMsg = ChatMessage(
                id = streamMessageId,
                role = MessageRole.ASSISTANT,
                text = "",
                modelTag = modelTag,
                isStreaming = true
            )
            messages.add(placeholderMsg)

            var responseText: String
            var statsText: String? = null

            if (modelFile != null && modelFile.exists() && modelFile.length() > 1_000_000L) {
                // Real on-device neural network inference using llama.cpp!
                try {
                    val prompt = userText.trim()
                    val sysPrompt = if (bookContext != null) {
                        "You are an offline reading companion. Use the following chapter context to answer concisely:\n\n$bookContext"
                    } else {
                        "You are a helpful, concise AI assistant running locally and privately on an Android device. Answer accurately."
                    }

                    // Show inference status in UI
                    val index = messages.indexOfFirst { it.id == streamMessageId }
                    if (index != -1) {
                        messages[index] = placeholderMsg.copy(text = "🧠 *Computing on-device inference with ${activeModelInfo?.name ?: "local model"}...*")
                    }

                    val llamaModel = getOrLoadNativeModel(modelFile)
                    if (llamaModel != null) {
                        val result = withContext(Dispatchers.IO) {
                            Llama.complete(
                                model = llamaModel,
                                prompt = prompt,
                                systemPrompt = sysPrompt,
                                maxTokens = 384
                            )
                        }
                        responseText = result.text.trim()
                        if (responseText.isBlank()) {
                            responseText = "*(No response generated by model)*"
                        }
                        statsText = "${result.tokensGenerated} tokens • ${String.format("%.1f", result.tokensPerSecond)} tok/s • ${result.generateTimeMs + result.promptEvalTimeMs}ms"
                    } else {
                        responseText = "⚠️ **Failed to load model weights into RAM.**\n\nPlease ensure your device has enough free memory (~${activeModelInfo?.ramRequiredMb ?: 300} MB) or switch to a lighter model like **SmolLM2 135M** in the Local Models tab."
                    }
                } catch (e: Throwable) {
                    Log.e("OfflineAiEngine", "LLM Inference error", e)
                    responseText = "❌ **Inference Error:** ${e.message ?: e.javaClass.simpleName}\n\nPlease verify that the model file is complete or try downloading again."
                }
            } else {
                // Model is not yet downloaded to the phone. Provide clear, direct instructions instead of fake canned replies!
                val lower = userText.trim().lowercase()
                val isGreeting = lower in listOf("hi", "hello", "hey", "who are you", "are you working", "test", "is this working")

                responseText = buildString {
                    if (isGreeting) {
                        append("👋 **Yes, Offline AI Hub is active and working!**\n\n")
                    } else {
                        append("⚠️ **Model Weights Not Found on Disk**\n\n")
                    }
                    append("You currently have **${activeModelInfo?.name ?: "SmolLM2 135M"}** selected, but its neural network file (~${activeModelInfo?.fileSizeMb ?: 92} MB) has not been downloaded to your phone yet.\n\n")
                    append("### To run real on-device AI inference:\n")
                    append("1. Tap the **Local Models** tab at the top (or the cloud icon).\n")
                    append("2. Tap **Download** next to **${activeModelInfo?.name ?: "SmolLM2 135M"}** (or choose DeepSeek-R1, Llama 3.2, or import any `.gguf` file).\n")
                    append("3. Once the download finishes, ask your question again and your device will run real neural network inference completely offline!")
                }
            }

            // Stream response to UI
            val words = responseText.split(" ")
            val partial = StringBuilder()
            for (i in words.indices) {
                partial.append(words[i])
                if (i < words.size - 1) partial.append(" ")
                currentStreamingText = partial.toString()

                val index = messages.indexOfFirst { it.id == streamMessageId }
                if (index != -1) {
                    messages[index] = placeholderMsg.copy(text = currentStreamingText)
                }

                val word = words[i]
                val delayMs = if (word.endsWith(".") || word.endsWith("\n") || word.endsWith("!")) 30L else 12L
                delay(delayMs)
            }

            val finalIndex = messages.indexOfFirst { it.id == streamMessageId }
            if (finalIndex != -1) {
                messages[finalIndex] = placeholderMsg.copy(
                    text = partial.toString(),
                    isStreaming = false,
                    stats = statsText
                )
            }

            // Persist assistant message to Room DB
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        id = streamMessageId,
                        sessionId = activeSessionId,
                        role = "ASSISTANT",
                        text = partial.toString(),
                        timestamp = System.currentTimeMillis(),
                        modelTag = modelTag,
                        stats = statsText
                    )
                )
            }

            isGenerating = false
            currentStreamingText = ""
        }
    }
}
