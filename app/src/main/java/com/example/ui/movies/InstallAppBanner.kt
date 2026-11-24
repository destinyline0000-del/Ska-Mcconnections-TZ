package com.example.ui.movies

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.InstallBannerSettings
import com.example.ui.components.RoyalCrownMark
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.*

/**
 * Floating Obsidian & Royal Gold "Install App" prompt card matching the reference screenshot.
 */
@Composable
fun FloatingInstallAppAdCard(
    strings: UiStrings,
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = OnyxSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.2.dp, TanzanianGold.copy(alpha = 0.85f)),
        shadowElevation = 12.dp,
        modifier = modifier
            .widthIn(max = 320.dp)
            .testTag("floating_install_app_ad")
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .testTag("dismiss_install_app_ad")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(end = 28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg)
                            .border(1.dp, GoldBorderGlow, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RoyalCrownMark(size = 30.dp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.installAdTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.installAdSubtitleShort,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onInstallClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TanzanianGold,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("install_app_ad_cta")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.installAppButton,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Interactive Browser Install Portal & Live Mobile Web Ad Preview Modal.
 */
@Composable
fun BrowserInstallPortalDialog(
    settings: InstallBannerSettings,
    language: AppLanguage,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val safeApkUrl = settings.apkDownloadUrl.trim().takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }
    val safeWebUrl = settings.webPortalUrl.trim().takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }

    val previewLocalAssetUrl = remember(language, safeApkUrl) {
        val builder = Uri.parse("file:///android_asset/install_portal.html").buildUpon()
            .appendQueryParameter("lang", language.code.lowercase())
        if (safeApkUrl != null) {
            builder.appendQueryParameter("apk", safeApkUrl)
        }
        builder.build().toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = OnyxSurface,
            border = BorderStroke(1.dp, GoldBorderGlow),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .fillMaxHeight(0.86f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ObsidianBg, GoldStripSurface, ObsidianBg)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RoyalCrownMark(size = 28.dp)
                        Text(
                            text = strings.browserInstallPortalTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TanzanianGold,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SlateCard,
                    contentColor = TanzanianGold
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.getPhoneApp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.previewBrowserAd, fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = strings.browserInstallPortalDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        // Preview of the floating browser prompt card
                        FloatingInstallAppAdCard(
                            strings = strings,
                            onInstallClick = {
                                if (safeApkUrl != null) {
                                    openExternalUrl(context, safeApkUrl, onStatusMessage)
                                } else {
                                    selectedTab = 1
                                }
                            },
                            onDismiss = { selectedTab = 1 },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        HorizontalDivider(color = DividerSubtle)

                        Button(
                            onClick = {
                                if (safeApkUrl != null) {
                                    openExternalUrl(context, safeApkUrl, onStatusMessage)
                                } else {
                                    onStatusMessage("Admin can set the Android APK / Store URL in Admin -> GitHub tab.")
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TanzanianGold,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Filled.Android, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.downloadPhoneApk, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (safeWebUrl != null) {
                                    val withParams = Uri.parse(safeWebUrl).buildUpon()
                                        .appendQueryParameter("lang", language.code.lowercase())
                                        .apply { if (safeApkUrl != null) appendQueryParameter("apk", safeApkUrl) }
                                        .build().toString()
                                    openExternalUrl(context, withParams, onStatusMessage)
                                } else {
                                    selectedTab = 1
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GoldBorderGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = TanzanianGold)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.openInBrowser, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                val targetLink = safeWebUrl ?: safeApkUrl ?: "skamactz://open"
                                val shareText = "Ska Macconnections TZ — Watch & Download Movies in HD! Install on your phone: $targetLink"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Ska Macconnections TZ")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, strings.shareAppLink))
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, DividerSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = TanzaniteTeal)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.shareAppLink, color = TextPrimary)
                        }
                    }
                } else {
                    BrowserPortalWebView(
                        url = previewLocalAssetUrl,
                        onExternalOpen = { extUrl ->
                            openExternalUrl(context, extUrl, onStatusMessage)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String, onError: (String) -> Unit) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Unable to open link in browser")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserPortalWebView(
    url: String,
    onExternalOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        if (target.startsWith("file:///android_asset/")) return false
                        if (target.startsWith("https://") || target.startsWith("http://") || target.startsWith("skamactz://")) {
                            onExternalOpen(target)
                            return true
                        }
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) webView.loadUrl(url)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier
    )
}
