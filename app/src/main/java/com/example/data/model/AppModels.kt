package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MovieTier {
    FREE, VIP, PREMIUM;

    companion object {
        fun fromRaw(raw: String?): MovieTier =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FREE
    }
}

enum class UserRole {
    FREE, VIP, PREMIUM, ADMIN;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FREE
    }
}

enum class VideoLinkType(val label: String) {
    DIRECT_MP4("Direct MP4 / HLS Stream"),
    YOUTUBE("YouTube Video"),
    VIMEO("Vimeo Video"),
    GOOGLE_DRIVE("Google Drive Video"),
    HOSTED_WEB("Hosted Video URL"),
    BUNDLED_CLIP("Bundled Showcase Clip");

    companion object {
        fun fromRaw(raw: String?): VideoLinkType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HOSTED_WEB
    }
}

enum class ChatMediaType {
    NONE, IMAGE, VIDEO;

    companion object {
        fun fromRaw(raw: String?): ChatMediaType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class MobileMoneyProvider(val displayName: String, val ussdCode: String) {
    MPESA("Vodacom M-Pesa", "*150*00#"),
    TIGO_PESA("Mixx by Yas (Tigo Pesa)", "*150*01#"),
    AIRTEL_MONEY("Airtel Money", "*150*60#");

    companion object {
        fun fromRaw(raw: String?): MobileMoneyProvider =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MPESA
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val role: String = "FREE",
    val subscriptionTier: String = "FREE",
    val preferredLanguage: String = "EN",
    val banned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedRole(): UserRole = UserRole.fromRaw(role)
    fun resolvedTier(): MovieTier = MovieTier.fromRaw(subscriptionTier)
    fun isAdminUser(): Boolean = resolvedRole() == UserRole.ADMIN
    fun canWatch(movieTier: MovieTier): Boolean {
        if (isAdminUser()) return true
        val userTier = resolvedTier()
        return when (movieTier) {
            MovieTier.FREE -> true
            MovieTier.VIP -> userTier == MovieTier.VIP || userTier == MovieTier.PREMIUM
            MovieTier.PREMIUM -> userTier == MovieTier.PREMIUM
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserDirectoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "FREE",
    val subscriptionTier: String = "FREE",
    val discoverable: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Movie(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val tier: String = "FREE",
    val videoUrl: String = "",
    val resolvedStreamUrl: String? = null,
    val linkType: String = "DIRECT_MP4",
    val posterUrl: String? = null,
    val genre: String = "Drama",
    val durationMinutes: Long = 90L,
    val driveAccountSlot: Long = 0L,
    val driveFileId: String? = null,
    val published: Boolean = true,
    val addedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedTier(): MovieTier = MovieTier.fromRaw(tier)
    fun resolvedLinkType(): VideoLinkType = VideoLinkType.fromRaw(linkType)
}

@Keep
@IgnoreExtraProperties
data class ChatGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val topicTag: String? = null,
    val createdByUid: String = "",
    val active: Boolean = true,
    val memberCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderTier: String = "FREE",
    val text: String = "",
    val mediaType: String = "NONE",
    val mediaUrl: String? = null,
    val thumbnailData: String? = null,
    val groupActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedMediaType(): ChatMediaType = ChatMediaType.fromRaw(mediaType)
}

@Keep
@IgnoreExtraProperties
data class PrivateChat(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val mediaType: String = "NONE",
    val mediaUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedMediaType(): ChatMediaType = ChatMediaType.fromRaw(mediaType)
}

@Keep
@IgnoreExtraProperties
data class PaymentRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val provider: String = "MPESA",
    val phoneNumber: String = "",
    val targetTier: String = "VIP",
    val amountTzs: Long = 10000L,
    val referenceCode: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DriveAccountSlot(
    @DocumentId val id: String = "",
    val slotNumber: Long = 1L,
    val accountLabel: String = "",
    val accountEmail: String = "",
    val folderId: String? = null,
    val status: String = "DISCONNECTED",
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GitHubConfig(
    @DocumentId val id: String = "",
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val latestCommitSha: String = "",
    val latestCommitMessage: String = "",
    val remoteVersion: String = "1.0.0",
    val updateNotes: String? = null,
    val status: String = "DISCONNECTED",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class OfflineMovieItem(
    val movieId: String,
    val title: String,
    val description: String,
    val tier: String,
    val genre: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val downloadedAtMillis: Long
)

data class DownloadProgressState(
    val movieId: String,
    val title: String,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
