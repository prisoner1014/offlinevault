package com.offlinevault.app.ui.screens.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offlinevault.app.data.storage.StorageBreakdown
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.util.AppVersionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val storageTracker = remember { StorageTracker(context) }
    var storageBreakdown by remember { mutableStateOf<StorageBreakdown?>(null) }

    LaunchedEffect(Unit) {
        storageBreakdown = storageTracker.calculateStorageBreakdown()
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Branding Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = AppVersionInfo.APP_NAME,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppVersionInfo.APP_TAGLINE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("v${AppVersionInfo.CURRENT_VERSION_NAME}", fontWeight = FontWeight.Bold) }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Build #${AppVersionInfo.CURRENT_VERSION_CODE}") }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(AppVersionInfo.BUILD_CHANNEL) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Serial Number Box with 1-Tap Copy
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OFFICIAL RELEASE SERIAL NUMBER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = AppVersionInfo.CURRENT_SERIAL_NUMBER,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard("Serial Number", AppVersionInfo.CURRENT_SERIAL_NUMBER) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Serial", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // Device Diagnostics & Hardware Info
        item {
            Text(
                text = "📱 Device & Native Hardware Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticRow(
                        label = "Connected Device",
                        value = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "Android OS",
                        value = "Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "Form Factor",
                        value = "Foldable Dual-Screen Layout (Samsung Galaxy Fold Optimized)"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "Native CPU Architecture",
                        value = "${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"} (64-bit Native Binaries)"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "On-Device Neural Engine",
                        value = AppVersionInfo.NEURAL_ENGINE
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "Offline Vault Storage Used",
                        value = StorageBreakdown.formatBytes(storageBreakdown?.totalAppUsedBytes ?: 0L)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DiagnosticRow(
                        label = "Device Free Storage",
                        value = StorageBreakdown.formatBytes(storageBreakdown?.freeDeviceBytes ?: 0L)
                    )
                }
            }
        }

        // Core Capabilities Architecture
        item {
            Text(
                text = "⚡ Built-in Offline Engines",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineFeatureCard(
                    icon = Icons.Default.Psychology,
                    title = "Local Neural AI Assistant",
                    description = "11 quantized GGUF models (Gemma 2 2B, Phi-3.5 Mini 128K, DeepSeek-R1, Llama 3.2, Qwen 2.5, SmolLM2). 100% offline neural inference with zero cloud telemetry."
                )
                EngineFeatureCard(
                    icon = Icons.Default.MenuBook,
                    title = "Multi-Repository Book Hub",
                    description = "Polls and downloads from Standard Ebooks (curated modern typography), Project Gutenberg (70k+ catalog), and Open Library with built-in EPUB reader and Text-To-Speech audio."
                )
                EngineFeatureCard(
                    icon = Icons.Default.Podcasts,
                    title = "Podcasts & Audio Compressor",
                    description = "RSS podcast synchronization with Opus voice compression to shrink speech audio up to 80% without losing vocal clarity."
                )
                EngineFeatureCard(
                    icon = Icons.Default.Language,
                    title = "Zero-Data Privacy Browser",
                    description = "Integrated web browser with aggressive ad, tracker, and script blocking, plus full web archive saving for offline reading."
                )
                EngineFeatureCard(
                    icon = Icons.Default.SportsEsports,
                    title = "Offline Arcade (10 Built-In Games)",
                    description = "10 fully offline games with retro synth audio and zero internet required: 2048, Retro Snake, Block Drop, Minesweeper, Sudoku Master, Cyber Glider, Klondike Solitaire, Connect 4 & Tic-Tac-Toe, Memory Matrix, and Neon Breakout."
                )
                EngineFeatureCard(
                    icon = Icons.Default.Download,
                    title = "Universal Media Downloader",
                    description = "High-speed video and audio download engine with yt-dlp compatibility for persistent offline preservation."
                )
            }
        }

        // Version History & Serial Numbers
        item {
            Text(
                text = "📜 Version Release History & Serial Numbers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(AppVersionInfo.versionHistory) { release ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (release.versionName == AppVersionInfo.CURRENT_VERSION_NAME)
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v${release.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Build ${release.versionCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (release.versionName == AppVersionInfo.CURRENT_VERSION_NAME) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "CURRENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = release.releaseDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Serial: ${release.serialNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { copyToClipboard("Serial Number", release.serialNumber) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    release.highlights.forEach { highlight ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(
                                text = highlight,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Open Source & Privacy Acknowledgements
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 Privacy & Open Source Commitment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Offline Vault is built with 100% user privacy as the core priority. It requires no user account, transmits zero telemetry, collects no personal identifiers, and is fully capable of running completely detached from the internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Special thanks to the open repositories and engines that make this possible: Project Gutenberg, Standard Ebooks, Open Library, Internet Archive, Georgi Gerganov & llama.cpp, Unsloth, Meta AI, Google DeepMind, Microsoft Research, Alibaba Qwen, Hugging Face, yt-dlp, and FFmpeg.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EngineFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
