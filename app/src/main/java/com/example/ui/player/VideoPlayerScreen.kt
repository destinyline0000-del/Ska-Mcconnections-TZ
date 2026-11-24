package com.example.ui.player

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.DownloadProgressState
import com.example.data.model.Movie
import com.example.data.model.VideoLinkType
import com.example.data.util.VideoLinkParser
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    movie: Movie,
    strings: UiStrings,
    offlineLocalPath: String?,
    downloadProgress: DownloadProgressState?,
    onDownloadClick: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val parsed = remember(movie.videoUrl, movie.resolvedStreamUrl) {
        VideoLinkParser.parse(movie.resolvedStreamUrl ?: movie.videoUrl)
    }

    val initialUseWebView = remember(parsed, offlineLocalPath) {
        offlineLocalPath == null && (
            parsed.linkType == VideoLinkType.YOUTUBE ||
                parsed.linkType == VideoLinkType.VIMEO ||
                parsed.linkType == VideoLinkType.HOSTED_WEB
            )
    }
    var useWebViewFallback by remember(movie.id, offlineLocalPath) { mutableStateOf(initialUseWebView) }
    var exoErrorMessage by remember(movie.id) { mutableStateOf<String?>(null) }

    val mediaUri = remember(movie.id, offlineLocalPath, parsed) {
        when {
            offlineLocalPath != null -> Uri.parse("file://$offlineLocalPath")
            parsed.linkType == VideoLinkType.BUNDLED_CLIP ->
                RawResourceDataSource.buildRawResourceUri(R.raw.sample_showcase)
            else -> Uri.parse(parsed.playableUrl)
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnyxSurface,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (offlineLocalPath != null) strings.playOffline else parsed.linkType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TanzanianGold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(ObsidianBg)
            ) {
                if (useWebViewFallback && parsed.embedUrl != null) {
                    EmbeddedVideoWebView(
                        url = parsed.embedUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ExoPlayerSurface(
                        mediaUri = mediaUri,
                        onError = { msg ->
                            exoErrorMessage = msg
                            if (parsed.embedUrl != null) {
                                useWebViewFallback = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(movie.tier) },
                        leadingIcon = {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(movie.genre) }
                    )
                    if (movie.driveAccountSlot > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Drive #${movie.driveAccountSlot}") }
                        )
                    }
                }

                if (exoErrorMessage != null && !useWebViewFallback) {
                    Text(
                        text = exoErrorMessage.orEmpty(),
                        color = ErrorCoral,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (parsed.embedUrl != null && parsed.linkType == VideoLinkType.GOOGLE_DRIVE) {
                    OutlinedButton(
                        onClick = { useWebViewFallback = !useWebViewFallback },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (useWebViewFallback) "Switch to Native ExoPlayer Stream"
                            else "Switch to Google Drive Embedded Preview"
                        )
                    }
                }

                Text(
                    text = movie.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                HorizontalDivider(color = DividerSubtle)

                if (offlineLocalPath != null) {
                    Surface(
                        color = TanzaniteTealContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = TanzaniteTeal)
                            Text(
                                text = strings.downloaded,
                                color = TanzaniteTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (downloadProgress != null && !downloadProgress.isCompleted && downloadProgress.errorMessage == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SlateCard)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.downloading, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${downloadProgress.progressPercent}%", color = TanzanianGold, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { downloadProgress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = TanzanianGold,
                            trackColor = DividerSubtle
                        )
                    }
                } else {
                    if (downloadProgress?.errorMessage != null) {
                        Text(
                            text = downloadProgress.errorMessage,
                            color = ErrorCoral,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = onDownloadClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TanzanianGold,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("player_download_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.downloadOffline, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExoPlayerSurface(
    mediaUri: Uri,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError(error.localizedMessage ?: "Stream playback error")
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = modifier
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedVideoWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    val safeUrl = remember(url) {
        val lower = url.trim().lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) url.trim() else "about:blank"
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString()?.lowercase().orEmpty()
                        return !(reqUrl.startsWith("https://") || reqUrl.startsWith("http://"))
                    }
                }
                loadUrl(safeUrl)
            }
        },
        update = { webView ->
            if (webView.url != safeUrl) webView.loadUrl(safeUrl)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier
    )
}
