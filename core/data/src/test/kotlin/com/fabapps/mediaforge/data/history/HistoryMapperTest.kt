package com.fabapps.mediaforge.data.history

import com.fabapps.mediaforge.domain.history.HistoryEntry
import com.fabapps.mediaforge.domain.history.HistoryKind
import com.fabapps.mediaforge.domain.model.OutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMapperTest {
    private val video = HistoryEntry(
        id = "a",
        kind = HistoryKind.VIDEO,
        title = "Big Buck Bunny",
        sourceName = "YouTube",
        sourceUrl = "https://youtu.be/x",
        format = "MP4",
        maxHeight = 1080,
        file = OutputFile("content://media/1", "Big Buck Bunny.mp4", "video/mp4", 42),
        thumbnailUrl = "https://i.ytimg.com/x.jpg",
        completedAt = 20,
    )

    @Test
    fun everyKindRoundTrips() {
        val entries = listOf(
            video,
            video.copy(id = "b", kind = HistoryKind.AUDIO, format = "MP3", maxHeight = null, bitrateKbps = 320),
            video.copy(
                id = "c",
                kind = HistoryKind.CONVERSION,
                sourceName = null,
                sourceUrl = null,
                thumbnailUrl = null,
            ),
        )
        entries.forEach { assertEquals(it, HistoryMapper.toDomain(HistoryMapper.toEntity(it))) }
    }

    @Test
    fun unknownKindIsDropped() {
        assertNull(HistoryMapper.toDomain(HistoryMapper.toEntity(video).copy(kind = "HOLOGRAM")))
    }
}
