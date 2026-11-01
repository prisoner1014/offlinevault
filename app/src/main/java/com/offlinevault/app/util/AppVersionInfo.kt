package com.offlinevault.app.util

import android.os.Build

data class VersionRelease(
    val versionName: String,
    val versionCode: Int,
    val serialNumber: String,
    val releaseDate: String,
    val highlights: List<String>
)

object AppVersionInfo {
    const val APP_NAME = "Offline Vault"
    const val APP_TAGLINE = "All-in-One Private Offline Knowledge & Media Suite"
    const val CURRENT_VERSION_NAME = "1.3.0"
    const val CURRENT_VERSION_CODE = 8
    const val CURRENT_SERIAL_NUMBER = "OV-20261031-ARCADE-ARM64"
    const val BUILD_CHANNEL = "Production / Fold Edition"
    const val BUILD_DATE = "October 31, 2026"
    const val TARGET_HARDWARE = "Samsung Galaxy Fold & Foldable Architecture"
    const val PRIMARY_ABI = "arm64-v8a (64-bit Native Execution)"
    const val NEURAL_ENGINE = "llama.cpp v0.1.1 (libllama.so + libllama_jni.so)"

    val versionHistory = listOf(
        VersionRelease(
            versionName = "1.3.0",
            versionCode = 8,
            serialNumber = "OV-20261031-ARCADE-ARM64",
            releaseDate = "October 31, 2026",
            highlights = listOf(
                "Universal system back gesture handling across all screens, modals, full-screen video player, book reader, and browser history.",
                "Built-in Offline Arcade with 10 classic games: 2048, Retro Snake, Block Drop, Minesweeper, Sudoku Master, Cyber Glider, Klondike Solitaire, Connect 4 & Tic-Tac-Toe, Memory Matrix, and Neon Breakout.",
                "Self-contained 60 FPS HTML5 Canvas + Web Audio API synthesizer for offline retro sound effects.",
                "Added Games navigation rail / bottom tab and 1-tap quick access banner in Vault."
            )
        ),
        VersionRelease(
            versionName = "1.2.0",
            versionCode = 7,
            serialNumber = "OV-20261031-7EA1A83C-ARM64",
            releaseDate = "October 31, 2026",
            highlights = listOf(
                "Added Gemma 2 2B Instruct (Google DeepMind) & Phi-3.5 Mini Instruct 128K (Microsoft Research) to on-device AI hub.",
                "Multi-repository book polling: Standard Ebooks, Project Gutenberg (70k+ catalog), and Open Library / Internet Archive.",
                "Genre & topic filter chips with color-coded repository source badges and direct EPUB downloading.",
                "Dedicated About & System Inspection page with version serial number tracking and device hardware diagnostics."
            )
        ),
        VersionRelease(
            versionName = "1.1.0",
            versionCode = 6,
            serialNumber = "OV-20261030-AC0A3DFD-ARM64",
            releaseDate = "October 30, 2026",
            highlights = listOf(
                "Integrated real on-device neural network execution via native llama.cpp arm64-v8a binaries.",
                "Persistent Room DB chat sessions with auto-naming, chat history drawer, and real-time token/sec metrics.",
                "Added DeepSeek-R1 Distill 1.5B, Llama 3.2 1B & 3B, Qwen 2.5, and SmolLM2 models."
            )
        ),
        VersionRelease(
            versionName = "1.0.0",
            versionCode = 1,
            serialNumber = "OV-20261029-BASEINIT-ARM64",
            releaseDate = "October 29, 2026",
            highlights = listOf(
                "Initial release featuring Universal Media Downloader, low-data podcast engine with Opus voice compression.",
                "EPUB/PDF reader with Samsung Fold two-page spread support and text-to-speech audio reader.",
                "Zero-data web browser with aggressive ad and telemetry blocking."
            )
        )
    )

    fun getDeviceSummary(): String {
        return "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
    }
}
