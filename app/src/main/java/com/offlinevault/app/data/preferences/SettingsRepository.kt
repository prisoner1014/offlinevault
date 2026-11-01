package com.offlinevault.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.offlinevault.app.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vault_settings")

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val allowMobileDataAutoDownload: Boolean = true,
    val mobileDataMaxFileSizeMb: Int = 100,
    val adBlockEnabled: Boolean = true,
    val blockImagesDataSaver: Boolean = false,
    val autoCleanupDays: Int = 14,
    val quickClipboardDetection: Boolean = true,
    val autoRetryOnConnection: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data_auto_download")
        val MOBILE_DATA_MAX_MB = intPreferencesKey("mobile_data_max_file_size_mb")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val BLOCK_IMAGES = booleanPreferencesKey("block_images_data_saver")
        val AUTO_CLEANUP_DAYS = intPreferencesKey("auto_cleanup_days")
        val QUICK_CLIPBOARD = booleanPreferencesKey("quick_clipboard_detection")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry_on_connection")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferencesKeys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val themeMode = try {
            AppThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }

        AppSettings(
            themeMode = themeMode,
            allowMobileDataAutoDownload = preferences[PreferencesKeys.ALLOW_MOBILE_DATA] ?: true,
            mobileDataMaxFileSizeMb = preferences[PreferencesKeys.MOBILE_DATA_MAX_MB] ?: 100,
            adBlockEnabled = preferences[PreferencesKeys.AD_BLOCK_ENABLED] ?: true,
            blockImagesDataSaver = preferences[PreferencesKeys.BLOCK_IMAGES] ?: false,
            autoCleanupDays = preferences[PreferencesKeys.AUTO_CLEANUP_DAYS] ?: 14,
            quickClipboardDetection = preferences[PreferencesKeys.QUICK_CLIPBOARD] ?: true,
            autoRetryOnConnection = preferences[PreferencesKeys.AUTO_RETRY] ?: true
        )
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateAllowMobileData(allow: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOW_MOBILE_DATA] = allow
        }
    }

    suspend fun updateMobileDataMaxMb(maxMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOBILE_DATA_MAX_MB] = maxMb
        }
    }

    suspend fun updateAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AD_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun updateBlockImages(block: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLOCK_IMAGES] = block
        }
    }

    suspend fun updateAutoCleanupDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CLEANUP_DAYS] = days
        }
    }

    suspend fun updateQuickClipboard(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUICK_CLIPBOARD] = enabled
        }
    }

    suspend fun updateAutoRetry(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RETRY] = enabled
        }
    }

    suspend fun exportSettingsJson(): String {
        val settings = settingsFlow.first()
        val json = org.json.JSONObject().apply {
            put("themeMode", settings.themeMode.name)
            put("allowMobileDataAutoDownload", settings.allowMobileDataAutoDownload)
            put("mobileDataMaxFileSizeMb", settings.mobileDataMaxFileSizeMb)
            put("adBlockEnabled", settings.adBlockEnabled)
            put("blockImagesDataSaver", settings.blockImagesDataSaver)
            put("autoCleanupDays", settings.autoCleanupDays)
            put("quickClipboardDetection", settings.quickClipboardDetection)
            put("autoRetryOnConnection", settings.autoRetryOnConnection)
        }
        return json.toString(2)
    }

    suspend fun restoreSettingsFromJson(jsonStr: String) {
        val json = org.json.JSONObject(jsonStr)
        if (json.has("themeMode")) {
            val modeStr = json.getString("themeMode")
            val mode = try { AppThemeMode.valueOf(modeStr) } catch (_: Exception) { AppThemeMode.SYSTEM }
            updateThemeMode(mode)
        }
        if (json.has("allowMobileDataAutoDownload")) updateAllowMobileData(json.getBoolean("allowMobileDataAutoDownload"))
        if (json.has("mobileDataMaxFileSizeMb")) updateMobileDataMaxMb(json.getInt("mobileDataMaxFileSizeMb"))
        if (json.has("adBlockEnabled")) updateAdBlockEnabled(json.getBoolean("adBlockEnabled"))
        if (json.has("blockImagesDataSaver")) updateBlockImages(json.getBoolean("blockImagesDataSaver"))
        if (json.has("autoCleanupDays")) updateAutoCleanupDays(json.getInt("autoCleanupDays"))
        if (json.has("quickClipboardDetection")) updateQuickClipboard(json.getBoolean("quickClipboardDetection"))
        if (json.has("autoRetryOnConnection")) updateAutoRetry(json.getBoolean("autoRetryOnConnection"))
    }
}
