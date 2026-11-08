package com.fabapps.mediaforge.ui.converter

import com.fabapps.mediaforge.domain.convert.AudioStream
import com.fabapps.mediaforge.domain.convert.Clip
import com.fabapps.mediaforge.domain.convert.LocalMedia
import com.fabapps.mediaforge.domain.convert.MediaInspector
import com.fabapps.mediaforge.domain.convert.StreamPlan
import com.fabapps.mediaforge.domain.convert.VideoStream
import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.ConversionRequest
import com.fabapps.mediaforge.domain.model.ConversionTarget
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.domain.model.VideoContainer
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.jobs.InputAccess
import com.fabapps.mediaforge.testing.InMemoryJobStore
import com.fabapps.mediaforge.ui.common.WorkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeInspector : MediaInspector {
        var result = CompletableDeferred<Outcome<LocalMedia>>()
        override suspend fun inspect(input: String) = result.await()
    }

    private class FakeConverter : MediaConverter {
        val requests = mutableListOf<ConversionRequest>()
        var events = Channel<JobState>(Channel.UNLIMITED)
        var cancelled = 0
        override fun convert(request: ConversionRequest): Flow<JobState> {
            requests += request
            return events.consumeAsFlow().onCompletion { if (it is CancellationException) cancelled++ }
        }
    }

    private class FakeLibrary : MediaLibrary {
        val saved = mutableListOf<OutputFile>()
        override suspend fun save(file: OutputFile): Outcome<OutputFile> {
            saved += file
            return Outcome.Success(file.copy(location = "content://media/9"))
        }
        override fun needsWritePermission() = false
    }

    private val inspector = FakeInspector()
    private val converter = FakeConverter()
    private val library = FakeLibrary()
    private class FakeInputAccess : InputAccess {
        val kept = mutableListOf<String>()
        override fun keep(uri: String) {
            kept += uri
        }
        override fun release(uri: String) = Unit
    }

    private val inputAccess = FakeInputAccess()
    private val store = InMemoryJobStore()

    /** La cola real, con el conversor falso. */
    private val queueScope = CoroutineScope(dispatcher + SupervisorJob())
    private val noDownloader = object : MediaDownloader {
        override fun download(request: DownloadRequest) = emptyFlow<JobState>()
    }
    private lateinit var vm: ConverterViewModel
    private val state get() = vm.state.value

    private val video = LocalMedia(
        displayName = "Vacaciones.mkv",
        sizeBytes = 10_000_000,
        durationSeconds = 100.0,
        video = VideoStream("h264", 1920, 1080, 30.0),
        audio = AudioStream("opus", 128.0, 2, 48_000),
    )
    private val song = LocalMedia("Tema.flac", 30_000_000, 200.0, audio = AudioStream("flac", null, 2, 44_100))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val queue =
            JobScheduler(store, noDownloader, converter, library, queueScope, clock = {
                dispatcher.scheduler.currentTime
            })
        vm = ConverterViewModel(inspector, queue, library, inputAccess)
    }

    @After
    fun tearDown() {
        queueScope.cancel()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun TestScope.pick(media: LocalMedia) {
        vm.onFilePicked("content://docs/1")
        assertEquals(InputState.Inspecting, state.input)
        inspector.result.complete(Outcome.Success(media))
        advanceUntilIdle()
    }

    @Test
    fun startsWithoutFileAndCannotConvert() {
        assertEquals(InputState.None, state.input)
        assertFalse(state.canConvert)
    }

    @Test
    fun cancelledPickerChangesNothing() {
        vm.onFilePicked(null)
        assertEquals(InputState.None, state.input)
    }

    @Test
    fun videoFileDefaultsToVideoTargetAndNameWithoutExtension() = test {
        pick(video)
        assertEquals(TargetKind.VIDEO, state.kind)
        assertEquals("Vacaciones", state.fileName)
        assertEquals(listOf(720, 480, 360), state.heightOptions)
        // MKV con H.264 + Opus → MP4: video copiado, audio a AAC.
        val plan = state.readyPlan!!
        assertEquals(StreamPlan.Copy, plan.video)
        assertEquals(StreamPlan.Encode("aac", 160), plan.audio)
        assertTrue(state.canConvert)
    }

    @Test
    fun audioFileDefaultsToAudioTarget() = test {
        pick(song)
        assertEquals(TargetKind.AUDIO, state.kind)
        assertEquals(StreamPlan.Encode("mp3", 192), state.readyPlan!!.audio)
    }

    @Test
    fun audioFileToVideoIsAnExplainedError() = test {
        pick(song)
        vm.onKindChange(TargetKind.VIDEO)
        assertTrue((state.plan as Outcome.Failure).error is MediaError.UnsupportedConversion)
        assertFalse(state.canConvert)
    }

    @Test
    fun unreadableFileShowsError() = test {
        vm.onFilePicked("content://docs/1")
        inspector.result.complete(Outcome.Failure(MediaError.UnsupportedConversion("bad")))
        advanceUntilIdle()
        assertEquals(InputState.Failed(MediaError.UnsupportedConversion("bad")), state.input)
    }

    @Test
    fun optionsBuildTheTarget() = test {
        pick(video)
        vm.onContainerChange(VideoContainer.WEBM)
        vm.onMaxHeightChange(720)
        assertEquals(ConversionTarget.Video(VideoContainer.WEBM, 720), state.target)
        vm.onMaxHeightChange(4000)
        assertEquals(720, state.maxHeight)

        vm.onKindChange(TargetKind.AUDIO)
        vm.onAudioFormatChange(AudioFormat.MP3)
        vm.onBitrateChange(320)
        assertEquals(ConversionTarget.Audio(AudioFormat.MP3, 320), state.target)
        assertTrue(state.bitrateExceedsSource)
        vm.onBitrateChange(999)
        assertEquals(320, state.audioBitrate)
        vm.onAudioFormatChange(AudioFormat.FLAC)
        assertEquals(null, state.audioBitrate)
        assertTrue(state.bitrates.isEmpty())
    }

    @Test
    fun convertShowsProgressThenSaves() = test {
        pick(video)
        vm.onFileNameChange("Mis vacaciones")
        vm.onConvert()
        val step = JobState.Processing(ProcessingStep.TRANSCODE, 0.5f)
        converter.events.send(step)
        advanceUntilIdle()
        assertEquals(WorkState.Running(step), state.work)
        assertTrue(state.locked)
        vm.onContainerChange(VideoContainer.MOV)
        assertEquals(VideoContainer.MP4, state.container)

        val output = OutputFile("/data/jobs/x/output.mp4", "Mis vacaciones.mp4", "video/mp4", 9)
        converter.events.send(JobState.Completed(listOf(output)))
        converter.events.close()
        advanceUntilIdle()
        val request = converter.requests.single()
        assertEquals("content://docs/1", request.input)
        assertEquals("Mis vacaciones", request.fileName)
        assertEquals(ConversionTarget.Video(VideoContainer.MP4, null), request.target)
        assertEquals(listOf(output), library.saved)
        assertEquals(WorkState.Saved(output.copy(location = "content://media/9")), state.work)
        // La cola puede correr o reintentar la conversión cuando esta pantalla ya no está.
        assertEquals(listOf("content://docs/1"), inputAccess.kept)
        assertEquals("Mis vacaciones", store.jobs.value.values.single().title)
    }

    @Test
    fun anotherFileCanBePickedWhileConverting() = test {
        pick(video)
        vm.onConvert()
        converter.events.send(JobState.Processing(ProcessingStep.TRANSCODE, 0.1f))
        advanceUntilIdle()

        vm.onFilePicked("content://docs/2")
        assertEquals(InputState.Inspecting, state.input)
        assertEquals(WorkState.None, state.work)
        advanceUntilIdle()
        // La primera conversión sigue en la cola.
        assertEquals(0, converter.cancelled)
        assertEquals(1, store.jobs.value.size)
    }

    @Test
    fun cancelStopsTheConversion() = test {
        pick(video)
        vm.onConvert()
        converter.events.send(JobState.Processing(ProcessingStep.TRANSCODE, 0.1f))
        advanceUntilIdle()
        vm.onCancel()
        advanceUntilIdle()
        assertEquals(1, converter.cancelled)
        assertEquals(WorkState.Cancelled, state.work)
        assertTrue(state.canConvert)
    }

    @Test
    fun failureIsShownAndChangingAnOptionClearsIt() = test {
        pick(video)
        vm.onConvert()
        converter.events.send(JobState.Failed(MediaError.Unknown("ffmpeg")))
        converter.events.close()
        advanceUntilIdle()
        assertEquals(WorkState.Failed(MediaError.Unknown("ffmpeg")), state.work)
        vm.onContainerChange(VideoContainer.MKV)
        assertEquals(WorkState.None, state.work)
    }

    @Test
    fun trimIsOffUntilEnabled() = test {
        pick(video)
        vm.onTrimStartChange("10")
        assertEquals(null, state.clip)
        vm.onTrimEnabledChange(true)
        assertEquals(Clip(10.0), state.clip)
        assertEquals(90.0, state.clipLength!!, 0.0)
    }

    @Test
    fun trimErrorsBlockTheConversion() = test {
        pick(video)
        vm.onTrimEnabledChange(true)
        vm.onTrimStartChange("1:7x")
        assertEquals(TrimError.START, state.trimError)
        assertFalse(state.canConvert)
        vm.onTrimStartChange("0:30")
        vm.onTrimEndChange("0:30.2")
        assertEquals(TrimError.TOO_SHORT, state.trimError)
        vm.onTrimEndChange("2:00")
        assertEquals(TrimError.BEYOND_END, state.trimError)
        vm.onTrimEndChange("nope")
        assertEquals(TrimError.END, state.trimError)
        vm.onTrimEndChange("1:10")
        assertEquals(null, state.trimError)
        assertTrue(state.canConvert)
    }

    @Test
    fun emptyTrimMeansTheWholeFile() = test {
        pick(video)
        vm.onTrimEnabledChange(true)
        assertEquals(null, state.clip)
        assertEquals(null, state.trimError)
        assertTrue(state.canConvert)
    }

    @Test
    fun quickCutWarnsAboutKeyframesAndExactCutEncodes() = test {
        pick(video)
        vm.onContainerChange(VideoContainer.MKV)
        vm.onTrimEnabledChange(true)
        vm.onTrimStartChange("10")
        vm.onTrimEndChange("20")
        assertTrue(state.cutsAtKeyframe)
        assertEquals(StreamPlan.Copy, state.readyPlan!!.video)
        // 10 s de 100: la décima parte de los 10 MB.
        assertEquals(1_000_000L, state.estimatedBytes)
        vm.onExactCutChange(true)
        assertFalse(state.cutsAtKeyframe)
        assertEquals(StreamPlan.Encode("h264"), state.readyPlan!!.video)
    }

    @Test
    fun convertSendsTheClip() = test {
        pick(song)
        vm.onTrimEnabledChange(true)
        vm.onTrimStartChange("1:00")
        vm.onTrimEndChange("1:30")
        vm.onConvert()
        advanceUntilIdle()
        assertEquals(Clip(60.0, 90.0), converter.requests.single().clip)
    }

    @Test
    fun clearGoesBackToStart() = test {
        pick(video)
        vm.onClear()
        assertEquals(ConverterUiState(), state)
    }
}
