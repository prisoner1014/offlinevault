package com.offlinevault.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.preferences.AppSettings
import com.offlinevault.app.data.preferences.SettingsRepository
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.ui.theme.AppThemeMode
import com.offlinevault.app.util.AppVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val settings by repo.settingsFlow.collectAsState(initial = AppSettings())
    val storageTracker = remember { StorageTracker(context) }

    var showClearAllDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Preferences",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Personalize appearance, storage limits, and download behavior",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Appearance & Dark Mode
        item {
            Text(
                text = "🎨 Appearance & Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme Mode", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Dark Mode includes high-contrast OLED black for Samsung Galaxy Fold power saving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    scope.launch { repo.updateThemeMode(mode) }
                                },
                                label = {
                                    Text(
                                        when (mode) {
                                            AppThemeMode.SYSTEM -> "System"
                                            AppThemeMode.DARK -> "Dark (OLED)"
                                            AppThemeMode.LIGHT -> "Light"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        when (mode) {
                                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                            AppThemeMode.DARK -> Icons.Default.DarkMode
                                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Quality of Life & Automation
        item {
            Text(
                text = "⚡ Quality of Life & Automation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Clipboard Detection", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Auto-detect copied video/audio URLs on app launch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.quickClipboardDetection,
                            onCheckedChange = { checked ->
                                scope.launch { repo.updateQuickClipboard(checked) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Retry on Reconnection", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Automatically resume queued/failed downloads when network reconnects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoRetryOnConnection,
                            onCheckedChange = { checked ->
                                scope.launch { repo.updateAutoRetry(checked) }
                            }
                        )
                    }
                }
            }
        }

        // Network & Auto-Download Preferences
        item {
            Text(
                text = "📶 Network & Data Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Download on Mobile Data", fontWeight = FontWeight.SemiBold)
                            Text(
                                "When enabled, media and podcasts can sync over cellular data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.allowMobileDataAutoDownload,
                            onCheckedChange = { checked ->
                                scope.launch { repo.updateAllowMobileData(checked) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cellular File Size Limit", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Current cap: ${settings.mobileDataMaxFileSizeMb} MB per item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            listOf(50, 100, 250).forEach { mb ->
                                FilterChip(
                                    selected = settings.mobileDataMaxFileSizeMb == mb,
                                    onClick = { scope.launch { repo.updateMobileDataMaxMb(mb) } },
                                    label = { Text("${mb}MB") },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Web Browser & Data Saver Preferences
        item {
            Text(
                text = "🌐 Browser & Privacy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aggressive Ad & Tracker Blocking", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Blocks telemetry and ad requests to minimize data usage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.adBlockEnabled,
                            onCheckedChange = { checked ->
                                scope.launch { repo.updateAdBlockEnabled(checked) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block Images by Default (Data Saver)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Strips web images to load web pages with near-zero data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.blockImagesDataSaver,
                            onCheckedChange = { checked ->
                                scope.launch { repo.updateBlockImages(checked) }
                            }
                        )
                    }
                }
            }
        }

        // About & Version Serial Number
        item {
            Text(
                text = "ℹ️ About & Version Serial Number",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Vault v${AppVersionInfo.CURRENT_VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Build #${AppVersionInfo.CURRENT_VERSION_CODE} • ${AppVersionInfo.BUILD_CHANNEL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "LATEST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Serial: ${AppVersionInfo.CURRENT_SERIAL_NUMBER}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToAbout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Full About & Diagnostics Page")
                    }
                }
            }
        }

        // Cloud & Local Vault Backup
        item {
            BackupRestoreSection()
        }

        // Storage & Isolation Info
        item {
            Text(
                text = "🔒 Storage Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Storage Root:", style = MaterialTheme.typography.labelSmall)
                    Text(
                        storageTracker.vaultRootDir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearAllDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Offline Vault Data")
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Erase All Offline Data?") },
            text = { Text("This will permanently delete all downloaded videos, podcasts, and saved web archives from your private vault.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                storageTracker.clearCache()
                                storageTracker.deleteCategoryContent(com.offlinevault.app.data.storage.StorageCategory.VIDEOS)
                                storageTracker.deleteCategoryContent(com.offlinevault.app.data.storage.StorageCategory.PODCASTS)
                                storageTracker.deleteCategoryContent(com.offlinevault.app.data.storage.StorageCategory.WEB_ARCHIVES)
                                db.clearAllTables()
                            }
                            showClearAllDialog = false
                            Toast.makeText(context, "All Vault Media & Database Cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
