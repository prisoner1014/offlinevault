package com.offlinevault.app.ui.screens.downloader

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinevault.app.service.download.YouTubeAuthManager

@Composable
fun ImportCookiesDialog(
    authManager: YouTubeAuthManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var pastedText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val success = authManager.importFromUri(uri)
            if (success) {
                Toast.makeText(context, "cookies.txt imported successfully! \uD83C\uDF89", Toast.LENGTH_LONG).show()
                onSuccess()
            } else {
                Toast.makeText(context, "Failed to import cookies.txt. Please verify file format.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import cookies.txt")
            }
        },
        text = {
            Column {
                Text(
                    text = "Import Netscape-formatted cookies exported from your browser (e.g. using 'Get cookies.txt LOCALLY').",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { filePickerLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Browse & Select cookies.txt",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Or paste raw cookie text below:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = { Text("# Netscape HTTP Cookie File...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pastedText.isNotBlank()) {
                        val success = if (pastedText.contains("\t") || pastedText.startsWith("#")) {
                            authManager.saveNetscapeText(pastedText, "Pasted cookies.txt")
                        } else {
                            authManager.saveFromRawCookieString(pastedText, "Pasted raw cookies")
                        }
                        if (success) {
                            Toast.makeText(context, "Cookies saved! Memberships Active \u2705", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } else {
                            Toast.makeText(context, "Could not parse cookies. Please check format.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = pastedText.isNotBlank()
            ) {
                Text("Save Pasted")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
