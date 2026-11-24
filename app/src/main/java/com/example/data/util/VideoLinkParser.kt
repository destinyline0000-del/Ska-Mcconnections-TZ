package com.example.data.util

import android.net.Uri
import com.example.data.model.VideoLinkType

data class ParsedVideoLink(
    val originalUrl: String,
    val linkType: VideoLinkType,
    val playableUrl: String,
    val embedUrl: String?,
    val extractedId: String?,
    val isSafeScheme: Boolean,
    val supportsDirectDownload: Boolean
)

object VideoLinkParser {

    private val youtubeRegex = Regex(
        "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})",
        RegexOption.IGNORE_CASE
    )
    private val vimeoRegex = Regex(
        "(?:vimeo\\.com/|player\\.vimeo\\.com/video/)([0-9]{4,15})",
        RegexOption.IGNORE_CASE
    )
    private val driveFileRegex = Regex(
        "drive\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:export=[^&]+&)?id=)([A-Za-z0-9_-]{10,80})",
        RegexOption.IGNORE_CASE
    )
    private val driveIdParamRegex = Regex("[?&]id=([A-Za-z0-9_-]{10,80})")

    fun parse(rawUrl: String): ParsedVideoLink {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return ParsedVideoLink(
                originalUrl = "",
                linkType = VideoLinkType.HOSTED_WEB,
                playableUrl = "",
                embedUrl = null,
                extractedId = null,
                isSafeScheme = false,
                supportsDirectDownload = false
            )
        }

        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.HOSTED_WEB,
                playableUrl = "",
                embedUrl = null,
                extractedId = null,
                isSafeScheme = false,
                supportsDirectDownload = false
            )
        }

        if (lower.startsWith("android.resource://") || lower.startsWith("bundled://")) {
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.BUNDLED_CLIP,
                playableUrl = trimmed,
                embedUrl = null,
                extractedId = "sample_showcase",
                isSafeScheme = true,
                supportsDirectDownload = true
            )
        }

        if (lower.startsWith("file://")) {
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.DIRECT_MP4,
                playableUrl = trimmed,
                embedUrl = null,
                extractedId = null,
                isSafeScheme = true,
                supportsDirectDownload = false
            )
        }

        // Check YouTube
        youtubeRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { ytId ->
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.YOUTUBE,
                playableUrl = "https://www.youtube.com/embed/$ytId?playsinline=1&rel=0",
                embedUrl = "https://www.youtube.com/embed/$ytId?playsinline=1&rel=0",
                extractedId = ytId,
                isSafeScheme = true,
                supportsDirectDownload = false
            )
        }

        // Check Vimeo
        vimeoRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { vimeoId ->
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.VIMEO,
                playableUrl = "https://player.vimeo.com/video/$vimeoId",
                embedUrl = "https://player.vimeo.com/video/$vimeoId",
                extractedId = vimeoId,
                isSafeScheme = true,
                supportsDirectDownload = false
            )
        }

        // Check Google Drive
        if (lower.contains("drive.google.com") || lower.contains("docs.google.com")) {
            val fileId = driveFileRegex.find(trimmed)?.groupValues?.getOrNull(1)
                ?: driveIdParamRegex.find(trimmed)?.groupValues?.getOrNull(1)
            val directStream = if (fileId != null) {
                "https://drive.google.com/uc?export=download&id=$fileId"
            } else {
                trimmed
            }
            val previewEmbed = if (fileId != null) {
                "https://drive.google.com/file/d/$fileId/preview"
            } else {
                trimmed
            }
            return ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.GOOGLE_DRIVE,
                playableUrl = directStream,
                embedUrl = previewEmbed,
                extractedId = fileId,
                isSafeScheme = lower.startsWith("https://") || lower.startsWith("http://"),
                supportsDirectDownload = fileId != null
            )
        }

        // Check Direct MP4 / HLS / WebM / MKV
        val pathLower = trimmed.substringBefore('?').substringBefore('#').lowercase()
        val isDirectMedia = pathLower.endsWith(".mp4") ||
            pathLower.endsWith(".m3u8") ||
            pathLower.endsWith(".webm") ||
            pathLower.endsWith(".mkv") ||
            pathLower.endsWith(".mov") ||
            lower.contains(".mp4?") ||
            lower.contains(".m3u8?")

        val safeHttp = lower.startsWith("https://") || lower.startsWith("http://")
        return if (isDirectMedia) {
            ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.DIRECT_MP4,
                playableUrl = trimmed,
                embedUrl = null,
                extractedId = null,
                isSafeScheme = safeHttp,
                supportsDirectDownload = safeHttp && !pathLower.endsWith(".m3u8")
            )
        } else {
            ParsedVideoLink(
                originalUrl = trimmed,
                linkType = VideoLinkType.HOSTED_WEB,
                playableUrl = trimmed,
                embedUrl = trimmed,
                extractedId = null,
                isSafeScheme = safeHttp,
                supportsDirectDownload = safeHttp
            )
        }
    }
}
