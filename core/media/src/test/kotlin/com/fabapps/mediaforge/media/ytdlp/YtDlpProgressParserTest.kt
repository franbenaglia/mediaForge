package com.fabapps.mediaforge.media.ytdlp

import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.ProcessingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Salida reconstruida con el formato de yt-dlp 2026.10.26 y la plantilla de [YtDlpCommand.download]. */
class YtDlpProgressParserTest {
    private fun feedAll(vararg lines: String): List<JobState> {
        val parser = YtDlpProgressParser()
        return lines.mapNotNull { parser.feed(it) }
    }

    private fun downloading(states: List<JobState>) = states.filterIsInstance<JobState.Downloading>()

    @Test
    fun singlePartWithKnownSize() {
        val states = feedAll(
            "[info] x2kdx9f: Downloading 1 format(s): http-380",
            "[download] Destination: /w/media.mp4",
            "MFPROG downloading 0 1000 NA NA",
            "MFPROG downloading 250 1000 NA 500.5",
            "MFPROG finished 1000 1000 NA NA",
        )
        assertEquals(
            listOf(
                JobState.Downloading(0f, 0, 1000, null),
                JobState.Downloading(0.25f, 250, 1000, 500),
                JobState.Downloading(1f, 1000, 1000, null),
            ),
            states,
        )
    }

    @Test
    fun videoAndAudioAreOneProgressAndTotalAppearsInLastPart() {
        val states = downloading(
            feedAll(
                "[youtube] aqz-KE-bpKQ: Downloading webpage",
                "[info] aqz-KE-bpKQ: Downloading 1 format(s): 299+258",
                "[download] Destination: /w/media.f299.mp4",
                "MFPROG downloading 500 1000 NA 100",
                "MFPROG finished 1000 1000 NA NA",
                "[download] Destination: /w/media.f258.m4a",
                "MFPROG downloading 100 200 NA 100",
                "MFPROG finished 200 200 NA NA",
            ),
        )
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), states.map { it.progress })
        assertEquals(listOf(500L, 1000L, 1100L, 1200L), states.map { it.downloadedBytes })
        assertEquals(listOf(null, null, 1200L, 1200L), states.map { it.totalBytes })
    }

    @Test
    fun hlsUsesEstimateAndUnknownSizeHasNoProgress() {
        val hls = downloading(
            feedAll(
                "[download] Destination: /w/media.mp4",
                "MFPROG downloading 1048576 NA 34311720.0 250000.0",
            ),
        ).single()
        assertEquals(1_048_576L, hls.downloadedBytes)
        assertEquals(34_311_720L, hls.totalBytes)
        assertEquals(250_000L, hls.bytesPerSecond)

        val unknown = downloading(feedAll("MFPROG downloading 4096 NA NA NA")).single()
        assertNull(unknown.progress)
        assertNull(unknown.totalBytes)
        assertEquals(4096L, unknown.downloadedBytes)
    }

    @Test
    fun mergeFixupAndMetadataSteps() {
        val states = feedAll(
            "[Merger] Merging formats into \"/w/media.mp4\"",
            "Deleting original file /w/media.f299.mp4 (pass -k to keep)",
            "[FixupM3u8] Fixing MPEG-TS in MP4 container of \"/w/media.mp4\"",
            "[Metadata] Adding metadata to \"/w/media.mp4\"",
        )
        assertEquals(
            listOf(
                JobState.Processing(ProcessingStep.MERGE, null),
                JobState.Processing(ProcessingStep.METADATA, null),
            ),
            states,
        )
    }

    @Test
    fun repeatedAndUnrelatedLinesEmitNothing() {
        val states = feedAll(
            "MFPROG downloading 10 100 NA NA",
            "MFPROG downloading 10 100 NA NA",
            "[youtube] Extracting URL: https://www.youtube.com/watch?v=x",
            "WARNING: something",
            "MFPROG garbage",
            "MFPROG downloading NA NA NA NA",
        )
        assertEquals(1, states.size)
    }

    @Test
    fun moreDestinationsThanPartsDoNotOverflow() {
        val states = downloading(
            feedAll(
                "[info] x: Downloading 1 format(s): 18",
                "[download] Destination: /w/a",
                "[download] Destination: /w/b",
                "MFPROG downloading 50 100 NA NA",
            ),
        )
        assertEquals(0.5f, states.single().progress)
    }
}
