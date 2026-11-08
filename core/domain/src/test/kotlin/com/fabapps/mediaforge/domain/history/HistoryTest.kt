package com.fabapps.mediaforge.domain.history

import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.ImageFormat
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.VideoContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryTest {
    private val file = OutputFile("content://media/1", "Big Buck Bunny.mp4", "video/mp4", 42)

    private fun download(selection: DownloadSelection, status: JobStatus = JobStatus.COMPLETED) = Job(
        JobRequest.Download(DownloadRequest("d", "https://youtu.be/x", selection, "Big Buck Bunny")),
        title = "Big Buck Bunny",
        sourceName = "YouTube",
        thumbnailUrl = "https://i.ytimg.com/x.jpg",
        status = status,
        output = file.takeIf { status == JobStatus.COMPLETED },
        createdAt = 10,
        updatedAt = 20,
    )

    private fun conversion(target: ConversionTarget) = Job(
        JobRequest.Conversion(ConversionRequest("c", "content://docs/1", target, "Tema")),
        title = "Tema",
        status = JobStatus.COMPLETED,
        output = file,
        createdAt = 10,
        updatedAt = 30,
    )

    @Test
    fun videoDownloadKeepsLinkQualityAndFile() {
        val entry = HistoryEntry.from(download(DownloadSelection.Video(maxHeight = 1080)))!!
        assertEquals(HistoryKind.VIDEO, entry.kind)
        assertEquals("https://youtu.be/x", entry.sourceUrl)
        assertEquals("YouTube", entry.sourceName)
        assertEquals("MP4", entry.format)
        assertEquals(1080, entry.maxHeight)
        assertEquals(file, entry.file)
        assertEquals("https://i.ytimg.com/x.jpg", entry.thumbnailUrl)
        assertEquals(20L, entry.completedAt)
    }

    @Test
    fun audioDownloadKeepsFormatAndBitrate() {
        val entry = HistoryEntry.from(download(DownloadSelection.Audio(AudioFormat.MP3, 320)))!!
        assertEquals(HistoryKind.AUDIO, entry.kind)
        assertEquals("MP3", entry.format)
        assertEquals(320, entry.bitrateKbps)
        assertNull(entry.maxHeight)
    }

    @Test
    fun conversionHasNoLinkNorSite() {
        val entry = HistoryEntry.from(conversion(ConversionTarget.Video(VideoContainer.WEBM, 720)))!!
        assertEquals(HistoryKind.CONVERSION, entry.kind)
        assertNull(entry.sourceUrl)
        assertNull(entry.sourceName)
        assertEquals("WEBM", entry.format)
        assertEquals(720, entry.maxHeight)
        assertEquals(30L, entry.completedAt)
        val audio = HistoryEntry.from(conversion(ConversionTarget.Audio(AudioFormat.FLAC)))!!
        assertEquals("FLAC", audio.format)
        val image = HistoryEntry.from(conversion(ConversionTarget.Image(3.0, ImageFormat.PNG)))!!
        assertEquals(HistoryKind.CONVERSION, image.kind)
        assertEquals("PNG", image.format)
    }

    @Test
    fun onlyCompletedJobsWithAFileEnterTheHistory() {
        listOf(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.SAVING, JobStatus.FAILED, JobStatus.CANCELLED).forEach {
            assertNull(HistoryEntry.from(download(DownloadSelection.Video(), status = it)))
        }
        assertNull(HistoryEntry.from(download(DownloadSelection.Video()).copy(output = null)))
    }

    private fun entry(id: String, kind: HistoryKind, title: String, site: String? = null, name: String = "$title.x") =
        HistoryEntry(
            id = id,
            kind = kind,
            title = title,
            sourceName = site,
            format = "MP4",
            file = file.copy(displayName = name),
            completedAt = 0,
        )

    private val entries = listOf(
        entry("1", HistoryKind.VIDEO, "Canción de cuna", "YouTube"),
        entry("2", HistoryKind.AUDIO, "Podcast 12", "Dailymotion"),
        entry("3", HistoryKind.CONVERSION, "Vacaciones", name = "Playa 2025.mp3"),
    )

    private fun ids(filter: HistoryFilter, query: String = "") = HistorySearch.apply(entries, filter, query).map {
        it.id
    }

    @Test
    fun filterByKind() {
        assertEquals(listOf("1", "2", "3"), ids(HistoryFilter.ALL))
        assertEquals(listOf("1"), ids(HistoryFilter.VIDEO))
        assertEquals(listOf("2"), ids(HistoryFilter.AUDIO))
        assertEquals(listOf("3"), ids(HistoryFilter.CONVERSIONS))
    }

    @Test
    fun searchIgnoresCaseAccentsAndWordOrder() {
        assertEquals(listOf("1"), ids(HistoryFilter.ALL, "CANCION"))
        assertEquals(listOf("1"), ids(HistoryFilter.ALL, "  cuna   canción "))
        assertEquals(listOf("2"), ids(HistoryFilter.ALL, "dailymotion"))
        assertEquals(listOf("3"), ids(HistoryFilter.ALL, "playa"))
        assertEquals(emptyList<String>(), ids(HistoryFilter.VIDEO, "podcast"))
        assertEquals(emptyList<String>(), ids(HistoryFilter.ALL, "nada"))
    }
}
