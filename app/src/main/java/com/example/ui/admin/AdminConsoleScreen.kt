package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.InstallBannerSettings
import com.example.data.model.*
import com.example.data.remote.DriveFileItem
import com.example.data.util.VideoLinkParser
import com.example.ui.i18n.UiStrings
import com.example.ui.social.PrivateChatRoom
import com.example.ui.theme.*

@Composable
fun AdminConsoleScreen(
    currentUserId: String,
    moviesState: UiState<List<Movie>>,
    driveAccountsState: UiState<List<DriveAccountSlot>>,
    driveSlotFiles: List<DriveFileItem>,
    gitHubConfigState: UiState<GitHubConfig?>,
    installSettings: InstallBannerSettings,
    allPrivateChatsState: UiState<List<PrivateChat>>,
    selectedOversightChat: PrivateChat?,
    selectedOversightMessages: UiState<List<PrivateMessage>>,
    allPaymentsState: UiState<List<PaymentRequest>>,
    allUsersState: UiState<List<UserProfile>>,
    isBusy: Boolean,
    strings: UiStrings,
    onPublishMovie: (title: String, desc: String, tier: MovieTier, url: String, poster: String?, genre: String, duration: Long, driveSlot: Long) -> Unit,
    onPreviewMovie: (Movie) -> Unit,
    onDeleteMovie: (String) -> Unit,
    onConnectDriveSlot: (slotNumber: Int, label: String, accessToken: String) -> Unit,
    onDisconnectDriveSlot: (DriveAccountSlot) -> Unit,
    onLoadDriveVideos: (Int) -> Unit,
    onUploadVideoToDriveSlot: (slotNumber: Int, uri: Uri, onLinkReady: (String) -> Unit) -> Unit,
    onPullGitHub: (owner: String, repo: String, branch: String, pat: String?) -> Unit,
    onPushGitHub: (owner: String, repo: String, branch: String, pat: String, message: String) -> Unit,
    onSaveInstallAdConfig: (enabled: Boolean, apkUrl: String, webUrl: String) -> Unit,
    onPushBrowserAdPortal: (owner: String, repo: String, branch: String, pat: String) -> Unit,
    onOpenInstallPortalPreview: () -> Unit,
    onSelectOversightChat: (PrivateChat?) -> Unit,
    onApprovePayment: (PaymentRequest, Boolean) -> Unit,
    onUpdateUserTier: (String, MovieTier) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedOversightChat != null) {
        PrivateChatRoom(
            chat = selectedOversightChat,
            currentUserId = currentUserId,
            messagesState = selectedOversightMessages,
            strings = strings,
            readOnlyOversight = true,
            onBack = { onSelectOversightChat(null) },
            onSend = { _, _ -> },
            onPlayMediaUrl = { url, title ->
                onPreviewMovie(Movie(id = "preview_chat", title = title, videoUrl = url, linkType = VideoLinkParser.parse(url).linkType.name))
            },
            modifier = modifier
        )
        return
    }

    var adminTab by remember { mutableIntStateOf(0) }
    var prefilledVideoUrl by remember { mutableStateOf("") }
    var prefilledDriveSlot by remember { mutableStateOf(0L) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        ScrollableTabRow(
            selectedTabIndex = adminTab,
            containerColor = OnyxSurface,
            contentColor = TanzanianGold,
            edgePadding = 12.dp
        ) {
            Tab(selected = adminTab == 0, onClick = { adminTab = 0 }, text = { Text(strings.adminAddMovie) })
            Tab(selected = adminTab == 1, onClick = { adminTab = 1 }, text = { Text(strings.adminDriveAccounts) })
            Tab(selected = adminTab == 2, onClick = { adminTab = 2 }, text = { Text(strings.adminGitHubUpdates) })
            Tab(selected = adminTab == 3, onClick = { adminTab = 3 }, text = { Text(strings.adminOversight) })
            Tab(selected = adminTab == 4, onClick = { adminTab = 4 }, text = { Text(strings.adminUsersAndPayments) })
        }

        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = TanzanianGold,
                trackColor = DividerSubtle
            )
        }

        when (adminTab) {
            0 -> AdminUploadMovieTab(
                moviesState = moviesState,
                initialUrl = prefilledVideoUrl,
                initialDriveSlot = prefilledDriveSlot,
                strings = strings,
                onPublish = { t, d, tier, url, poster, genre, dur, slot ->
                    onPublishMovie(t, d, tier, url, poster, genre, dur, slot)
                    prefilledVideoUrl = ""
                },
                onPreview = onPreviewMovie,
                onDelete = onDeleteMovie,
                onUploadLocalVideoToSlot = { slot, uri, onLink ->
                    onUploadVideoToDriveSlot(slot, uri, onLink)
                }
            )
            1 -> AdminDriveManagerTab(
                driveAccountsState = driveAccountsState,
                driveSlotFiles = driveSlotFiles,
                strings = strings,
                onConnectSlot = onConnectDriveSlot,
                onDisconnectSlot = onDisconnectDriveSlot,
                onLoadSlotFiles = onLoadDriveVideos,
                onSelectFileForUpload = { file, slot ->
                    prefilledVideoUrl = file.shareUrl
                    prefilledDriveSlot = slot.toLong()
                    adminTab = 0
                }
            )
            2 -> AdminGitHubTab(
                gitHubConfigState = gitHubConfigState,
                installSettings = installSettings,
                strings = strings,
                onPull = onPullGitHub,
                onPush = onPushGitHub,
                onSaveInstallAdConfig = onSaveInstallAdConfig,
                onPushBrowserAdPortal = onPushBrowserAdPortal,
                onOpenInstallPortalPreview = onOpenInstallPortalPreview
            )
            3 -> AdminOversightTab(
                allPrivateChatsState = allPrivateChatsState,
                strings = strings,
                onOpenChat = onSelectOversightChat
            )
            else -> AdminUsersAndPaymentsTab(
                allPaymentsState = allPaymentsState,
                allUsersState = allUsersState,
                strings = strings,
                onApprovePayment = onApprovePayment,
                onUpdateUserTier = onUpdateUserTier
            )
        }
    }
}

