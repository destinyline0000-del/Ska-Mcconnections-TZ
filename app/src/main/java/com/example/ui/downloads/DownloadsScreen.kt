package com.example.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadProgressState
import com.example.data.model.Movie
import com.example.data.model.OfflineMovieItem
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*
import java.io.File

@Composable
fun DownloadsScreen(
    offlineMovies: List<OfflineMovieItem>,
    downloadsProgress: Map<String, DownloadProgressState>,
    strings: UiStrings,
    onPlayOffline: (Movie, String) -> Unit,
    onDeleteOffline: (OfflineMovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDownloads = downloadsProgress.values.filter { !it.isCompleted && it.errorMessage == null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strings.offlineLibrary,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }

        if (activeDownloads.isNotEmpty()) {
            items(activeDownloads, key = { "active_${it.movieId}" }) { progress ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, TanzanianGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(progress.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${progress.progressPercent}%", color = TanzanianGold, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { progress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = TanzanianGold,
                            trackColor = DividerSubtle
                        )
                        val mbDone = String.format("%.2f MB", progress.bytesDownloaded / (1024f * 1024f))
                        Text(
                            text = "${strings.downloading} $mbDone",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        if (offlineMovies.isEmpty() && activeDownloads.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = OnyxSurface,
                    border = BorderStroke(1.dp, DividerSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = TanzanianGold,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(strings.noDownloadsTitle, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(strings.noDownloadsHint, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        } else {
            items(offlineMovies, key = { it.movieId }) { item ->
                val existsOnDisk = File(item.localFilePath).exists()
                val sizeMb = String.format("%.2f MB", item.fileSizeBytes / (1024f * 1024f))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, DividerSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_card_${item.movieId}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${item.tier} • ${item.genre} • $sizeMb", style = MaterialTheme.typography.labelSmall, color = TanzaniteTeal)
                            }
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = TanzaniteTeal)
                        }

                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val movie = Movie(
                                        id = item.movieId,
                                        title = item.title,
                                        description = item.description,
                                        tier = item.tier,
                                        videoUrl = "file://${item.localFilePath}",
                                        linkType = "DIRECT_MP4",
                                        genre = item.genre
                                    )
                                    onPlayOffline(movie, item.localFilePath)
                                },
                                enabled = existsOnDisk,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TanzanianGold,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.playOffline, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onDeleteOffline(item) },
                                border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.deleteOffline, tint = ErrorCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}
