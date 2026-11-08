package com.fabapps.mediaforge.data.jobs

import com.fabapps.mediaforge.domain.convert.Clip
import com.fabapps.mediaforge.domain.jobs.Job
import com.fabapps.mediaforge.domain.jobs.JobRequest
import com.fabapps.mediaforge.domain.jobs.JobStatus
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.ImageFormat
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.VideoContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JobMapperTest {
    private fun job(request: JobRequest, error: MediaError? = null, output: OutputFile? = null) = Job(
        request = request,
        title = "Título",
        sourceName = "YouTube",
        thumbnailUrl = "https://i.ytimg.com/x.jpg",
        status = if (error != null) JobStatus.FAILED else JobStatus.COMPLETED,
        error = error,
        output = output,
        attempts = 2,
        createdAt = 100,
        updatedAt = 200,
    )

    private fun roundTrip(job: Job) = assertEquals(job, JobMapper.toDomain(JobMapper.toEntity(job)))

    private val video = JobRequest.Download(
        DownloadRequest(
            jobId = "v",
            sourceUrl = "https://www.youtube.com/watch?v=x",
            selection = DownloadSelection.Video(listOf("299", "258"), 1080, VideoContainer.MKV),
            fileName = "clip",
            embedMetadata = false,
            expectedBytes = 1234,
        ),
    )

    @Test
    fun `video download round trips`() =
        roundTrip(job(video, output = OutputFile("content://media/1", "clip.mkv", "video/x-matroska", 99)))

    @Test
    fun `audio download round trips`() = roundTrip(
        job(
            JobRequest.Download(
                DownloadRequest(
                    "a",
                    "https://x.com/a",
                    DownloadSelection.Audio(AudioFormat.MP3, 320, listOf("251")),
                    "a",
                ),
            ),
        ),
    )

    @Test
    fun `conversions round trip`() {
        roundTrip(
            job(
                JobRequest.Conversion(
                    ConversionRequest("c", "content://doc/1", ConversionTarget.Video(VideoContainer.WEBM, 480), "c"),
                ),
            ),
        )
        roundTrip(
            job(
                JobRequest.Conversion(
                    ConversionRequest("d", "content://doc/2", ConversionTarget.Audio(AudioFormat.FLAC), "d"),
                ),
            ),
        )
        roundTrip(
            job(
                JobRequest.Conversion(
                    ConversionRequest(
                        "e",
                        "content://doc/3",
                        ConversionTarget.Video(VideoContainer.MP4),
                        "e",
                        Clip(1.5, 20.0, exact = true),
                    ),
                ),
            ),
        )
        roundTrip(
            job(
                JobRequest.Conversion(
                    ConversionRequest("f", "content://doc/4", ConversionTarget.Image(12.5, ImageFormat.PNG), "f"),
                ),
            ),
        )
    }

    @Test
    fun `conversions saved before the clip existed still load`() {
        val row = JobMapper.toEntity(
            job(
                JobRequest.Conversion(
                    ConversionRequest("c", "content://doc/1", ConversionTarget.Audio(AudioFormat.MP3), "c"),
                ),
            ),
        )
        val old = """{"type":"conversion","jobId":"c","input":"content://doc/1",""" +
            """"target":{"type":"audio","format":"MP3"},"fileName":"c"}"""
        val request = (JobMapper.toDomain(row.copy(requestJson = old))?.request as JobRequest.Conversion).request
        assertNull(request.clip)
        assertEquals(ConversionTarget.Audio(AudioFormat.MP3), request.target)
    }

    @Test
    fun `every error kind round trips`() {
        listOf(
            MediaError.InvalidUrl("u"),
            MediaError.UnsupportedSite("s"),
            MediaError.LoginRequired(),
            MediaError.AccessDenied("403"),
            MediaError.Network("timeout"),
            MediaError.InsufficientStorage(requiredBytes = 10, availableBytes = 5, detail = "full"),
            MediaError.UnsupportedConversion("no audio"),
            MediaError.EngineOutdated(),
            MediaError.Unknown("boom"),
        ).forEach { roundTrip(job(video, error = it)) }
    }

    @Test
    fun `unreadable or newer rows are skipped or shown as interrupted`() {
        val row = JobMapper.toEntity(job(video))
        assertNull(JobMapper.toDomain(row.copy(requestJson = "{nope")))
        assertNull(JobMapper.toDomain(row.copy(requestVersion = RequestJson.VERSION + 1)))
        assertNull(JobMapper.toDomain(row.copy(requestJson = row.requestJson.replace("MKV", "AVI"))))
        assertEquals(JobStatus.INTERRUPTED, JobMapper.toDomain(row.copy(status = "PAUSED"))?.status)
        assertEquals(
            MediaError.Unknown("x"),
            JobMapper.toDomain(row.copy(errorType = "NEW_KIND", errorDetail = "x"))?.error,
        )
    }

    @Test
    fun `stored format is stable`() {
        val json = JobMapper.toEntity(job(video)).requestJson
        assertEquals(
            """{"type":"download","jobId":"v","sourceUrl":"https://www.youtube.com/watch?v=x",""" +
                """"selection":{"type":"video","formatIds":["299","258"],"maxHeight":1080,"container":"MKV"},""" +
                """"fileName":"clip","embedMetadata":false,"expectedBytes":1234}""",
            json,
        )
    }
}
