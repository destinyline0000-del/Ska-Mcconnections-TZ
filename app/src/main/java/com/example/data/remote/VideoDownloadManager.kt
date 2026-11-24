package com.example.data.remote

import android.content.Context
import android.os.Environment
import com.example.R
import com.example.data.local.LocalSecretsAndDownloadsStore
import com.example.data.model.DownloadProgressState
import com.example.data.model.Movie
import com.example.data.model.OfflineMovieItem
import com.example.data.model.VideoLinkType
import com.example.data.util.VideoLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VideoDownloadManager(
    private val context: Context,
    private val localStore: LocalSecretsAndDownloadsStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadsProgress = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
    val downloadsProgress: StateFlow<Map<String, DownloadProgressState>> = _downloadsProgress.asStateFlow()

    private fun getMoviesDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val dir = File(base, "ska_offline_movies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadMovie(movie: Movie): Result<OfflineMovieItem> = withContext(Dispatchers.IO) {
        val safeId = movie.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "movie_${System.currentTimeMillis()}" }
        val targetFile = File(getMoviesDir(), "$safeId.mp4")

        val parsed = VideoLinkParser.parse(movie.resolvedStreamUrl ?: movie.videoUrl)
        if (!parsed.isSafeScheme) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported video URL scheme"))
        }

        _downloadsProgress.update { map ->
            map + (movie.id to DownloadProgressState(movie.id, movie.title, 0, 0L, 0L))
        }

        try {
            if (parsed.linkType == VideoLinkType.BUNDLED_CLIP) {
                context.resources.openRawResource(R.raw.sample_showcase).use { input ->
                    val total = input.available().toLong().coerceAtLeast(1L)
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            copied += read
                            val pct = ((copied * 100) / total).toInt().coerceIn(0, 100)
                            _downloadsProgress.update { map ->
                                map + (movie.id to DownloadProgressState(movie.id, movie.title, pct, copied, total))
                            }
                        }
                    }
                }
            } else if (parsed.linkType == VideoLinkType.YOUTUBE || parsed.linkType == VideoLinkType.VIMEO) {
                _downloadsProgress.update { it - movie.id }
                return@withContext Result.failure(
                    IllegalStateException("YouTube & Vimeo embeds stream online; direct MP4 and Google Drive files support offline download.")
                )
            } else {
                val downloadUrl = parsed.playableUrl
                val request = Request.Builder().url(downloadUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed (HTTP ${response.code})")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html")) {
                        throw IllegalStateException("This link returned a web page instead of a direct video stream. Ensure Google Drive file sharing is set to Anyone with link.")
                    }
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var copied = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copied += read
                                val pct = if (total > 0) ((copied * 100) / total).toInt().coerceIn(0, 100) else 50
                                _downloadsProgress.update { map ->
                                    map + (movie.id to DownloadProgressState(movie.id, movie.title, pct, copied, total))
                                }
                            }
                        }
                    }
                }
            }

            val item = OfflineMovieItem(
                movieId = movie.id,
                title = movie.title,
                description = movie.description,
                tier = movie.tier,
                genre = movie.genre,
                localFilePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length(),
                downloadedAtMillis = System.currentTimeMillis()
            )
            localStore.saveOfflineMovie(item)
            _downloadsProgress.update { map ->
                map + (movie.id to DownloadProgressState(movie.id, movie.title, 100, targetFile.length(), targetFile.length(), isCompleted = true))
            }
            Result.success(item)
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            _downloadsProgress.update { map ->
                map + (movie.id to DownloadProgressState(movie.id, movie.title, 0, 0L, 0L, errorMessage = e.localizedMessage ?: "Download error"))
            }
            Result.failure(e)
        }
    }

    suspend fun deleteOfflineMovie(item: OfflineMovieItem) = withContext(Dispatchers.IO) {
        try {
            val file = File(item.localFilePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
        localStore.removeOfflineMovie(item.movieId)
        _downloadsProgress.update { it - item.movieId }
    }
}
