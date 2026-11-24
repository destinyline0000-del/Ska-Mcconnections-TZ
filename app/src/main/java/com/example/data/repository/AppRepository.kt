package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.R
import com.example.data.model.*
import com.example.data.util.OperationType
import com.example.data.util.VideoLinkParser
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AppRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun isBootstrappedAdmin(user: FirebaseUser?): Boolean =
        user != null && user.email == "destinyline0000@gmail.com" && user.isEmailVerified

    private fun <T> safeSnapshotFlow(
        path: String,
        flowProvider: () -> Flow<T>
    ): Flow<T> = flow {
        val uid = requireUserId()
        emitAll(
            flowProvider().catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    suspend fun ensureUserProfileInitialized(user: FirebaseUser, preferredLanguage: String): Result<Unit> {
        val uid = user.uid
        val userPath = "users/$uid"
        val dirPath = "user_directory/$uid"
        return try {
            val userDocRef = db.collection("users").document(uid)
            val existingSnap = userDocRef.get().await()
            val bootstrapAdmin = isBootstrappedAdmin(user)

            if (!existingSnap.exists()) {
                val initialRole = if (bootstrapAdmin) "ADMIN" else "FREE"
                val initialTier = if (bootstrapAdmin) "PREMIUM" else "FREE"
                val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80)
                    ?: user.email?.substringBefore("@")?.take(80)
                    ?: "TZ Viewer"

                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to user.email?.take(120),
                    "photoUrl" to user.photoUrl?.toString()?.take(1000),
                    "role" to initialRole,
                    "subscriptionTier" to initialTier,
                    "preferredLanguage" to (if (preferredLanguage == "SW") "SW" else "EN"),
                    "banned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                userDocRef.set(profilePayload).await()

                val dirPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "photoUrl" to user.photoUrl?.toString()?.take(1000),
                    "role" to initialRole,
                    "subscriptionTier" to initialTier,
                    "discoverable" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                db.collection("user_directory").document(uid).set(dirPayload).await()

                if (bootstrapAdmin) {
                    seedInitialCatalogIfEmpty(uid)
                }
            } else {
                val dirSnap = db.collection("user_directory").document(uid).get().await()
                if (!dirSnap.exists()) {
                    val name = existingSnap.getString("displayName") ?: "TZ Viewer"
                    val role = existingSnap.getString("role") ?: "FREE"
                    val tier = existingSnap.getString("subscriptionTier") ?: "FREE"
                    val photo = existingSnap.getString("photoUrl")
                    val dirPayload = mapOf(
                        "userId" to uid,
                        "displayName" to name,
                        "photoUrl" to photo,
                        "role" to role,
                        "subscriptionTier" to tier,
                        "discoverable" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null } as Map<String, Any>
                    db.collection("user_directory").document(uid).set(dirPayload).await()
                }
                if (bootstrapAdmin) {
                    seedInitialCatalogIfEmpty(uid)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$userPath / $dirPath")
            Result.failure(e)
        }
    }

    private suspend fun seedInitialCatalogIfEmpty(adminUid: String) {
        try {
            val moviesSnap = db.collection("movies").whereEqualTo("published", true).limit(1).get().await()
            if (moviesSnap.isEmpty) {
                val seedMovies = listOf(
                    mapOf(
                        "title" to "Ska TZ Showcase Reel",
                        "description" to "Official Ska Macconnections TZ preview reel. Plays smoothly online and saves offline for Bongo & East Africa cinema fans.",
                        "tier" to "FREE",
                        "videoUrl" to "bundled://sample_showcase",
                        "resolvedStreamUrl" to "bundled://sample_showcase",
                        "linkType" to "BUNDLED_CLIP",
                        "genre" to "Showcase",
                        "durationMinutes" to 1L,
                        "driveAccountSlot" to 1L,
                        "published" to true,
                        "addedByUid" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "title" to "Dar es Salaam Nights",
                        "description" to "An upbeat urban drama set along Kariakoo and Coco Beach, following two young filmmakers chasing their big break.",
                        "tier" to "FREE",
                        "videoUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        "resolvedStreamUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        "linkType" to "DIRECT_MP4",
                        "genre" to "Bongo Drama",
                        "durationMinutes" to 92L,
                        "driveAccountSlot" to 1L,
                        "published" to true,
                        "addedByUid" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "title" to "Kilimanjaro Horizon (VIP)",
                        "description" to "High-altitude thriller filmed around Moshi and Arusha. Exclusively unlocked for VIP and Premium subscribers.",
                        "tier" to "VIP",
                        "videoUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        "resolvedStreamUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        "linkType" to "DIRECT_MP4",
                        "genre" to "Thriller",
                        "durationMinutes" to 108L,
                        "driveAccountSlot" to 2L,
                        "published" to true,
                        "addedByUid" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "title" to "Zanzibar Crown (Premium)",
                        "description" to "Premier historical epic from Stone Town in crisp HD. Available to Premium tier members.",
                        "tier" to "PREMIUM",
                        "videoUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        "resolvedStreamUrl" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        "linkType" to "DIRECT_MP4",
                        "genre" to "Epic",
                        "durationMinutes" to 124L,
                        "driveAccountSlot" to 3L,
                        "published" to true,
                        "addedByUid" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                seedMovies.forEach { payload ->
                    db.collection("movies").document().set(payload).await()
                }
            }

            val groupsSnap = db.collection("chat_groups").whereEqualTo("active", true).limit(1).get().await()
            if (groupsSnap.isEmpty) {
                val groups = listOf(
                    mapOf(
                        "name" to "Bongo Movies & Cinema TZ",
                        "description" to "Discuss new Tanzanian releases, share clips, and drop movie reviews!",
                        "topicTag" to "Cinema",
                        "createdByUid" to adminUid,
                        "active" to true,
                        "memberCount" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "name" to "Dar & Zanzibar VIP Lounge",
                        "description" to "Social hangout for VIP & Premium members across Tanzania.",
                        "topicTag" to "Social",
                        "createdByUid" to adminUid,
                        "active" to true,
                        "memberCount" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                groups.forEach { payload ->
                    db.collection("chat_groups").document().set(payload).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "seed_initial")
        }
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserProfile?> =
        safeSnapshotFlow("users/$uid") {
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    fun observePublishedMovies(): Flow<List<Movie>> =
        safeSnapshotFlow("movies") {
            db.collection("movies")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Movie::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeChatGroups(): Flow<List<ChatGroup>> =
        safeSnapshotFlow("chat_groups") {
            db.collection("chat_groups")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeGroupMessages(groupId: String): Flow<List<GroupMessage>> =
        safeSnapshotFlow("chat_groups/$groupId/messages") {
            db.collection("chat_groups").document(groupId).collection("messages")
                .whereEqualTo("groupActive", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeUserDirectory(): Flow<List<UserDirectoryEntry>> =
        safeSnapshotFlow("user_directory") {
            db.collection("user_directory")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserDirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
        }

    fun observeMyPrivateChats(uid: String): Flow<List<PrivateChat>> =
        safeSnapshotFlow("private_chats") {
            db.collection("private_chats")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PrivateChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeAllPrivateChatsForAdmin(): Flow<List<PrivateChat>> =
        safeSnapshotFlow("private_chats_admin") {
            db.collection("private_chats")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PrivateChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observePrivateMessages(chatId: String, uid: String, isAdmin: Boolean): Flow<List<PrivateMessage>> =
        safeSnapshotFlow("private_chats/$chatId/messages") {
            val baseCol = db.collection("private_chats").document(chatId).collection("messages")
            val query = if (isAdmin) baseCol else baseCol.whereArrayContains("participantIds", uid)
            query.snapshots()
                .map { snap ->
                    snap.toObjects(PrivateMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeMyPaymentRequests(uid: String): Flow<List<PaymentRequest>> =
        safeSnapshotFlow("payment_requests") {
            db.collection("payment_requests")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PaymentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeAllPaymentRequestsForAdmin(): Flow<List<PaymentRequest>> =
        safeSnapshotFlow("payment_requests_admin") {
            db.collection("payment_requests")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PaymentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> =
        safeSnapshotFlow("users_admin") {
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
        }

    fun observeDriveAccountsForAdmin(): Flow<List<DriveAccountSlot>> =
        safeSnapshotFlow("drive_accounts") {
            db.collection("drive_accounts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(DriveAccountSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.slotNumber }
                }
        }

    fun observeGitHubConfigForAdmin(): Flow<GitHubConfig?> =
        safeSnapshotFlow("github_config/main") {
            db.collection("github_config").document("main")
                .snapshots()
                .map { it.toObject(GitHubConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    // --- Mutations ---

    suspend fun updateUserLanguage(languageCode: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val validCode = if (languageCode == "SW") "SW" else "EN"
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to validCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createChatGroup(name: String, description: String, topicTag: String): Result<String> {
        var path = "chat_groups"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            val cleanDesc = description.trim().take(300)
            require(cleanName.isNotEmpty() && cleanDesc.isNotEmpty()) { "Group name and description are required." }
            val ref = db.collection("chat_groups").document()
            path = ref.path
            val payload = mapOf(
                "name" to cleanName,
                "description" to cleanDesc,
                "topicTag" to topicTag.trim().take(40).ifBlank { null },
                "createdByUid" to uid,
                "active" to true,
                "memberCount" to 1L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        groupId: String,
        senderName: String,
        senderTier: String,
        text: String,
        mediaType: ChatMediaType = ChatMediaType.NONE,
        mediaUrl: String? = null,
        thumbnailData: String? = null
    ): Result<Unit> {
        val path = "chat_groups/$groupId/messages"
        return try {
            val uid = requireUserId()
            require(groupId.isNotBlank()) { "Group ID required" }
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotBlank() || !mediaUrl.isNullOrBlank() || !thumbnailData.isNullOrBlank()) {
                "Message cannot be empty."
            }
            val validTier = if (senderTier in listOf("FREE", "VIP", "PREMIUM", "ADMIN")) senderTier else "FREE"
            val msgRef = db.collection("chat_groups").document(groupId).collection("messages").document()
            val payload = mapOf(
                "groupId" to groupId,
                "senderId" to uid,
                "senderName" to senderName.trim().take(80).ifBlank { "Member" },
                "senderTier" to validTier,
                "text" to cleanText,
                "mediaType" to mediaType.name,
                "mediaUrl" to mediaUrl?.take(2000),
                "thumbnailData" to thumbnailData?.take(340000),
                "groupActive" to true,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun compressImageToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
            val maxDim = 640
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return@withContext null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            bmp.recycle()
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= 320_000) b64 else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun openOrCreatePrivateChat(
        myUid: String,
        myName: String,
        otherUser: UserDirectoryEntry
    ): Result<String> {
        val sortedIds = listOf(myUid, otherUser.userId).sorted()
        val chatId = "${sortedIds[0]}_${sortedIds[1]}"
        val path = "private_chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == myUid) { "User session changed" }
            require(otherUser.userId != myUid) { "Cannot start private chat with yourself" }
            val ref = db.collection("private_chats").document(chatId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "participantIds" to sortedIds,
                    "participantNames" to mapOf(
                        myUid to myName.take(80).ifBlank { "Member" },
                        otherUser.userId to otherUser.displayName.take(80).ifBlank { "Member" }
                    ),
                    "lastMessage" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendPrivateMessage(
        chatId: String,
        participantIds: List<String>,
        senderName: String,
        text: String,
        mediaType: ChatMediaType = ChatMediaType.NONE,
        mediaUrl: String? = null
    ): Result<Unit> {
        val path = "private_chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotBlank() || !mediaUrl.isNullOrBlank()) { "Message cannot be empty" }
            require(participantIds.size == 2 && uid in participantIds) { "Invalid participants" }

            val msgRef = db.collection("private_chats").document(chatId).collection("messages").document()
            val msgPayload = mapOf(
                "chatId" to chatId,
                "participantIds" to participantIds,
                "senderId" to uid,
                "senderName" to senderName.trim().take(80).ifBlank { "Member" },
                "text" to cleanText,
                "mediaType" to mediaType.name,
                "mediaUrl" to mediaUrl?.take(2000),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            msgRef.set(msgPayload).await()

            db.collection("private_chats").document(chatId).update(
                mapOf(
                    "lastMessage" to cleanText.ifBlank { "[${mediaType.name}]" }.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitMobileMoneyPayment(
        userName: String,
        provider: MobileMoneyProvider,
        phoneNumber: String,
        targetTier: MovieTier
    ): Result<String> {
        var path = "payment_requests"
        return try {
            val uid = requireUserId()
            val cleanPhone = phoneNumber.trim()
            require(cleanPhone.length in 9..20) { "Enter a valid Tanzanian phone number (e.g. 0754123456)." }
            require(targetTier == MovieTier.VIP || targetTier == MovieTier.PREMIUM) { "Target tier must be VIP or PREMIUM." }
            val amountTzs = if (targetTier == MovieTier.VIP) 10_000L else 25_000L
            val refCode = "TZ${System.currentTimeMillis().toString().takeLast(6)}"
            val docRef = db.collection("payment_requests").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "userName" to userName.trim().take(80).ifBlank { "Subscriber" },
                "provider" to provider.name,
                "phoneNumber" to cleanPhone,
                "targetTier" to targetTier.name,
                "amountTzs" to amountTzs,
                "referenceCode" to refCode,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(refCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Admin Mutations ---

    suspend fun publishMovieByLink(
        title: String,
        description: String,
        tier: MovieTier,
        rawVideoUrl: String,
        posterUrl: String?,
        genre: String,
        durationMinutes: Long,
        driveAccountSlot: Long
    ): Result<Unit> {
        var path = "movies"
        return try {
            val uid = requireUserId()
            val parsed = VideoLinkParser.parse(rawVideoUrl)
            require(parsed.isSafeScheme && parsed.originalUrl.isNotBlank()) { "Provide a valid http(s) or Drive video URL." }
            require(title.trim().isNotBlank() && description.trim().isNotBlank()) { "Title and description are required." }

            val ref = db.collection("movies").document()
            path = ref.path
            val payload = mapOf(
                "title" to title.trim().take(140),
                "description" to description.trim().take(2000),
                "tier" to tier.name,
                "videoUrl" to parsed.originalUrl.take(2000),
                "resolvedStreamUrl" to parsed.playableUrl.take(2000),
                "linkType" to parsed.linkType.name,
                "posterUrl" to posterUrl?.trim()?.take(2000)?.takeIf { it.isNotBlank() },
                "genre" to genre.trim().take(60).ifBlank { "Bongo Cinema" },
                "durationMinutes" to durationMinutes.coerceIn(0L, 1000L),
                "driveAccountSlot" to driveAccountSlot.coerceIn(0L, 5L),
                "driveFileId" to parsed.extractedId?.take(256),
                "published" to true,
                "addedByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMovie(movieId: String): Result<Unit> {
        val path = "movies/$movieId"
        return try {
            requireUserId()
            db.collection("movies").document(movieId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDriveAccountSlot(
        slotNumber: Int,
        accountLabel: String,
        accountEmail: String,
        status: String,
        usedBytes: Long,
        totalBytes: Long,
        folderId: String? = null
    ): Result<Unit> {
        val slotId = "slot_$slotNumber"
        val path = "drive_accounts/$slotId"
        return try {
            requireUserId()
            require(slotNumber in 1..5) { "Drive slot must be 1 to 5" }
            val ref = db.collection("drive_accounts").document(slotId)
            val snap = ref.get().await()
            val payload = mapOf(
                "slotNumber" to slotNumber.toLong(),
                "accountLabel" to accountLabel.trim().take(80).ifBlank { "Drive Storage $slotNumber" },
                "accountEmail" to accountEmail.trim().take(120).ifBlank { "drive$slotNumber@gmail.com" },
                "folderId" to folderId?.trim()?.take(256)?.takeIf { it.isNotBlank() },
                "status" to if (status in listOf("CONNECTED", "ACTIVE", "DISCONNECTED", "ERROR")) status else "ACTIVE",
                "usedBytes" to usedBytes.coerceAtLeast(0L),
                "totalBytes" to totalBytes.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            if (!snap.exists()) {
                ref.set(payload).await()
            } else {
                ref.update(payload.filterKeys { it != "slotNumber" }).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveGitHubConfig(
        owner: String,
        repo: String,
        branch: String,
        sha: String,
        commitMsg: String,
        remoteVersion: String,
        updateNotes: String?,
        status: String
    ): Result<Unit> {
        val path = "github_config/main"
        return try {
            requireUserId()
            val ref = db.collection("github_config").document("main")
            val payload = mapOf(
                "owner" to owner.trim().take(100),
                "repo" to repo.trim().take(120),
                "branch" to branch.trim().take(80).ifBlank { "main" },
                "latestCommitSha" to sha.trim().take(64),
                "latestCommitMessage" to commitMsg.trim().take(300),
                "remoteVersion" to remoteVersion.trim().take(40).ifBlank { "1.0.0" },
                "updateNotes" to updateNotes?.trim()?.take(1000)?.takeIf { it.isNotBlank() },
                "status" to if (status in listOf("CONNECTED", "SYNCED", "DISCONNECTED")) status else "SYNCED",
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun approveOrRejectPayment(request: PaymentRequest, approve: Boolean): Result<Unit> {
        val payPath = "payment_requests/${request.id}"
        return try {
            requireUserId()
            val newStatus = if (approve) "APPROVED" else "REJECTED"
            db.collection("payment_requests").document(request.id).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                updateUserTierAdmin(request.userId, MovieTier.fromRaw(request.targetTier))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserTierAdmin(targetUserId: String, tier: MovieTier): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            val userSnap = db.collection("users").document(targetUserId).get().await()
            val currentRole = userSnap.getString("role") ?: "FREE"
            val nextRole = if (currentRole == "ADMIN") "ADMIN" else tier.name
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "subscriptionTier" to tier.name,
                    "role" to nextRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("user_directory").document(targetUserId).update(
                mapOf(
                    "subscriptionTier" to tier.name,
                    "role" to nextRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