@Composable
private fun AdminUploadMovieTab(
    moviesState: UiState<List<Movie>>,
    initialUrl: String,
    initialDriveSlot: Long,
    strings: UiStrings,
    onPublish: (String, String, MovieTier, String, String?, String, Long, Long) -> Unit,
    onPreview: (Movie) -> Unit,
    onDelete: (String) -> Unit,
    onUploadLocalVideoToSlot: (Int, Uri, (String) -> Unit) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var videoUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var posterUrl by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Bongo Drama") }
    var duration by remember { mutableStateOf("95") }
    var selectedTier by remember { mutableStateOf(MovieTier.FREE) }
    var selectedDriveSlot by remember(initialDriveSlot) { mutableStateOf(initialDriveSlot) }

    val parsed = remember(videoUrl) { VideoLinkParser.parse(videoUrl) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedDriveSlot in 1L..5L) {
            onUploadLocalVideoToSlot(selectedDriveSlot.toInt(), uri) { shareUrl ->
                videoUrl = shareUrl
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, DividerSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.adminAddMovie, style = MaterialTheme.typography.titleLarge, color = TanzanianGold)

                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text(strings.pasteAnyVideoLink) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_video_url_input")
                    )

                    if (videoUrl.isNotBlank()) {
                        Surface(
                            color = if (parsed.isSafeScheme) TanzaniteTealContainer else PremiumCrimsonContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.detectedLinkType}: ${parsed.linkType.label}",
                                        color = if (parsed.isSafeScheme) TanzaniteTeal else ErrorCoral,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (parsed.extractedId != null) {
                                        Text("ID: ${parsed.extractedId}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                                TextButton(onClick = {
                                    onPreview(
                                        Movie(
                                            id = "admin_preview",
                                            title = title.ifBlank { "Link Preview" },
                                            description = description.ifBlank { "Admin link test" },
                                            tier = selectedTier.name,
                                            videoUrl = videoUrl,
                                            resolvedStreamUrl = parsed.playableUrl,
                                            linkType = parsed.linkType.name
                                        )
                                    )
                                }) {
                                    Text(strings.previewStream, color = TanzanianGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Movie Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Mins") },
                            singleLine = true,
                            modifier = Modifier.width(96.dp)
                        )
                    }

                    OutlinedTextField(
                        value = posterUrl,
                        onValueChange = { posterUrl = it },
                        label = { Text("Poster Image URL (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(strings.selectSection, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MovieTier.entries.forEach { tier ->
                            FilterChip(
                                selected = selectedTier == tier,
                                onClick = { selectedTier = tier },
                                label = { Text(tier.name) }
                            )
                        }
                    }

                    Text(strings.selectDriveSlot, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedDriveSlot == 0L,
                                onClick = { selectedDriveSlot = 0L },
                                label = { Text("Direct URL") }
                            )
                        }
                        items((1..5).toList()) { slot ->
                            FilterChip(
                                selected = selectedDriveSlot == slot.toLong(),
                                onClick = { selectedDriveSlot = slot.toLong() },
                                label = { Text("Drive #$slot") }
                            )
                        }
                    }

                    if (selectedDriveSlot in 1L..5L) {
                        OutlinedButton(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Upload Video File to Drive #$selectedDriveSlot")
                        }
                    }

                    Button(
                        onClick = {
                            onPublish(
                                title,
                                description,
                                selectedTier,
                                videoUrl,
                                posterUrl.takeIf { it.isNotBlank() },
                                genre,
                                duration.toLongOrNull() ?: 90L,
                                selectedDriveSlot
                            )
                            title = ""
                            description = ""
                            videoUrl = ""
                            posterUrl = ""
                        },
                        enabled = title.isNotBlank() && description.isNotBlank() && parsed.isSafeScheme,
                        colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_publish_movie_button")
                    ) {
                        Text(strings.publishMovie, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (moviesState is UiState.Success && moviesState.data.isNotEmpty()) {
            item {
                Text("Published Catalog (${moviesState.data.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(moviesState.data, key = { it.id }) { movie ->
                Surface(
                    color = OnyxSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DividerSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(movie.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${movie.tier} • ${movie.resolvedLinkType().name} • Drive #${movie.driveAccountSlot}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { onDelete(movie.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Movie", tint = ErrorCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDriveManagerTab(
    driveAccountsState: UiState<List<DriveAccountSlot>>,
    driveSlotFiles: List<DriveFileItem>,
    strings: UiStrings,
    onConnectSlot: (Int, String, String) -> Unit,
    onDisconnectSlot: (DriveAccountSlot) -> Unit,
    onLoadSlotFiles: (Int) -> Unit,
    onSelectFileForUpload: (DriveFileItem, Int) -> Unit
) {
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var activeBrowseSlot by remember { mutableStateOf<Int?>(null) }
    val slotsList = (driveAccountsState as? UiState.Success)?.data.orEmpty()
    val byNumber = remember(slotsList) { slotsList.associateBy { it.slotNumber.toInt() } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Google Drive Multi-Account Storage (Max 5)",
                style = MaterialTheme.typography.titleLarge,
                color = TanzanianGold
            )
        }

        items((1..5).toList()) { slotNum ->
            val slot = byNumber[slotNum]
            val isActive = slot != null && (slot.status == "ACTIVE" || slot.status == "CONNECTED")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, if (isActive) TanzaniteTeal else DividerSubtle),
                modifier = Modifier.fillMaxWidth()
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
                        Column {
                            Text(
                                text = "${strings.driveAccountSlotLabel} #$slotNum",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = slot?.accountEmail ?: strings.disconnected,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) TanzaniteTeal else TextMuted
                            )
                        }
                        Badge(
                            containerColor = if (isActive) TanzaniteTealContainer else OnyxSurface
                        ) {
                            Text(
                                text = slot?.status ?: "DISCONNECTED",
                                color = if (isActive) TanzaniteTeal else TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (slot != null && slot.totalBytes > 0) {
                        val ratio = (slot.usedBytes.toFloat() / slot.totalBytes.toFloat()).coerceIn(0f, 1f)
                        val usedGb = String.format("%.2f GB", slot.usedBytes / 1_073_741_824f)
                        val totalGb = String.format("%.1f GB", slot.totalBytes / 1_073_741_824f)
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth(),
                            color = TanzaniteTeal,
                            trackColor = DividerSubtle
                        )
                        Text("$usedGb / $totalGb used", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { editingSlot = slotNum },
                            colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isActive) "Update Token" else strings.connectDriveSlot, maxLines = 1)
                        }
                        if (isActive && slot != null) {
                            OutlinedButton(onClick = {
                                activeBrowseSlot = slotNum
                                onLoadSlotFiles(slotNum)
                            }) {
                                Text("Browse")
                            }
                            IconButton(onClick = { onDisconnectSlot(slot) }) {
                                Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect", tint = ErrorCoral)
                            }
                        }
                    }
                }
            }
        }

        if (activeBrowseSlot != null && driveSlotFiles.isNotEmpty()) {
            item {
                Text("Videos in Drive #${activeBrowseSlot}", style = MaterialTheme.typography.titleMedium, color = TanzaniteTeal)
            }
            items(driveSlotFiles, key = { it.fileId }) { file ->
                Surface(
                    color = OnyxSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(file.shareUrl, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(onClick = { onSelectFileForUpload(file, activeBrowseSlot ?: 1) }) {
                            Text("Use Link", color = TanzanianGold)
                        }
                    }
                }
            }
        }
    }

    val targetSlot = editingSlot
    if (targetSlot != null) {
        var label by remember { mutableStateOf("Ska Drive Vault #$targetSlot") }
        var oauthToken by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingSlot = null },
            containerColor = OnyxSurface,
            title = { Text("Connect Drive Slot #$targetSlot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste a Google Drive OAuth 2.0 Access Token (scope: drive.file / drive.readonly). Verified live via Drive API v3 and stored strictly on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Account Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = oauthToken,
                        onValueChange = { oauthToken = it },
                        label = { Text("Google OAuth2 Access Token") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (oauthToken.isNotBlank()) {
                            onConnectSlot(targetSlot, label, oauthToken)
                            editingSlot = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg)
                ) {
                    Text("Verify & Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSlot = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminGitHubTab(
    gitHubConfigState: UiState<GitHubConfig?>,
    installSettings: InstallBannerSettings,
    strings: UiStrings,
    onPull: (String, String, String, String?) -> Unit,
    onPush: (String, String, String, String, String) -> Unit,
    onSaveInstallAdConfig: (Boolean, String, String) -> Unit,
    onPushBrowserAdPortal: (String, String, String, String) -> Unit,
    onOpenInstallPortalPreview: () -> Unit
) {
    val current = (gitHubConfigState as? UiState.Success)?.data
    var owner by remember(current?.owner) { mutableStateOf(current?.owner.orEmpty()) }
    var repo by remember(current?.repo) { mutableStateOf(current?.repo.orEmpty()) }
    var branch by remember(current?.branch) { mutableStateOf(current?.branch ?: "main") }
    var pat by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("Sync Ska Macconnections TZ catalog updates") }

    var bannerEnabled by remember(installSettings.bannerEnabled) { mutableStateOf(installSettings.bannerEnabled) }
    var apkUrl by remember(installSettings.apkDownloadUrl) { mutableStateOf(installSettings.apkDownloadUrl) }
    var webPortalUrl by remember(installSettings.webPortalUrl) { mutableStateOf(installSettings.webPortalUrl) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (current != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TanzaniteTealContainer),
                    border = BorderStroke(1.dp, TanzaniteTeal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${current.owner}/${current.repo} (${current.branch})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TanzaniteTeal
                        )
                        Text("Status: ${current.status} • Version: ${current.remoteVersion}", color = TextPrimary)
                        Text("Commit ${current.latestCommitSha}: ${current.latestCommitMessage}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        if (!current.updateNotes.isNullOrBlank()) {
                            Text("Notes: ${current.updateNotes}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Browser Install Ad & Phone Distribution Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, GoldBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.adminInstallAdSection, style = MaterialTheme.typography.titleLarge, color = TanzanianGold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.adminEnableInstallAd, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = bannerEnabled,
                            onCheckedChange = { bannerEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = TanzanianGold)
                        )
                    }
                    OutlinedTextField(
                        value = apkUrl,
                        onValueChange = { apkUrl = it },
                        label = { Text(strings.adminApkUrlLabel) },
                        placeholder = { Text("https://github.com/owner/repo/releases/latest") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webPortalUrl,
                        onValueChange = { webPortalUrl = it },
                        label = { Text(strings.adminWebPortalUrlLabel) },
                        placeholder = { Text("https://owner.github.io/repo/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveInstallAdConfig(bannerEnabled, apkUrl, webPortalUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.saveInstallAdSettings, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenInstallPortalPreview,
                            border = BorderStroke(1.dp, GoldBorderGlow)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = TanzanianGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview", color = TextPrimary)
                        }
                    }
                    OutlinedButton(
                        onClick = { onPushBrowserAdPortal(owner, repo, branch, pat) },
                        enabled = owner.isNotBlank() && repo.isNotBlank(),
                        border = BorderStroke(1.dp, TanzaniteTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, tint = TanzaniteTeal)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.pushBrowserAdToGitHub, color = TanzaniteTeal)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, DividerSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.adminGitHubUpdates, style = MaterialTheme.typography.titleLarge, color = TanzanianGold)
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text(strings.githubRepoOwner) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text(strings.githubRepoName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text(strings.githubBranch) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pat,
                        onValueChange = { pat = it },
                        label = { Text("GitHub Personal Access Token (device-local)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Push Commit Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onPull(owner, repo, branch, pat.takeIf { it.isNotBlank() }) },
                        enabled = owner.isNotBlank() && repo.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TanzanianGold, contentColor = ObsidianBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.githubCheckPull, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onPush(owner, repo, branch, pat, commitMessage) },
                        enabled = owner.isNotBlank() && repo.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.githubPushUpdate)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOversightTab(
    allPrivateChatsState: UiState<List<PrivateChat>>,
    strings: UiStrings,
    onOpenChat: (PrivateChat) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = PremiumCrimsonContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = TanzanianGold)
                    Text(strings.allPrivateOversightHint, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }

        when (allPrivateChatsState) {
            is UiState.Loading -> item { CircularProgressIndicator(color = TanzanianGold) }
            is UiState.Error -> item { Text(allPrivateChatsState.message, color = ErrorCoral) }
            is UiState.Success -> {
                if (allPrivateChatsState.data.isEmpty()) {
                    item {
                        Text("No private conversations recorded yet.", color = TextSecondary)
                    }
                } else {
                    items(allPrivateChatsState.data, key = { it.id }) { chat ->
                        val participants = chat.participantNames.values.joinToString(" ↔ ").ifBlank { chat.participantIds.joinToString(" ↔ ") }
                        Card(
                            onClick = { onOpenChat(chat) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, DividerSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(participants, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(chat.lastMessage.ifBlank { "No messages yet" }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
                                }
                                Icon(Icons.Filled.Visibility, contentDescription = "Inspect", tint = TanzanianGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersAndPaymentsTab(
    allPaymentsState: UiState<List<PaymentRequest>>,
    allUsersState: UiState<List<UserProfile>>,
    strings: UiStrings,
    onApprovePayment: (PaymentRequest, Boolean) -> Unit,
    onUpdateUserTier: (String, MovieTier) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Mobile Money Requests", style = MaterialTheme.typography.titleLarge, color = TanzanianGold)
        }
        when (allPaymentsState) {
            is UiState.Loading -> item { CircularProgressIndicator(color = TanzanianGold) }
            is UiState.Error -> item { Text(allPaymentsState.message, color = ErrorCoral) }
            is UiState.Success -> {
                if (allPaymentsState.data.isEmpty()) {
                    item { Text("No mobile money requests yet.", color = TextSecondary) }
                } else {
                    items(allPaymentsState.data, key = { it.id }) { req ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            border = BorderStroke(1.dp, DividerSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("${req.userName} -> ${req.targetTier} (${req.amountTzs} TZS)", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${req.provider} • ${req.phoneNumber} • Ref: ${req.referenceCode} • ${req.status}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                if (req.status == "PENDING") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onApprovePayment(req, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = TanzaniteTeal, contentColor = ObsidianBg),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(strings.approveTier, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onApprovePayment(req, false) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(strings.rejectPayment, color = ErrorCoral)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("Platform Users & Roles", style = MaterialTheme.typography.titleLarge, color = TanzanianGold)
        }
        when (allUsersState) {
            is UiState.Loading -> item { CircularProgressIndicator(color = TanzanianGold) }
            is UiState.Error -> item { Text(allUsersState.message, color = ErrorCoral) }
            is UiState.Success -> {
                items(allUsersState.data, key = { it.userId }) { user ->
                    Surface(
                        color = OnyxSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DividerSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("${user.displayName} (${user.role})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(user.email ?: user.userId, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MovieTier.entries.forEach { tier ->
                                    FilterChip(
                                        selected = user.resolvedTier() == tier,
                                        onClick = { onUpdateUserTier(user.userId, tier) },
                                        label = { Text(tier.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
