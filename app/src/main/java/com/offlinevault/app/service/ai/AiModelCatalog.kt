package com.offlinevault.app.service.ai

data class LocalModelInfo(
    val id: String,
    val name: String,
    val publisher: String,
    val parameterCount: String,
    val quantization: String,
    val fileSizeMb: Long,
    val ramRequiredMb: Int,
    val description: String,
    val tags: List<String>,
    val downloadUrl: String,
    val fileName: String,
    val promptFormat: String, // "ChatML", "Llama", "Smol", "DeepSeek"
    val isNew: Boolean = false
)

object AiModelCatalog {
    val curatedModels = listOf(
        // === BRAND NEW 2024-2025 CUTTING-EDGE RELEASES ===
        LocalModelInfo(
            id = "gemma-2-2b",
            name = "Gemma 2 2B Instruct",
            publisher = "Google DeepMind",
            parameterCount = "2.6B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 1630,
            ramRequiredMb = 2100,
            description = "Google's breakthrough compact architecture. Outperforms many larger models on reasoning, instruction following, and factual knowledge.",
            tags = listOf("New", "Google", "DeepMind", "Top Rated"),
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            promptFormat = "ChatML",
            isNew = true
        ),
        LocalModelInfo(
            id = "phi-3.5-mini",
            name = "Phi-3.5 Mini Instruct (128K)",
            publisher = "Microsoft Research",
            parameterCount = "3.8B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 2390,
            ramRequiredMb = 2800,
            description = "Microsoft's state-of-the-art 3.8B model with massive 128K context window. Superb mathematical reasoning, multi-document synthesis, and logic.",
            tags = listOf("New", "Microsoft", "128K Context", "Reasoning"),
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            promptFormat = "ChatML",
            isNew = true
        ),
        LocalModelInfo(
            id = "deepseek-r1-1.5b",
            name = "DeepSeek-R1 Distill 1.5B",
            publisher = "DeepSeek AI",
            parameterCount = "1.5B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 1117,
            ramRequiredMb = 1500,
            description = "State-of-the-art chain-of-thought reasoning model distilled from DeepSeek-R1. Exceptional at logic, complex book questions, and problem-solving.",
            tags = listOf("New", "Reasoning", "Chain of Thought", "Top Rated"),
            downloadUrl = "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            promptFormat = "ChatML",
            isNew = true
        ),
        LocalModelInfo(
            id = "llama-3.2-1b",
            name = "Llama 3.2 1B Instruct",
            publisher = "Meta AI",
            parameterCount = "1.2B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 750,
            ramRequiredMb = 1100,
            description = "Meta's latest ultra-efficient compact model built specifically for mobile edge computing. Fast, fluent, and highly knowledgeable.",
            tags = listOf("New", "Meta Official", "Mobile Edge", "Fast"),
            downloadUrl = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            promptFormat = "Llama",
            isNew = true
        ),
        LocalModelInfo(
            id = "qwen2.5-1.5b",
            name = "Qwen 2.5 1.5B Instruct",
            publisher = "Alibaba Qwen",
            parameterCount = "1.5B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 986,
            ramRequiredMb = 1400,
            description = "Alibaba's benchmark-topping small model. Outstanding at coding, math, structured markdown note-taking, and multilingual tasks.",
            tags = listOf("New", "High Intelligence", "Coding & Math"),
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            promptFormat = "ChatML",
            isNew = true
        ),
        LocalModelInfo(
            id = "smollm2-1.7b",
            name = "SmolLM2 1.7B Instruct",
            publisher = "Hugging Face",
            parameterCount = "1.7B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 1050,
            ramRequiredMb = 1450,
            description = "Hugging Face's flagship small language model, trained on curated synthetic data and educational content. Excellent offline tutor.",
            tags = listOf("New", "Hugging Face", "Education", "Creative"),
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            fileName = "smollm2-1.7b-instruct-q4_k_m.gguf",
            promptFormat = "ChatML",
            isNew = true
        ),
        LocalModelInfo(
            id = "llama-3.2-3b",
            name = "Llama 3.2 3B Instruct",
            publisher = "Meta AI",
            parameterCount = "3.2B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 1920,
            ramRequiredMb = 2400,
            description = "Meta's flagship high-capability on-device model. Unmatched depth for a mobile model, best suited for Galaxy Fold Snapdragon hardware.",
            tags = listOf("New", "Meta Official", "Max Capability", "Deep Chat"),
            downloadUrl = "https://huggingface.co/unsloth/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            promptFormat = "Llama",
            isNew = true
        ),

        // === ULTRA-LIGHTWEIGHT & MOBILE SPECIALIZED MODELS ===
        LocalModelInfo(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B Instruct",
            publisher = "Alibaba Qwen",
            parameterCount = "0.5B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 395,
            ramRequiredMb = 750,
            description = "High intelligence per megabyte. Superior multi-language comprehension, math, and structured markdown output under 400MB.",
            tags = listOf("Lightweight", "Markdown", "Multi-Language"),
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            promptFormat = "ChatML"
        ),
        LocalModelInfo(
            id = "smollm2-360m",
            name = "SmolLM2 360M Instruct",
            publisher = "Hugging Face",
            parameterCount = "360M",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 238,
            ramRequiredMb = 480,
            description = "Remarkable reasoning and text structuring for under 250MB. Great for book questions, analysis, and drafting study notes.",
            tags = listOf("Balanced", "Book Notes", "Reasoning"),
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
            fileName = "smollm2-360m-instruct-q4_k_m.gguf",
            promptFormat = "ChatML"
        ),
        LocalModelInfo(
            id = "smollm2-135m",
            name = "SmolLM2 135M Instruct",
            publisher = "Hugging Face",
            parameterCount = "135M",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 92,
            ramRequiredMb = 220,
            description = "Ultra-compact mobile model. Lightning fast inference, minimal battery drain, excellent for instant offline summaries and definitions.",
            tags = listOf("Ultra-Fast", "Minimal RAM", "Summaries"),
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
            fileName = "smollm2-135m-instruct-q4_k_m.gguf",
            promptFormat = "ChatML"
        ),
        LocalModelInfo(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B Chat",
            publisher = "TinyLlama Project",
            parameterCount = "1.1B",
            quantization = "Q4_K_M GGUF",
            fileSizeMb = 669,
            ramRequiredMb = 1400,
            description = "Standard 1.1B parameter offline conversational assistant. Trained on 3 trillion tokens for natural dialogues.",
            tags = listOf("Chat", "3T Tokens", "Conversational"),
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            promptFormat = "Llama"
        )
    )

    fun getById(id: String): LocalModelInfo? = curatedModels.find { it.id == id }
}
