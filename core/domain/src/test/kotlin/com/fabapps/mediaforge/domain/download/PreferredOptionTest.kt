package com.fabapps.mediaforge.domain.download

import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredOptionTest {
    private fun video(height: Int?) = VideoOption(height, null, DownloadSelection.Video(), null, false, true)

    private val heights = listOf(2160, 1080, 720, 360).map(::video)

    @Test
    fun highestQualityThatDoesNotExceedThePreference() {
        assertEquals(0, VideoOptions.preferredIndex(heights, null))
        assertEquals(1, VideoOptions.preferredIndex(heights, 1080))
        assertEquals(2, VideoOptions.preferredIndex(heights, 1000))
        assertEquals(0, VideoOptions.preferredIndex(heights, 4320))
    }

    @Test
    fun allAboveThePreferenceTakesTheLowest() {
        assertEquals(3, VideoOptions.preferredIndex(heights, 240))
        assertEquals(0, VideoOptions.preferredIndex(listOf(video(null)), 720))
        assertEquals(0, VideoOptions.preferredIndex(emptyList(), 720))
    }

    @Test
    fun preferredAudioFormatOrTheFirst() {
        val options = listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS).map {
            AudioOption(it, null, null, false, emptyList(), null, null)
        }
        assertEquals(2, AudioOptions.preferredIndex(options, AudioFormat.OPUS))
        assertEquals(0, AudioOptions.preferredIndex(options, AudioFormat.FLAC))
    }
}
