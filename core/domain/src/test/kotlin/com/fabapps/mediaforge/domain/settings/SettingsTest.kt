package com.fabapps.mediaforge.domain.settings

import com.fabapps.mediaforge.domain.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    @Test
    fun defaultsAreAlreadyNormal() {
        assertEquals(Settings(), Settings().normalized())
    }

    @Test
    fun simultaneousDownloadsStayBetweenOneAndThree() {
        assertEquals(1, Settings(maxDownloads = 0).normalized().maxDownloads)
        assertEquals(3, Settings(maxDownloads = 10).normalized().maxDownloads)
        assertEquals(2, Settings(maxDownloads = 2).normalized().maxDownloads)
    }

    @Test
    fun unknownQualityGoesBackToBest() {
        assertNull(Settings(maxHeight = 999).normalized().maxHeight)
        assertEquals(720, Settings(maxHeight = 720).normalized().maxHeight)
    }

    @Test
    fun bitrateMustBelongToTheFormat() {
        assertEquals(320, Settings(audioFormat = AudioFormat.MP3, audioBitrateKbps = 320).normalized().audioBitrateKbps)
        assertNull(Settings(audioFormat = AudioFormat.OPUS, audioBitrateKbps = 320).normalized().audioBitrateKbps)
        assertNull(Settings(audioFormat = AudioFormat.FLAC, audioBitrateKbps = 128).normalized().audioBitrateKbps)
    }

    @Test
    fun destinationMustBeAContentUri() {
        assertNull(Settings(destinationTree = "/sdcard/Download").normalized().destinationTree)
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertEquals(tree, Settings(destinationTree = tree).normalized().destinationTree)
    }
}
