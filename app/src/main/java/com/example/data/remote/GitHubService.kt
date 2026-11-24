package com.example.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubRepoStatus(
    val owner: String,
    val repo: String,
    val branch: String,
    val latestCommitSha: String,
    val latestCommitMessage: String,
    val remoteVersion: String,
    val updateNotes: String
)

class GitHubService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun pullRepoUpdates(
        owner: String,
        repo: String,
        branch: String,
        token: String?
    ): Result<GitHubRepoStatus> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            val cleanBranch = branch.trim().ifBlank { "main" }

            val commitReqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$cleanOwner/$cleanRepo/commits/$cleanBranch")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "SkaMacconnectionsTZ-Android")
            if (!token.isNullOrBlank()) {
                commitReqBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
            }

            val commitJson = client.newCall(commitReqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = try {
                        JSONObject(body).optString("message").ifBlank { "GitHub API ${resp.code}" }
                    } catch (_: Exception) {
                        "GitHub API ${resp.code}"
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                JSONObject(body)
            }

            val sha = commitJson.optString("sha").take(12)
            val commitMsg = commitJson.optJSONObject("commit")?.optString("message")?.lines()?.firstOrNull()
                ?: "Synced from $cleanBranch"

            // Optional remote manifest check: ska_updates.json
            var remoteVersion = "1.0.0-$sha"
            var notes = "Synced commit $sha on $cleanBranch"
            val manifestReq = Request.Builder()
                .url("https://api.github.com/repos/$cleanOwner/$cleanRepo/contents/ska_updates.json?ref=$cleanBranch")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "SkaMacconnectionsTZ-Android")
                .apply {
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer ${token.trim()}")
                }
                .get()
                .build()

            client.newCall(manifestReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentBody = resp.body?.string().orEmpty()
                    val contentObj = JSONObject(contentBody)
                    val encoded = contentObj.optString("content").replace("\n", "")
                    if (encoded.isNotBlank()) {
                        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                        val manifestJson = JSONObject(decoded)
                        remoteVersion = manifestJson.optString("version", remoteVersion)
                        notes = manifestJson.optString("notes", notes)
                    }
                }
            }

            Result.success(
                GitHubRepoStatus(
                    owner = cleanOwner,
                    repo = cleanRepo,
                    branch = cleanBranch,
                    latestCommitSha = sha,
                    latestCommitMessage = commitMsg,
                    remoteVersion = remoteVersion,
                    updateNotes = notes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushCatalogManifest(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        commitMessage: String,
        manifestJson: String
    ): Result<String> = pushRepositoryFile(
        owner = owner,
        repo = repo,
        branch = branch,
        token = token,
        filePath = "ska_updates.json",
        commitMessage = commitMessage.ifBlank { "Update Ska Macconnections TZ catalog manifest" },
        fileContent = manifestJson
    )

    suspend fun pushRepositoryFile(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        filePath: String,
        commitMessage: String,
        fileContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("GitHub Personal Access Token required to push commits."))
            }
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            val cleanBranch = branch.trim().ifBlank { "main" }
            val cleanPath = filePath.trimStart('/')
            val url = "https://api.github.com/repos/$cleanOwner/$cleanRepo/contents/$cleanPath"

            // Check existing file SHA if already present
            var existingSha: String? = null
            val getReq = Request.Builder()
                .url("$url?ref=$cleanBranch")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .addHeader("User-Agent", "SkaMacconnectionsTZ-Android")
                .get()
                .build()
            client.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    existingSha = JSONObject(resp.body?.string().orEmpty()).optString("sha").takeIf { it.isNotBlank() }
                }
            }

            val encodedContent = Base64.encodeToString(fileContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("message", commitMessage.ifBlank { "Update $cleanPath" })
                put("content", encodedContent)
                put("branch", cleanBranch)
                if (existingSha != null) put("sha", existingSha)
            }

            val putReq = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .addHeader("User-Agent", "SkaMacconnectionsTZ-Android")
                .put(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(putReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = try {
                        JSONObject(body).optString("message").ifBlank { "GitHub push failed (${resp.code})" }
                    } catch (_: Exception) {
                        "GitHub push failed (${resp.code})"
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val newSha = JSONObject(body).optJSONObject("commit")?.optString("sha")?.take(12).orEmpty()
                Result.success(newSha)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
