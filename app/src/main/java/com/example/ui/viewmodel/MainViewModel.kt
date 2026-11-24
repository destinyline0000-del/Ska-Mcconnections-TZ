package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalSecretsAndDownloadsStore
import com.example.data.model.*
import com.example.data.remote.DriveFileItem
import com.example.data.remote.GitHubService
import com.example.data.remote.GoogleDriveService
import com.example.data.remote.VideoDownloadManager
import com.example.data.repository.AppRepository
import com.example.ui.i18n.AppLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AppRepository,
    private val localStore: LocalSecretsAndDownloadsStore,
    private val downloadManager: VideoDownloadManager,
    private val driveService: GoogleDriveService,
    private val gitHubService: GitHubService,
    private val currentUserId: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val language: StateFlow<AppLanguage> = localStore.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.EN)

    val installBannerSettings: StateFlow<com.example.data.local.InstallBannerSettings> = localStore.installBannerSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), com.example.data.local.InstallBannerSettings())

    val offlineMovies: StateFlow<List<OfflineMovieItem>> = localStore.offlineMoviesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val downloadsProgress: StateFlow<Map<String, DownloadProgressState>> = downloadManager.downloadsProgress

    private fun <T> Flow<T>.toUiState(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tag", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $tag"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeCurrentUserProfile(currentUserId).toUiState("profile")

    val moviesState: StateFlow<UiState<List<Movie>>> =
        repository.observePublishedMovies().toUiState("movies")

    val chatGroupsState: StateFlow<UiState<List<ChatGroup>>> =
        repository.observeChatGroups().toUiState("chat_groups")

    val userDirectoryState: StateFlow<UiState<List<UserDirectoryEntry>>> =
        repository.observeUserDirectory().toUiState("user_directory")

    val myPrivateChatsState: StateFlow<UiState<List<PrivateChat>>> =
        repository.observeMyPrivateChats(currentUserId).toUiState("private_chats")

    val myPaymentRequestsState: StateFlow<UiState<List<PaymentRequest>>> =
        repository.observeMyPaymentRequests(currentUserId).toUiState("payment_requests")

    private val isAdminFlow: Flow<Boolean> = userProfileState
        .map { state -> (state as? UiState.Success)?.data?.isAdminUser() == true }
        .distinctUntilChanged()

    val adminAllPrivateChatsState: StateFlow<UiState<List<PrivateChat>>> =
        isAdminFlow.flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeAllPrivateChatsForAdmin().map<List<PrivateChat>, UiState<List<PrivateChat>>> { UiState.Success(it) }
            else flowOf(UiState.Success(emptyList()))
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Admin oversight error"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminAllPaymentsState: StateFlow<UiState<List<PaymentRequest>>> =
        isAdminFlow.flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeAllPaymentRequestsForAdmin().map<List<PaymentRequest>, UiState<List<PaymentRequest>>> { UiState.Success(it) }
            else flowOf(UiState.Success(emptyList()))
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Admin payments error"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminAllUsersState: StateFlow<UiState<List<UserProfile>>> =
        isAdminFlow.flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeAllUsersForAdmin().map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
            else flowOf(UiState.Success(emptyList()))
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Admin users error"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val driveAccountsState: StateFlow<UiState<List<DriveAccountSlot>>> =
        isAdminFlow.flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeDriveAccountsForAdmin().map<List<DriveAccountSlot>, UiState<List<DriveAccountSlot>>> { UiState.Success(it) }
            else flowOf(UiState.Success(emptyList()))
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Drive slots error"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val gitHubConfigState: StateFlow<UiState<GitHubConfig?>> =
        isAdminFlow.flatMapLatest { isAdmin ->
            if (isAdmin) repository.observeGitHubConfigForAdmin().map<GitHubConfig?, UiState<GitHubConfig?>> { UiState.Success(it) }
            else flowOf(UiState.Success(null))
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "GitHub config error"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedGroup = MutableStateFlow<ChatGroup?>(null)
    val selectedGroup: StateFlow<ChatGroup?> = _selectedGroup.asStateFlow()

    val selectedGroupMessages: StateFlow<UiState<List<GroupMessage>>> =
        _selectedGroup.flatMapLatest { group ->
            if (group == null) flowOf(UiState.Success(emptyList()))
            else repository.observeGroupMessages(group.id).map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load group messages"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    private val _selectedPrivateChat = MutableStateFlow<PrivateChat?>(null)
    val selectedPrivateChat: StateFlow<PrivateChat?> = _selectedPrivateChat.asStateFlow()

    val selectedPrivateMessages: StateFlow<UiState<List<PrivateMessage>>> =
        combine(_selectedPrivateChat, isAdminFlow) { chat, isAdmin -> chat to isAdmin }
            .flatMapLatest { (chat, isAdmin) ->
                if (chat == null) flowOf(UiState.Success(emptyList()))
                else repository.observePrivateMessages(chat.id, currentUserId, isAdmin)
                    .map<List<PrivateMessage>, UiState<List<PrivateMessage>>> { UiState.Success(it) }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load private messages"))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    private val _driveSlotFiles = MutableStateFlow<List<DriveFileItem>>(emptyList())
    val driveSlotFiles: StateFlow<List<DriveFileItem>> = _driveSlotFiles.asStateFlow()

    private val _isAdminBusy = MutableStateFlow(false)
    val isAdminBusy: StateFlow<Boolean> = _isAdminBusy.asStateFlow()

    fun bootstrapUser(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        viewModelScope.launch {
            val langCode = language.value.code
            val res = repository.ensureUserProfileInitialized(firebaseUser, langCode)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showStatusMessage(message: String) {
        _statusBanner.value = message
    }

    fun dismissInstallBanner() {
        viewModelScope.launch {
            localStore.setInstallAdDismissed(true)
        }
    }

    fun saveInstallBannerConfig(enabled: Boolean, apkUrl: String, webUrl: String) {
        viewModelScope.launch {
            localStore.saveInstallBannerConfig(enabled, apkUrl, webUrl)
            _statusBanner.value = "Saved browser install ad & phone distribution settings."
        }
    }

    fun pushBrowserPortalToGitHub(context: Context, owner: String, repo: String, branch: String, pat: String) {
        viewModelScope.launch {
            _isAdminBusy.value = true
            if (pat.isNotBlank()) localStore.saveGitHubToken(pat)
            val activePat = pat.ifBlank { localStore.getGitHubToken().orEmpty() }
            val html = try {
                context.assets.open("install_portal.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                _isAdminBusy.value = false
                _statusBanner.value = "Failed to read install_portal.html: ${e.localizedMessage}"
                return@launch
            }
            val res = gitHubService.pushRepositoryFile(
                owner = owner,
                repo = repo,
                branch = branch,
                token = activePat,
                filePath = "index.html",
                commitMessage = "Publish Ska Macconnections TZ browser install ad portal",
                fileContent = html
            )
            _isAdminBusy.value = false
            _statusBanner.value = if (res.isSuccess) {
                "Published index.html (${res.getOrNull()})! Enable GitHub Pages for browser install ads."
            } else {
                "GitHub publish failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            localStore.setLanguage(lang)
            repository.updateUserLanguage(lang.code)
        }
    }

    fun downloadMovie(movie: Movie) {
        viewModelScope.launch {
            val res = downloadManager.downloadMovie(movie)
            _statusBanner.value = if (res.isSuccess) {
                "Saved offline: ${movie.title}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Download failed"
            }
        }
    }

    fun deleteOfflineMovie(item: OfflineMovieItem) {
        viewModelScope.launch {
            downloadManager.deleteOfflineMovie(item)
            _statusBanner.value = "Removed offline copy: ${item.title}"
        }
    }

    fun submitMobileMoneyPayment(
        provider: MobileMoneyProvider,
        phoneNumber: String,
        targetTier: MovieTier
    ) {
        viewModelScope.launch {
            val name = (userProfileState.value as? UiState.Success)?.data?.displayName ?: "Member"
            val res = repository.submitMobileMoneyPayment(name, provider, phoneNumber, targetTier)
            _statusBanner.value = if (res.isSuccess) {
                "Payment ref ${res.getOrNull()} submitted (${provider.displayName}). Pending confirmation."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Payment request failed"
            }
        }
    }

    fun openGroup(group: ChatGroup?) {
        _selectedGroup.value = group
    }

    fun createGroup(name: String, description: String, topicTag: String) {
        viewModelScope.launch {
            val res = repository.createChatGroup(name, description, topicTag)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun sendGroupTextOrMedia(
        context: Context,
        groupId: String,
        text: String,
        pickedUri: Uri?,
        pickedIsVideo: Boolean,
        manualMediaUrl: String?
    ) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val senderName = profile?.displayName ?: "Member"
            val senderTier = profile?.resolvedRole()?.name ?: "FREE"

            var finalType = ChatMediaType.NONE
            var finalUrl = manualMediaUrl?.trim()?.takeIf { it.isNotBlank() }
            var thumbData: String? = null

            if (pickedUri != null) {
                val mime = context.contentResolver.getType(pickedUri) ?: if (pickedIsVideo) "video/mp4" else "image/jpeg"
                val isVideo = pickedIsVideo || mime.startsWith("video/")
                finalType = if (isVideo) ChatMediaType.VIDEO else ChatMediaType.IMAGE

                // Try connected Google Drive slot 1..5 if token exists locally
                var uploadedUrl: String? = null
                for (slot in 1..5) {
                    val token = localStore.getDriveToken(slot)
                    if (!token.isNullOrBlank()) {
                        val fileName = resolveFileName(context, pickedUri) ?: "ska_chat_${System.currentTimeMillis()}"
                        val upRes = driveService.uploadMediaAndShare(context, pickedUri, fileName, mime, token)
                        if (upRes.isSuccess) {
                            uploadedUrl = upRes.getOrNull()?.directStreamUrl
                            break
                        }
                    }
                }
                if (uploadedUrl != null) {
                    finalUrl = uploadedUrl
                } else if (!isVideo) {
                    thumbData = repository.compressImageToBase64(context, pickedUri)
                } else {
                    _statusBanner.value = "Connect a Google Drive slot in Admin or paste a video link to share large videos."
                    return@launch
                }
            } else if (!finalUrl.isNullOrBlank()) {
                val lower = finalUrl.lowercase()
                finalType = if (lower.endsWith(".mp4") || lower.contains("youtube") || lower.contains("vimeo") || lower.contains("drive.google")) {
                    ChatMediaType.VIDEO
                } else {
                    ChatMediaType.IMAGE
                }
            }

            val res = repository.sendGroupMessage(
                groupId = groupId,
                senderName = senderName,
                senderTier = senderTier,
                text = text,
                mediaType = finalType,
                mediaUrl = finalUrl,
                thumbnailData = thumbData
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun openPrivateChatWithUser(other: UserDirectoryEntry) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val myName = profile?.displayName ?: "Member"
            val res = repository.openOrCreatePrivateChat(currentUserId, myName, other)
            val chatId = res.getOrNull()
            if (chatId != null) {
                _selectedPrivateChat.value = PrivateChat(
                    id = chatId,
                    participantIds = listOf(currentUserId, other.userId).sorted(),
                    participantNames = mapOf(currentUserId to myName, other.userId to other.displayName)
                )
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun openExistingPrivateChat(chat: PrivateChat?) {
        _selectedPrivateChat.value = chat
    }

    fun sendPrivateMessage(chat: PrivateChat, text: String, mediaUrl: String? = null) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val myName = profile?.displayName ?: "Member"
            val cleanUrl = mediaUrl?.trim()?.takeIf { it.isNotBlank() }
            val mediaType = if (cleanUrl != null) ChatMediaType.VIDEO else ChatMediaType.NONE
            val res = repository.sendPrivateMessage(
                chatId = chat.id,
                participantIds = chat.participantIds,
                senderName = myName,
                text = text,
                mediaType = mediaType,
                mediaUrl = cleanUrl
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    // --- Admin Actions ---

    fun publishMovieByLink(
        title: String,
        description: String,
        tier: MovieTier,
        rawVideoUrl: String,
        posterUrl: String?,
        genre: String,
        durationMinutes: Long,
        driveSlot: Long
    ) {
        viewModelScope.launch {
            _isAdminBusy.value = true
            val res = repository.publishMovieByLink(
                title = title,
                description = description,
                tier = tier,
                rawVideoUrl = rawVideoUrl,
                posterUrl = posterUrl,
                genre = genre,
                durationMinutes = durationMinutes,
                driveAccountSlot = driveSlot
            )
            _isAdminBusy.value = false
            _statusBanner.value = if (res.isSuccess) {
                "Published \"$title\" to ${tier.name} section!"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to publish movie"
            }
        }
    }

    fun deleteMovie(movieId: String) {
        viewModelScope.launch {
            val res = repository.deleteMovie(movieId)
            if (res.isFailure) _statusBanner.value = res.exceptionOrNull()?.localizedMessage
        }
    }

    fun connectDriveSlot(slotNumber: Int, label: String, accessToken: String) {
        viewModelScope.launch {
            _isAdminBusy.value = true
            val verifyRes = driveService.verifyAccount(accessToken)
            if (verifyRes.isSuccess) {
                val info = verifyRes.getOrThrow()
                localStore.saveDriveToken(slotNumber, accessToken)
                val saveRes = repository.saveDriveAccountSlot(
                    slotNumber = slotNumber,
                    accountLabel = label.ifBlank { info.displayName },
                    accountEmail = info.email,
                    status = "ACTIVE",
                    usedBytes = info.usedBytes,
                    totalBytes = info.totalBytes
                )
                _statusBanner.value = if (saveRes.isSuccess) {
                    "Drive Slot #$slotNumber connected (${info.email})"
                } else {
                    saveRes.exceptionOrNull()?.localizedMessage
                }
            } else {
                _statusBanner.value = "Drive verification failed: ${verifyRes.exceptionOrNull()?.localizedMessage}"
            }
            _isAdminBusy.value = false
        }
    }

    fun disconnectDriveSlot(slot: DriveAccountSlot) {
        viewModelScope.launch {
            localStore.saveDriveToken(slot.slotNumber.toInt(), "")
            repository.saveDriveAccountSlot(
                slotNumber = slot.slotNumber.toInt(),
                accountLabel = slot.accountLabel,
                accountEmail = slot.accountEmail,
                status = "DISCONNECTED",
                usedBytes = slot.usedBytes,
                totalBytes = slot.totalBytes,
                folderId = slot.folderId
            )
            _statusBanner.value = "Drive Slot #${slot.slotNumber} disconnected"
        }
    }

    fun loadDriveVideosForSlot(slotNumber: Int) {
        viewModelScope.launch {
            val token = localStore.getDriveToken(slotNumber)
            if (token.isNullOrBlank()) {
                _statusBanner.value = "No active local OAuth token for Drive Slot #$slotNumber."
                return@launch
            }
            _isAdminBusy.value = true
            val res = driveService.listVideoFiles(token)
            if (res.isSuccess) {
                _driveSlotFiles.value = res.getOrDefault(emptyList())
                _statusBanner.value = "Loaded ${_driveSlotFiles.value.size} videos from Drive Slot #$slotNumber"
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
            _isAdminBusy.value = false
        }
    }

    fun uploadVideoToDriveSlot(
        context: Context,
        slotNumber: Int,
        uri: Uri,
        onUploadedLink: (String) -> Unit
    ) {
        viewModelScope.launch {
            val token = localStore.getDriveToken(slotNumber)
            if (token.isNullOrBlank()) {
                _statusBanner.value = "Connect Drive Slot #$slotNumber with a Drive OAuth token first."
                return@launch
            }
            _isAdminBusy.value = true
            val name = resolveFileName(context, uri) ?: "ska_movie_${System.currentTimeMillis()}.mp4"
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val res = driveService.uploadMediaAndShare(context, uri, name, mime, token)
            _isAdminBusy.value = false
            if (res.isSuccess) {
                val item = res.getOrThrow()
                _statusBanner.value = "Uploaded to Drive Slot #$slotNumber with share link!"
                onUploadedLink(item.shareUrl)
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun pullGitHubUpdates(owner: String, repo: String, branch: String, pat: String?) {
        viewModelScope.launch {
            _isAdminBusy.value = true
            if (!pat.isNullOrBlank()) {
                localStore.saveGitHubToken(pat)
            }
            val activePat = pat?.takeIf { it.isNotBlank() } ?: localStore.getGitHubToken()
            val res = gitHubService.pullRepoUpdates(owner, repo, branch, activePat)
            if (res.isSuccess) {
                val status = res.getOrThrow()
                repository.saveGitHubConfig(
                    owner = status.owner,
                    repo = status.repo,
                    branch = status.branch,
                    sha = status.latestCommitSha,
                    commitMsg = status.latestCommitMessage,
                    remoteVersion = status.remoteVersion,
                    updateNotes = status.updateNotes,
                    status = "SYNCED"
                )
                _statusBanner.value = "Pulled ${status.owner}/${status.repo}@${status.latestCommitSha} (${status.remoteVersion})"
            } else {
                _statusBanner.value = "GitHub Pull failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isAdminBusy.value = false
        }
    }

    fun pushGitHubCatalogUpdate(owner: String, repo: String, branch: String, pat: String, commitMsg: String) {
        viewModelScope.launch {
            _isAdminBusy.value = true
            if (pat.isNotBlank()) localStore.saveGitHubToken(pat)
            val activePat = pat.ifBlank { localStore.getGitHubToken().orEmpty() }
            val movies = (moviesState.value as? UiState.Success)?.data.orEmpty()
            val manifest = JSONObject().apply {
                put("app", "Ska Macconnections TZ")
                put("version", "1.0.${movies.size}")
                put("notes", commitMsg.ifBlank { "Catalog sync (${movies.size} titles)" })
                put("movieCount", movies.size)
                put("movies", JSONArray().apply {
                    movies.forEach { m ->
                        put(JSONObject().apply {
                            put("title", m.title)
                            put("tier", m.tier)
                            put("linkType", m.linkType)
                            put("driveAccountSlot", m.driveAccountSlot)
                        })
                    }
                })
            }.toString(2)

            val res = gitHubService.pushCatalogManifest(owner, repo, branch, activePat, commitMsg, manifest)
            if (res.isSuccess) {
                val sha = res.getOrThrow()
                repository.saveGitHubConfig(
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    sha = sha,
                    commitMsg = commitMsg.ifBlank { "Update catalog manifest" },
                    remoteVersion = "1.0.${movies.size}",
                    updateNotes = "Pushed catalog manifest from Admin Console",
                    status = "SYNCED"
                )
                _statusBanner.value = "Pushed update commit $sha to $owner/$repo!"
            } else {
                _statusBanner.value = "GitHub Push failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isAdminBusy.value = false
        }
    }

    fun approveOrRejectPayment(request: PaymentRequest, approve: Boolean) {
        viewModelScope.launch {
            val res = repository.approveOrRejectPayment(request, approve)
            _statusBanner.value = if (res.isSuccess) {
                if (approve) "Approved ${request.targetTier} for ${request.userName}" else "Rejected payment ${request.referenceCode}"
            } else {
                res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun updateUserTier(userId: String, tier: MovieTier) {
        viewModelScope.launch {
            val res = repository.updateUserTierAdmin(userId, tier)
            if (res.isFailure) _statusBanner.value = res.exceptionOrNull()?.localizedMessage
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val TAG = "MainVM"
    }
}
