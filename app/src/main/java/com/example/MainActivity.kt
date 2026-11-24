package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalSecretsAndDownloadsStore
import com.example.data.model.Movie
import com.example.data.model.MovieTier
import com.example.data.model.UiState
import com.example.data.remote.GitHubService
import com.example.data.remote.GoogleDriveService
import com.example.data.remote.VideoDownloadManager
import com.example.data.repository.AppRepository
import com.example.data.util.VideoLinkParser
import com.example.ui.admin.AdminConsoleScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.SkaBrandHeaderTitle
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.ui.movies.BrowserInstallPortalDialog
import com.example.ui.movies.MobileMoneyUpgradeSheet
import com.example.ui.movies.MoviesScreen
import com.example.ui.player.VideoPlayerScreen
import com.example.ui.social.ConnectionScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class MainDestination {
    MOVIES, CONNECT, DOWNLOADS, ADMIN
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkaMacconnectionsTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    val localStore = remember(context) { LocalSecretsAndDownloadsStore(context.applicationContext) }
    val language by localStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.EN)
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            language = language,
            onLanguageChange = { lang ->
                scope.launch { localStore.setLanguage(lang) }
            },
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedRoot(firebaseUser = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedRoot(firebaseUser: FirebaseUser) {
    val uid = firebaseUser.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val localStore = LocalSecretsAndDownloadsStore(app)
                MainViewModel(
                    repository = AppRepository(db),
                    localStore = localStore,
                    downloadManager = VideoDownloadManager(app, localStore),
                    driveService = GoogleDriveService(),
                    gitHubService = GitHubService(),
                    currentUserId = uid
                )
            }
        }
    )

    LaunchedEffect(uid) {
        viewModel.bootstrapUser(firebaseUser)
    }

    val language by viewModel.language.collectAsStateWithLifecycle()
    val strings = remember(language) { stringsFor(language) }
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val userProfile = (userProfileState as? UiState.Success)?.data
    val isAdmin = userProfile?.isAdminUser() == true ||
        (firebaseUser.email == "destinyline0000@gmail.com" && firebaseUser.isEmailVerified)

    val moviesState by viewModel.moviesState.collectAsStateWithLifecycle()
    val chatGroupsState by viewModel.chatGroupsState.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val groupMessagesState by viewModel.selectedGroupMessages.collectAsStateWithLifecycle()
    val userDirectoryState by viewModel.userDirectoryState.collectAsStateWithLifecycle()
    val myPrivateChatsState by viewModel.myPrivateChatsState.collectAsStateWithLifecycle()
    val selectedPrivateChat by viewModel.selectedPrivateChat.collectAsStateWithLifecycle()
    val privateMessagesState by viewModel.selectedPrivateMessages.collectAsStateWithLifecycle()
    val myPaymentsState by viewModel.myPaymentRequestsState.collectAsStateWithLifecycle()
    val offlineMovies by viewModel.offlineMovies.collectAsStateWithLifecycle()
    val downloadsProgress by viewModel.downloadsProgress.collectAsStateWithLifecycle()
    val installSettings by viewModel.installBannerSettings.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    // Admin states
    val adminPrivateChatsState by viewModel.adminAllPrivateChatsState.collectAsStateWithLifecycle()
    val adminPaymentsState by viewModel.adminAllPaymentsState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminAllUsersState.collectAsStateWithLifecycle()
    val driveAccountsState by viewModel.driveAccountsState.collectAsStateWithLifecycle()
    val driveSlotFiles by viewModel.driveSlotFiles.collectAsStateWithLifecycle()
    val gitHubConfigState by viewModel.gitHubConfigState.collectAsStateWithLifecycle()
    val isAdminBusy by viewModel.isAdminBusy.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(MainDestination.MOVIES) }
    var activePlayerMovie by remember { mutableStateOf<Pair<Movie, String?>?>(null) }
    var upgradeSheetTier by remember { mutableStateOf<MovieTier?>(null) }
    var showInstallPortal by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val activePlayback = activePlayerMovie
    if (activePlayback != null) {
        val (movie, localPath) = activePlayback
        val liveLocal = offlineMovies.firstOrNull { it.movieId == movie.id }?.localFilePath ?: localPath
        VideoPlayerScreen(
            movie = movie,
            strings = strings,
            offlineLocalPath = liveLocal,
            downloadProgress = downloadsProgress[movie.id],
            onDownloadClick = { viewModel.downloadMovie(movie) },
            onClose = { activePlayerMovie = null }
        )
        return
    }

    val destinations = remember(isAdmin) {
        buildList {
            add(MainDestination.MOVIES)
            add(MainDestination.CONNECT)
            add(MainDestination.DOWNLOADS)
            if (isAdmin) add(MainDestination.ADMIN)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = OnyxSurface,
                            titleContentColor = TextPrimary
                        ),
                        title = {
                            SkaBrandHeaderTitle(
                                subtitle = strings.premiumBrandSubtitle,
                                compact = true
                            )
                        },
                        actions = {
                            OutlinedButton(
                                onClick = {
                                    val next = if (language == AppLanguage.EN) AppLanguage.SW else AppLanguage.EN
                                    viewModel.setLanguage(next)
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, GoldBorderGlow),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("language_toggle_button")
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = "Language", tint = TanzanianGold, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(language.code, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { signOutUser(credentialManager, scope) },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = strings.signOut,
                                    tint = TextSecondary
                                )
                            }
                        }
                    )
                    // Subtle golden hairline glow under header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, GoldBorderGlow, TanzanianGold.copy(alpha = 0.7f), GoldBorderGlow, Color.Transparent)
                                )
                            )
                    )
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, GoldBorderGlow, TanzanianGold, GoldBorderGlow, Color.Transparent)
                                    )
                                )
                        )
                        NavigationBar(containerColor = OnyxSurface) {
                            destinations.forEach { dest ->
                                val (label, icon) = destinationMeta(dest, strings)
                                NavigationBarItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianBg,
                                        selectedTextColor = TanzanianGold,
                                        indicatorColor = TanzanianGold,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(containerColor = OnyxSurface) {
                        Spacer(Modifier.height(12.dp))
                        destinations.forEach { dest ->
                            val (label, icon) = destinationMeta(dest, strings)
                            NavigationRailItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = TanzanianGold,
                                    indicatorColor = TanzanianGold,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        MainDestination.MOVIES -> MoviesScreen(
                            moviesState = moviesState,
                            userProfile = userProfile,
                            offlineMovies = offlineMovies,
                            downloadsProgress = downloadsProgress,
                            installSettings = installSettings,
                            strings = strings,
                            onPlayMovie = { movie, localPath ->
                                activePlayerMovie = movie to localPath
                            },
                            onDownloadMovie = { movie -> viewModel.downloadMovie(movie) },
                            onOpenUpgradeSheet = { tier -> upgradeSheetTier = tier },
                            onOpenInstallPortal = { showInstallPortal = true },
                            onDismissInstallBanner = { viewModel.dismissInstallBanner() }
                        )
                        MainDestination.CONNECT -> ConnectionScreen(
                            currentUserId = uid,
                            groupsState = chatGroupsState,
                            selectedGroup = selectedGroup,
                            groupMessagesState = groupMessagesState,
                            privateChatsState = myPrivateChatsState,
                            selectedPrivateChat = selectedPrivateChat,
                            privateMessagesState = privateMessagesState,
                            directoryState = userDirectoryState,
                            strings = strings,
                            onSelectGroup = { viewModel.openGroup(it) },
                            onCreateGroup = { n, d, t -> viewModel.createGroup(n, d, t) },
                            onSendGroupMessage = { gid, txt, uri, isVid, url ->
                                viewModel.sendGroupTextOrMedia(context, gid, txt, uri, isVid, url)
                            },
                            onSelectPrivateChat = { viewModel.openExistingPrivateChat(it) },
                            onStartPrivateChatWithMember = { viewModel.openPrivateChatWithUser(it) },
                            onSendPrivateMessage = { chat, txt, url ->
                                viewModel.sendPrivateMessage(chat, txt, url)
                            },
                            onPlayMediaUrl = { url, title ->
                                val parsed = VideoLinkParser.parse(url)
                                activePlayerMovie = Movie(
                                    id = "chat_media_${url.hashCode()}",
                                    title = title,
                                    videoUrl = url,
                                    resolvedStreamUrl = parsed.playableUrl,
                                    linkType = parsed.linkType.name
                                ) to null
                            }
                        )
                        MainDestination.DOWNLOADS -> DownloadsScreen(
                            offlineMovies = offlineMovies,
                            downloadsProgress = downloadsProgress,
                            strings = strings,
                            onPlayOffline = { movie, path ->
                                activePlayerMovie = movie to path
                            },
                            onDeleteOffline = { viewModel.deleteOfflineMovie(it) }
                        )
                        MainDestination.ADMIN -> AdminConsoleScreen(
                            currentUserId = uid,
                            moviesState = moviesState,
                            driveAccountsState = driveAccountsState,
                            driveSlotFiles = driveSlotFiles,
                            gitHubConfigState = gitHubConfigState,
                            installSettings = installSettings,
                            allPrivateChatsState = adminPrivateChatsState,
                            selectedOversightChat = selectedPrivateChat,
                            selectedOversightMessages = privateMessagesState,
                            allPaymentsState = adminPaymentsState,
                            allUsersState = adminUsersState,
                            isBusy = isAdminBusy,
                            strings = strings,
                            onPublishMovie = { t, d, tier, url, poster, genre, dur, slot ->
                                viewModel.publishMovieByLink(t, d, tier, url, poster, genre, dur, slot)
                            },
                            onPreviewMovie = { movie ->
                                activePlayerMovie = movie to null
                            },
                            onDeleteMovie = { viewModel.deleteMovie(it) },
                            onConnectDriveSlot = { slot, lbl, tok ->
                                viewModel.connectDriveSlot(slot, lbl, tok)
                            },
                            onDisconnectDriveSlot = { viewModel.disconnectDriveSlot(it) },
                            onLoadDriveVideos = { viewModel.loadDriveVideosForSlot(it) },
                            onUploadVideoToDriveSlot = { slot, uri, onLink ->
                                viewModel.uploadVideoToDriveSlot(context, slot, uri, onLink)
                            },
                            onPullGitHub = { o, r, b, p -> viewModel.pullGitHubUpdates(o, r, b, p) },
                            onPushGitHub = { o, r, b, p, m -> viewModel.pushGitHubCatalogUpdate(o, r, b, p, m) },
                            onSaveInstallAdConfig = { enabled, apk, web ->
                                viewModel.saveInstallBannerConfig(enabled, apk, web)
                            },
                            onPushBrowserAdPortal = { o, r, b, p ->
                                viewModel.pushBrowserPortalToGitHub(context, o, r, b, p)
                            },
                            onOpenInstallPortalPreview = { showInstallPortal = true },
                            onSelectOversightChat = { viewModel.openExistingPrivateChat(it) },
                            onApprovePayment = { req, approve -> viewModel.approveOrRejectPayment(req, approve) },
                            onUpdateUserTier = { targetUid, tier -> viewModel.updateUserTier(targetUid, tier) }
                        )
                    }
                }
            }
        }
    }

    val sheetTier = upgradeSheetTier
    if (sheetTier != null) {
        val requests = (myPaymentsState as? UiState.Success)?.data.orEmpty()
        MobileMoneyUpgradeSheet(
            initialTier = sheetTier,
            strings = strings,
            existingRequests = requests,
            onSubmitPayment = { provider, phone, target ->
                viewModel.submitMobileMoneyPayment(provider, phone, target)
            },
            onDismiss = { upgradeSheetTier = null }
        )
    }

    if (showInstallPortal) {
        BrowserInstallPortalDialog(
            settings = installSettings,
            language = language,
            strings = strings,
            onDismiss = { showInstallPortal = false },
            onStatusMessage = { msg -> viewModel.showStatusMessage(msg) }
        )
    }
}

private fun destinationMeta(dest: MainDestination, strings: com.example.ui.i18n.UiStrings) = when (dest) {
    MainDestination.MOVIES -> strings.navMovies to Icons.Filled.Movie
    MainDestination.CONNECT -> strings.navConnect to Icons.Filled.Forum
    MainDestination.DOWNLOADS -> strings.navDownloads to Icons.Filled.CloudDownload
    MainDestination.ADMIN -> strings.navAdmin to Icons.Filled.AdminPanelSettings
}
