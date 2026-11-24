package com.example

import com.example.data.model.VideoLinkType
import com.example.data.util.VideoLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLinkParserTest {

    @Test
    fun detectsDirectMp4AndHls() {
        val mp4 = VideoLinkParser.parse("https://cdn.example.com/movies/bongo_film.mp4")
        assertEquals(VideoLinkType.DIRECT_MP4, mp4.linkType)
        assertTrue(mp4.isSafeScheme)
        assertTrue(mp4.supportsDirectDownload)

        val hls = VideoLinkParser.parse("https://cdn.example.com/live/stream.m3u8?token=abc")
        assertEquals(VideoLinkType.DIRECT_MP4, hls.linkType)
        assertFalse(hls.supportsDirectDownload)
    }

    @Test
    fun detectsYouTubeLinks() {
        val watch = VideoLinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(VideoLinkType.YOUTUBE, watch.linkType)
        assertEquals("dQw4w9WgXcQ", watch.extractedId)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ?playsinline=1&rel=0", watch.embedUrl)

        val short = VideoLinkParser.parse("https://youtu.be/abc123XYZ")
        assertEquals(VideoLinkType.YOUTUBE, short.linkType)
        assertEquals("abc123XYZ", short.extractedId)
    }

    @Test
    fun detectsVimeoLinks() {
        val vimeo = VideoLinkParser.parse("https://vimeo.com/76979871")
        assertEquals(VideoLinkType.VIMEO, vimeo.linkType)
        assertEquals("76979871", vimeo.extractedId)
        assertEquals("https://player.vimeo.com/video/76979871", vimeo.embedUrl)
    }

    @Test
    fun detectsGoogleDriveShareLinks() {
        val drive = VideoLinkParser.parse("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing")
        assertEquals(VideoLinkType.GOOGLE_DRIVE, drive.linkType)
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz", drive.extractedId)
        assertEquals("https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz", drive.playableUrl)
        assertEquals("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/preview", drive.embedUrl)
        assertTrue(drive.supportsDirectDownload)
    }

    @Test
    fun rejectsUnsafeSchemes() {
        val xss = VideoLinkParser.parse("javascript:alert(1)")
        assertFalse(xss.isSafeScheme)
    }
}
