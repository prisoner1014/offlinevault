package com.offlinevault.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinevault.app.data.backup.BackupOptions
import com.offlinevault.app.data.backup.BackupStats
import com.offlinevault.app.data.backup.VaultBackupManager
import com.offlinevault.app.data.backup.VaultBackupManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { VaultBackupManager(context) }

    var backupStats by remember { mutableStateOf<BackupStats?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf<Pair<Uri, VaultBackupManifest>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatusText by remember { mutableStateOf("") }
    var completionMessage by remember { mutableStateOf<String?>(null) }

    // Backup Options State
    var includeDatabase by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(true) }
    var includeBooks by remember { mutableStateOf(true) }
    var includeArchives by remember { mutableStateOf(true) }
    var includePodcasts by remember { mutableStateOf(false) }
    var includeVideos by remember { mutableStateOf(false) }

    // Load stats whenever the export dialog is triggered
    LaunchedEffect(showExportDialog) {
        if (showExportDialog) {
            withContext(Dispatchers.IO) {
                backupStats = backupManager.calculateStats()
            }
        }
    }

    // Export Document Launcher (SAF CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            val options = BackupOptions(
                includeDatabase = includeDatabase,
                includeSettings = includeSettings,
                includeBooks = includeBooks,
                includeWebArchives = includeArchives,
                includePodcasts = includePodcasts,
                includeVideos = includeVideos
            )
            isProcessing = true
            progressFraction = 0.05f
            progressStatusText = "Preparing export..."

            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val outputStream = context.contentResolver.openOutputStream(uri)
                            ?: return@withContext Result.failure(IllegalStateException("Could not open destination stream"))
                        backupManager.exportBackup(outputStream, options) { progress, status ->
                            scope.launch(Dispatchers.Main) {
                                progressFraction = progress
                                progressStatusText = status
                            }
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                isProcessing = false
                if (result.isSuccess) {
                    val manifest = result.getOrNull()
                    completionMessage = "Backup successfully exported!\n\nIncluded: ${manifest?.entityCounts?.get("books") ?: 0} Books, " +
                            "${manifest?.entityCounts?.get("archives") ?: 0} Web Archives, " +
                            "${manifest?.entityCounts?.get("feeds") ?: 0} Podcast Feeds."
                } else {
                    Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Restore Document Launcher (SAF OpenDocument)
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            progressFraction = 0.1f
            progressStatusText = "Inspecting backup archive..."

            scope.launch {
                val manifest = withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                        backupManager.inspectBackup(inputStream)
                    } catch (e: Exception) {
                        null
                    }
                }
                isProcessing = false

                if (manifest != null) {
                    showRestoreConfirmDialog = uri to manifest
                } else {
                    Toast.makeText(context, "Invalid backup file: could not read manifest", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "☁️ Cloud & Local Backup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Backup to Google Drive or Dropbox",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Create a universal .vaultbackup archive to easily sink into Google Drive, Dropbox, or device storage. Restore on any phone at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup")
                    }

                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Backup")
                    }
                }
            }
        }
    }

    // Export Configuration Dialog
    if (showExportDialog) {
        val stats = backupStats
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val defaultFilename = "OfflineVault_Backup_$dateStr.vaultbackup"

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Vault Backup") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choose which vault data you want to package into your cloud backup file:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // Database & Settings (Always Recommended)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeDatabase, onCheckedChange = { includeDatabase = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Database & State", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Reading progress, bookmarks, podcasts, downloads index", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Books & Documents
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeBooks, onCheckedChange = { includeBooks = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Books & Documents", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${stats?.booksCount ?: 0} items • ${formatBackupBytes(stats?.booksSizeBytes ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Web Archives
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeArchives, onCheckedChange = { includeArchives = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Web Archives & Articles", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${stats?.archivesCount ?: 0} articles • ${formatBackupBytes(stats?.archivesSizeBytes ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Podcasts Audio (Optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includePodcasts, onCheckedChange = { includePodcasts = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Podcast Episodes Audio", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatBackupBytes(stats?.podcastsSizeBytes ?: 0L)} audio files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Videos (Optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeVideos, onCheckedChange = { includeVideos = it })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Videos & Heavy Media", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatBackupBytes(stats?.videosSizeBytes ?: 0L)} video files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        exportLauncher.launch(defaultFilename)
                    }
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Destination")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Confirmation Dialog
    showRestoreConfirmDialog?.let { (uri, manifest) ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = null },
            title = { Text("Restore Vault Backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Found backup created on ${manifest.createdAtFormatted} (App v${manifest.appVersionName}):")

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Database & State: ${if (manifest.includesDatabase) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                            Text("• App Settings: ${if (manifest.includesSettings) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Books: ${manifest.entityCounts["books"] ?: 0} items", style = MaterialTheme.typography.bodySmall)
                            Text("• Web Archives: ${manifest.entityCounts["archives"] ?: 0} articles", style = MaterialTheme.typography.bodySmall)
                            Text("• Podcast Feeds: ${manifest.entityCounts["feeds"] ?: 0} feeds", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Text(
                        "Restoring will import all contained database records, reading progress, and media into your vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = null
                        isProcessing = true
                        progressFraction = 0.05f
                        progressStatusText = "Restoring vault..."

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val inStream = context.contentResolver.openInputStream(uri)
                                        ?: return@withContext Result.failure(IllegalStateException("Cannot open backup file"))
                                    backupManager.restoreBackup(inStream) { progress, status ->
                                        scope.launch(Dispatchers.Main) {
                                            progressFraction = progress
                                            progressStatusText = status
                                        }
                                    }
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                            }
                            isProcessing = false
                            if (result.isSuccess) {
                                completionMessage = "Vault restored successfully!\nAll books, web archives, reading progress, and database items have been updated."
                            } else {
                                Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // In-Progress Modal Dialog
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissible while processing */ },
            title = { Text("Processing Vault Backup") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = progressStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {}
        )
    }

    // Completion / Success Dialog
    completionMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { completionMessage = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Vault Operation Complete") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = { completionMessage = null }) {
                    Text("Done")
                }
            }
        )
    }
}

private fun formatBackupBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}
