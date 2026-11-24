package com.example.data.remote

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveVerificationResult(
    val displayName: String,
    val email: String,
    val usedBytes: Long,
    val totalBytes: Long
)

data class DriveFileItem(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val shareUrl: String,
    val directStreamUrl: String
)

class GoogleDriveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun verifyAccount(accessToken: String): Result<DriveVerificationResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress),storageQuota(usage,limit)")
                    .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val msg = parseGoogleApiError(body, "Drive API error ${response.code}")
                        return@withContext Result.failure(IllegalStateException(msg))
                    }
                    val json = JSONObject(body)
                    val user = json.optJSONObject("user")
                    val quota = json.optJSONObject("storageQuota")
                    val email = user?.optString("emailAddress").orEmpty()
                    val displayName = user?.optString("displayName").orEmpty().ifBlank { email }
                    val used = quota?.optString("usage")?.toLongOrNull() ?: 0L
                    val total = quota?.optString("limit")?.toLongOrNull() ?: 16_106_127_360L
                    Result.success(
                        DriveVerificationResult(
                            displayName = displayName,
                            email = email,
                            usedBytes = used,
                            totalBytes = total
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun listVideoFiles(accessToken: String): Result<List<DriveFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val query = Uri.encode("mimeType contains 'video/' and trashed = false")
                val url = "https://www.googleapis.com/drive/v3/files?q=$query&pageSize=25&fields=files(id,name,mimeType,size)"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException(parseGoogleApiError(body, "Failed to list Drive videos (${response.code})"))
                        )
                    }
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    val list = buildList {
                        if (files != null) {
                            for (i in 0 until files.length()) {
                                val obj = files.optJSONObject(i) ?: continue
                                val id = obj.optString("id")
                                add(
                                    DriveFileItem(
                                        fileId = id,
                                        name = obj.optString("name", "Drive Video"),
                                        mimeType = obj.optString("mimeType", "video/mp4"),
                                        sizeBytes = obj.optString("size").toLongOrNull() ?: 0L,
                                        shareUrl = "https://drive.google.com/file/d/$id/view?usp=sharing",
                                        directStreamUrl = "https://drive.google.com/uc?export=download&id=$id"
                                    )
                                )
                            }
                        }
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun uploadMediaAndShare(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        accessToken: String
    ): Result<DriveFileItem> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file"))

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", mimeType)
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(bytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val uploadReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .post(multipartBody)
                .build()

            val uploadedObj = client.newCall(uploadReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(parseGoogleApiError(body, "Drive upload failed (${response.code})"))
                    )
                }
                JSONObject(body)
            }

            val fileId = uploadedObj.optString("id")
            if (fileId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Drive upload succeeded without file ID"))
            }

            // Ensure share permissions allow streaming
            val permJson = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }.toString()
            val permReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .post(permJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(permReq).execute().close()

            Result.success(
                DriveFileItem(
                    fileId = fileId,
                    name = uploadedObj.optString("name", fileName),
                    mimeType = uploadedObj.optString("mimeType", mimeType),
                    sizeBytes = uploadedObj.optString("size").toLongOrNull() ?: bytes.size.toLong(),
                    shareUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing",
                    directStreamUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGoogleApiError(body: String, fallback: String): String {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
