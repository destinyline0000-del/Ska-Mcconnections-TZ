package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.OfflineMovieItem
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class InstallBannerSettings(
    val bannerEnabled: Boolean = true,
    val bannerDismissed: Boolean = false,
    val apkDownloadUrl: String = "",
    val webPortalUrl: String = ""
)

private val Context.skaDataStore: DataStore<Preferences> by preferencesDataStore(name = "ska_mac_prefs")

class LocalSecretsAndDownloadsStore(private val context: Context) {

    private val langKey = stringPreferencesKey("preferred_language")
    private val offlineKey = stringPreferencesKey("offline_movies_json")
    private val githubPatKey = stringPreferencesKey("github_pat_local")
    private val installAdEnabledKey = booleanPreferencesKey("install_ad_enabled")
    private val installAdDismissedKey = booleanPreferencesKey("install_ad_dismissed")
    private val apkDownloadUrlKey = stringPreferencesKey("apk_download_url")
    private val webPortalUrlKey = stringPreferencesKey("web_portal_url")

    private fun driveTokenKey(slot: Int) = stringPreferencesKey("drive_oauth_token_$slot")

    val installBannerSettingsFlow: Flow<InstallBannerSettings> = context.skaDataStore.data.map { prefs ->
        InstallBannerSettings(
            bannerEnabled = prefs[installAdEnabledKey] ?: true,
            bannerDismissed = prefs[installAdDismissedKey] ?: false,
            apkDownloadUrl = prefs[apkDownloadUrlKey].orEmpty(),
            webPortalUrl = prefs[webPortalUrlKey].orEmpty()
        )
    }

    suspend fun setInstallAdDismissed(dismissed: Boolean) {
        context.skaDataStore.edit { prefs ->
            prefs[installAdDismissedKey] = dismissed
        }
    }

    suspend fun saveInstallBannerConfig(enabled: Boolean, apkUrl: String, webUrl: String) {
        context.skaDataStore.edit { prefs ->
            prefs[installAdEnabledKey] = enabled
            if (enabled) prefs[installAdDismissedKey] = false
            prefs[apkDownloadUrlKey] = apkUrl.trim()
            prefs[webPortalUrlKey] = webUrl.trim()
        }
    }

    val languageFlow: Flow<AppLanguage> = context.skaDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[langKey])
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.skaDataStore.edit { prefs ->
            prefs[langKey] = language.code
        }
    }

    val offlineMoviesFlow: Flow<List<OfflineMovieItem>> = context.skaDataStore.data.map { prefs ->
        parseOfflineMovies(prefs[offlineKey].orEmpty())
    }

    suspend fun saveOfflineMovie(item: OfflineMovieItem) {
        context.skaDataStore.edit { prefs ->
            val current = parseOfflineMovies(prefs[offlineKey].orEmpty())
                .filterNot { it.movieId == item.movieId }
                .toMutableList()
            current.add(0, item)
            prefs[offlineKey] = serializeOfflineMovies(current)
        }
    }

    suspend fun removeOfflineMovie(movieId: String) {
        context.skaDataStore.edit { prefs ->
            val updated = parseOfflineMovies(prefs[offlineKey].orEmpty())
                .filterNot { it.movieId == movieId }
            prefs[offlineKey] = serializeOfflineMovies(updated)
        }
    }

    suspend fun saveDriveToken(slotNumber: Int, token: String) {
        require(slotNumber in 1..5) { "Drive slot must be between 1 and 5" }
        context.skaDataStore.edit { prefs ->
            if (token.isBlank()) prefs.remove(driveTokenKey(slotNumber))
            else prefs[driveTokenKey(slotNumber)] = token.trim()
        }
    }

    suspend fun getDriveToken(slotNumber: Int): String? {
        if (slotNumber !in 1..5) return null
        return context.skaDataStore.data.first()[driveTokenKey(slotNumber)]
    }

    suspend fun saveGitHubToken(token: String) {
        context.skaDataStore.edit { prefs ->
            if (token.isBlank()) prefs.remove(githubPatKey)
            else prefs[githubPatKey] = token.trim()
        }
    }

    suspend fun getGitHubToken(): String? {
        return context.skaDataStore.data.first()[githubPatKey]
    }

    private fun parseOfflineMovies(rawJson: String): List<OfflineMovieItem> {
        if (rawJson.isBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        OfflineMovieItem(
                            movieId = obj.optString("movieId"),
                            title = obj.optString("title"),
                            description = obj.optString("description"),
                            tier = obj.optString("tier", "FREE"),
                            genre = obj.optString("genre", "Cinema"),
                            localFilePath = obj.optString("localFilePath"),
                            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                            downloadedAtMillis = obj.optLong("downloadedAtMillis", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeOfflineMovies(items: List<OfflineMovieItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("movieId", item.movieId)
                    put("title", item.title)
                    put("description", item.description)
                    put("tier", item.tier)
                    put("genre", item.genre)
                    put("localFilePath", item.localFilePath)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("downloadedAtMillis", item.downloadedAtMillis)
                }
            )
        }
        return array.toString()
    }
}
