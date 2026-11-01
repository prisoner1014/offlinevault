package com.offlinevault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.offlinevault.app.data.preferences.AppSettings
import com.offlinevault.app.data.preferences.SettingsRepository
import com.offlinevault.app.ui.navigation.AdaptiveNavScaffold
import com.offlinevault.app.ui.theme.OfflineVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming shared URLs from other applications
        val sharedUrl = extractSharedUrl(intent)

        setContent {
            val settingsRepo = remember { SettingsRepository(this@MainActivity) }
            val settings by settingsRepo.settingsFlow.collectAsState(initial = AppSettings())

            OfflineVaultTheme(themeMode = settings.themeMode) {
                Surface {
                    AdaptiveNavScaffold(sharedUrl = sharedUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                // Look for http/https URL within the shared text
                val urlRegex = "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)".toRegex()
                val match = urlRegex.find(sharedText)
                return match?.value ?: sharedText.trim()
            }
        }
        return null
    }
}
