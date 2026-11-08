package com.fabapps.mediaforge.ui.home

import com.fabapps.mediaforge.domain.engine.ExtractorEngine
import com.fabapps.mediaforge.domain.engine.MediaConverter
import com.fabapps.mediaforge.domain.engine.MediaDownloader
import com.fabapps.mediaforge.domain.engine.MediaExtractor
import com.fabapps.mediaforge.domain.engine.Outcome
import com.fabapps.mediaforge.domain.jobs.JobScheduler
import com.fabapps.mediaforge.domain.model.AudioFormat
import com.fabapps.mediaforge.domain.model.DownloadRequest
import com.fabapps.mediaforge.domain.model.DownloadSelection
import com.fabapps.mediaforge.domain.model.JobState
import com.fabapps.mediaforge.domain.model.MediaError
import com.fabapps.mediaforge.domain.model.MediaFormat
import com.fabapps.mediaforge.domain.model.MediaInfo
import com.fabapps.mediaforge.domain.model.OutputFile
import com.fabapps.mediaforge.domain.model.ProcessingStep
import com.fabapps.mediaforge.domain.model.SourceSite
import com.fabapps.mediaforge.domain.settings.PreferredMode
import com.fabapps.mediaforge.domain.settings.Settings
import com.fabapps.mediaforge.domain.storage.MediaLibrary
import com.fabapps.mediaforge.domain.url.UrlCheck
import com.fabapps.mediaforge.share.ShareInbox
import com.fabapps.mediaforge.share.SharedContent
import com.fabapps.mediaforge.testing.FakeSettingsStore
import com.fabapps.mediaforge.testing.InMemoryJobStore
import com.fabapps.mediaforge.ui.common.WorkState
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
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeExtractor : MediaExtractor {
        val calls = mutableListOf<String>()
        var pending = CompletableDeferred<Outcome<MediaInfo>>()
        var cancelled = 0

        override suspend fun extract(url: String): Outcome<MediaInfo> {
            calls += url
            return try {
                pending.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private class FakeEngine : ExtractorEngine {
        var pending = CompletableDeferred<Outcome<String>>()
        var updates = 0
        override suspend fun installedVersion() = "2025.11.12"
        override suspend fun checkForUpdate(): Outcome<String?> = Outcome.Success(null)
        override suspend fun update(): Outcome<String> {
            updates++
            return pending.await()
        }
    }

    /** Emite lo que la prueba manda por [events]. */
    private class FakeDownloader : MediaDownloader {
        val requests = mutableListOf<DownloadRequest>()
        var events = Channel<JobState>(Channel.UNLIMITED)
        var cancelled = 0
        override fun download(request: DownloadRequest): Flow<JobState> {
            requests += request
            return events.consumeAsFlow().onCompletion {
                if (it is kotlinx.coroutines.CancellationException) cancelled++
            }
        }
    }

    private class FakeLibrary : MediaLibrary {
        val saved = mutableListOf<OutputFile>()
        var result: (OutputFile) -> Outcome<OutputFile> = { Outcome.Success(it.copy(location = "content://media/1")) }
        override suspend fun save(file: OutputFile): Outcome<OutputFile> {
            saved += file
            return result(file)
        }
        override fun needsWritePermission() = false
    }

    private val extractor = FakeExtractor()
    private val engine = FakeEngine()
    private val downloader = FakeDownloader()
    private val library = FakeLibrary()
    private val store = InMemoryJobStore()
    private val inbox = ShareInbox()
    private val settings = FakeSettingsStore()

    /** La cola real, con el descargador falso: la descarga pasa por la cola como en la app. */
    private val queueScope = CoroutineScope(dispatcher + SupervisorJob())
    private val noConverter = object : MediaConverter {
        override fun convert(request: com.fabapps.mediaforge.domain.model.ConversionRequest) = emptyFlow<JobState>()
    }
    private lateinit var vm: HomeViewModel
    private val state get() = vm.state.value

    private val info = MediaInfo(
        id = "aqz-KE-bpKQ",
        sourceUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        title = "Big Buck Bunny",
        site = SourceSite("Youtube", "YouTube"),
        formats = listOf(
            MediaFormat("299", "mp4", videoCodec = "avc1", height = 1080, fps = 60.0, fileSizeBytes = 1000),
            MediaFormat("134", "mp4", videoCodec = "avc1", height = 360, fileSizeBytes = 100),
            MediaFormat("140", "m4a", audioCodec = "mp4a.40.2", bitrateKbps = 129.0, fileSizeBytes = 50),
        ),
        durationSeconds = 100,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val queue =
            JobScheduler(store, downloader, noConverter, library, queueScope, clock = {
                dispatcher.scheduler.currentTime
            })
        vm = HomeViewModel(extractor, engine, queue, library, inbox, settings)
    }

    @After
    fun tearDown() {
        queueScope.cancel()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    // Entrada

    @Test
    fun startsEmptyAndCannotAnalyze() {
        assertEquals(InputStatus.EMPTY, state.status)
        assertFalse(state.canAnalyze)
    }

    @Test
    fun typingPartialUrlDoesNotShowError() {
        vm.onInputChange("https://")
        assertEquals(InputStatus.TYPING, state.status)
        assertEquals(null, state.invalidReason)
    }

    @Test
    fun validUrlEnablesAnalyze() {
        vm.onInputChange("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertEquals(InputStatus.READY, state.status)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun analyzeWithInvalidInputShowsReasonAndDoesNotCallEngine() = test {
        vm.onInputChange("file:///etc/passwd")
        vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.UNSUPPORTED_SCHEME, state.invalidReason)
        assertTrue(extractor.calls.isEmpty())
    }

    @Test
    fun editingAfterErrorHidesIt() {
        vm.onInputChange("nope")
        vm.onAnalyze()
        vm.onInputChange("nope2")
        assertEquals(InputStatus.TYPING, state.status)
        assertEquals(null, state.invalidReason)
    }

    @Test
    fun pasteExtractsUrlFromSharedText() {
        vm.onPaste("Mirá esto https://www.dailymotion.com/video/x8abc ¡genial!")
        assertEquals("https://www.dailymotion.com/video/x8abc", state.input)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun pasteWithoutUrlShowsError() {
        vm.onPaste("solo texto")
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.NOT_A_URL, state.invalidReason)
    }

    @Test
    fun pasteEmptyClipboardChangesNothing() {
        vm.onInputChange("https://example.com/v")
        vm.onPaste(null)
        vm.onPaste("  ")
        assertEquals("https://example.com/v", state.input)
    }

    // Ajustes (ADR-019)

    @Test
    fun withoutPreferencesTheBestVideoIsChosen() = test {
        analyzed()
        assertEquals(DownloadMode.VIDEO, state.mode)
        assertEquals(0, state.selectedOption)
        assertEquals(AudioFormat.MP3, state.audioOption?.format)
        assertEquals(null, state.audioBitrate)
    }

    @Test
    fun preferredQualityAndAudioAreSelectedAfterAnalyzing() = test {
        settings.settings.value =
            Settings(mode = PreferredMode.AUDIO, maxHeight = 720, audioFormat = AudioFormat.MP3, audioBitrateKbps = 320)
        analyzed()
        assertEquals(DownloadMode.AUDIO, state.mode)
        // 1080 supera 720: queda 360, la mayor que no la supera.
        assertEquals(360, state.options[state.selectedOption].height)
        assertEquals(AudioFormat.MP3, state.audioOption?.format)
        assertEquals(320, state.audioBitrate)
    }

    @Test
    fun preferredAudioFormatWithoutBitrateUsesAutomatic() = test {
        settings.settings.value = Settings(audioFormat = AudioFormat.M4A)
        analyzed()
        assertEquals(AudioFormat.M4A, state.audioOption?.format)
        assertEquals(null, state.audioBitrate)
    }

    // Compartir (ADR-018)

    @Test
    fun sharedLinkIsLoadedAndAnalyzed() = test {
        inbox.offer(SharedContent("Mirá esto https://www.dailymotion.com/video/x8abc ¡genial!", null))
        advanceUntilIdle()
        assertEquals("https://www.dailymotion.com/video/x8abc", state.input)
        assertEquals(AnalysisState.Loading, state.analysis)
        assertEquals(listOf("https://www.dailymotion.com/video/x8abc"), extractor.calls)
        assertEquals(null, inbox.pending.value)
    }

    @Test
    fun linkSharedBeforeHomeOpensIsTaken() = test {
        val early = ShareInbox().apply { offer(SharedContent(null, "https://vimeo.com/1")) }
        val queue = JobScheduler(store, downloader, noConverter, library, queueScope)
        val home = HomeViewModel(extractor, engine, queue, library, early, settings)
        advanceUntilIdle()
        assertEquals("https://vimeo.com/1", home.state.value.input)
        assertEquals(listOf("https://vimeo.com/1"), extractor.calls)
        assertEquals(null, early.pending.value)
    }

    @Test
    fun sharedTextWithoutLinkShowsWhy() = test {
        vm.onInputChange("https://example.com/v")
        inbox.offer(SharedContent("solo un mensaje", null))
        advanceUntilIdle()
        assertEquals("solo un mensaje", state.input)
        assertEquals(InputStatus.INVALID, state.status)
        assertEquals(UrlCheck.Reason.NOT_A_URL, state.invalidReason)
        assertTrue(extractor.calls.isEmpty())
    }

    @Test
    fun sharingWhileDownloadingKeepsTheDownloadQueued() = test {
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Downloading(0.1f, 10, 100, null))
        advanceUntilIdle()

        extractor.pending = CompletableDeferred()
        inbox.offer(SharedContent("https://example.com/other", null))
        advanceUntilIdle()
        assertEquals("https://example.com/other", state.input)
        assertEquals(WorkState.None, state.download)
        assertEquals(AnalysisState.Loading, state.analysis)
        assertEquals(0, downloader.cancelled)
    }

    @Test
    fun aNewShareReplacesAnAnalysisInProgress() = test {
        inbox.offer(SharedContent("https://a.com/1", null))
        advanceUntilIdle()
        inbox.offer(SharedContent("https://b.com/2", null))
        advanceUntilIdle()
        assertEquals(1, extractor.cancelled)
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), extractor.calls)
        assertEquals("https://b.com/2", state.input)
    }

    // Análisis

    @Test
    fun analyzeShowsLoadingThenResultWithNormalizedUrl() = test {
        vm.onInputChange("WWW.YouTube.com/watch?v=aqz-KE-bpKQ")
        vm.onAnalyze()
        assertEquals(AnalysisState.Loading, state.analysis)
        assertFalse(state.canAnalyze)

        advanceUntilIdle()
        assertEquals(listOf("https://www.youtube.com/watch?v=aqz-KE-bpKQ"), extractor.calls)

        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
        assertEquals(AnalysisState.Loaded(info), state.analysis)
        assertTrue(state.canAnalyze)
    }

    @Test
    fun engineErrorIsShown() = test {
        vm.onInputChange("https://vimeo.com/1")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.LoginRequired()))
        advanceUntilIdle()
        assertEquals(AnalysisState.Failed(MediaError.LoginRequired()), state.analysis)
    }

    @Test
    fun secondTapWhileLoadingIsIgnored() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(1, extractor.calls.size)
    }

    @Test
    fun changingTheLinkCancelsTheRunningAnalysis() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onInputChange("https://example.com/other")
        advanceUntilIdle()
        assertEquals(1, extractor.cancelled)
        assertEquals(AnalysisState.Idle, state.analysis)
    }

    @Test
    fun clearCancelsAndResets() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        advanceUntilIdle()
        vm.onClear()
        advanceUntilIdle()
        assertEquals(1, extractor.cancelled)
        assertEquals(HomeUiState(), state)
    }

    @Test
    fun retryAfterErrorRunsAgain() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.Network()))
        advanceUntilIdle()

        extractor.pending = CompletableDeferred()
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
        assertEquals(2, extractor.calls.size)
        assertEquals(AnalysisState.Loaded(info), state.analysis)
    }

    // Actualización del motor (T3.9)

    private suspend fun TestScope.failWithOutdatedEngine() {
        vm.onInputChange("https://www.dailymotion.com/video/x2kdx9f")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.EngineOutdated()))
        advanceUntilIdle()
    }

    @Test
    fun updateIsOfferedOnlyForOutdatedEngine() = test {
        vm.onInputChange("https://example.com/v")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Failure(MediaError.Network()))
        advanceUntilIdle()
        assertFalse(state.canUpdateEngine)
        vm.onUpdateEngine()
        assertEquals(0, engine.updates)
    }

    @Test
    fun successfulUpdateRetriesTheAnalysis() = test {
        failWithOutdatedEngine()
        assertTrue(state.canUpdateEngine)

        extractor.pending = CompletableDeferred()
        vm.onUpdateEngine()
        assertEquals(AnalysisState.UpdatingEngine, state.analysis)
        assertFalse(state.canAnalyze)

        engine.pending.complete(Outcome.Success("2026.10.26"))
        advanceUntilIdle()
        assertEquals(AnalysisState.Loading, state.analysis)
        assertEquals(2, extractor.calls.size)

        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
        assertEquals(AnalysisState.Loaded(info), state.analysis)
    }

    @Test
    fun failedUpdateIsShownAndCanBeRetried() = test {
        failWithOutdatedEngine()
        vm.onUpdateEngine()
        engine.pending.complete(Outcome.Failure(MediaError.Network()))
        advanceUntilIdle()
        assertEquals(AnalysisState.UpdateFailed(MediaError.Network()), state.analysis)
        assertTrue(state.canUpdateEngine)
        assertEquals(1, extractor.calls.size)
    }

    // Descarga (T4.7)

    private val privateFile = OutputFile("/data/jobs/1/media.mp4", "Big Buck Bunny.mp4", "video/mp4", 1050)

    private suspend fun TestScope.analyzed() {
        vm.onInputChange("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        vm.onAnalyze()
        extractor.pending.complete(Outcome.Success(info))
        advanceUntilIdle()
    }

    @Test
    fun analysisOffersQualitiesAndTitleAsFileName() = test {
        analyzed()
        assertEquals(listOf(1080, 360), state.options.map { it.height })
        assertEquals(0, state.selectedOption)
        assertEquals("Big Buck Bunny", state.fileName)
        assertTrue(state.canDownload)
    }

    @Test
    fun downloadUsesSelectedQualityAndEditedName() = test {
        analyzed()
        vm.onSelectOption(1)
        vm.onFileNameChange("Conejo")
        vm.onDownload()
        advanceUntilIdle()
        val request = downloader.requests.single()
        assertEquals(
            listOf("134", "140"),
            (request.selection as DownloadSelection.Video).formatIds,
        )
        assertEquals("Conejo", request.fileName)
        assertEquals(150L, request.expectedBytes)
        assertEquals(info.sourceUrl, request.sourceUrl)
    }

    @Test
    fun blankNameFallsBackToTitle() = test {
        analyzed()
        vm.onFileNameChange("  ")
        vm.onDownload()
        advanceUntilIdle()
        assertEquals("Big Buck Bunny", downloader.requests.single().fileName)
    }

    @Test
    fun progressThenSavedInGallery() = test {
        analyzed()
        vm.onDownload()
        val progress = JobState.Downloading(0.5f, 500, 1000, 100)
        downloader.events.send(progress)
        advanceUntilIdle()
        assertEquals(WorkState.Running(progress), state.download)
        assertFalse(state.canDownload)

        downloader.events.send(JobState.Processing(ProcessingStep.MERGE, null))
        downloader.events.send(JobState.Completed(listOf(privateFile)))
        downloader.events.close()
        advanceUntilIdle()
        assertEquals(listOf(privateFile), library.saved)
        assertEquals(WorkState.Saved(privateFile.copy(location = "content://media/1")), state.download)
    }

    @Test
    fun anotherLinkCanBeQueuedWhileTheFirstDownloads() = test {
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Downloading(0.1f, 10, 100, null))
        advanceUntilIdle()

        // Otro enlace: la pantalla deja de seguir la primera descarga, que sigue en la cola.
        vm.onInputChange("https://example.com/other")
        assertEquals(WorkState.None, state.download)
        assertTrue(state.canAnalyze)

        downloader.events = Channel(Channel.UNLIMITED)
        extractor.pending = CompletableDeferred()
        analyzed()
        vm.onDownload()
        advanceUntilIdle()
        assertEquals(2, downloader.requests.size)
        assertEquals(0, downloader.cancelled)
        assertTrue(state.download is WorkState.Running)
    }

    @Test
    fun cancelStopsTheDownload() = test {
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Downloading(0.1f, 10, 100, null))
        advanceUntilIdle()
        vm.onCancelDownload()
        advanceUntilIdle()
        assertEquals(1, downloader.cancelled)
        assertEquals(WorkState.Cancelled, state.download)
        assertTrue(library.saved.isEmpty())
        assertTrue(state.canDownload)
    }

    @Test
    fun engineFailureIsShownAndCanRetry() = test {
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Failed(MediaError.AccessDenied()))
        downloader.events.close()
        advanceUntilIdle()
        assertEquals(WorkState.Failed(MediaError.AccessDenied()), state.download)
        assertTrue(state.canDownload)

        downloader.events = Channel(Channel.UNLIMITED)
        vm.onDownload()
        advanceUntilIdle()
        assertEquals(2, downloader.requests.size)
    }

    @Test
    fun saveFailureIsShown() = test {
        library.result = { Outcome.Failure(MediaError.InsufficientStorage(1050, 10)) }
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Completed(listOf(privateFile)))
        downloader.events.close()
        advanceUntilIdle()
        assertEquals(WorkState.Failed(MediaError.InsufficientStorage(1050, 10)), state.download)
    }

    @Test
    fun analyzingAgainClearsThePreviousDownload() = test {
        analyzed()
        vm.onDownload()
        downloader.events.send(JobState.Failed(MediaError.AccessDenied()))
        downloader.events.close()
        advanceUntilIdle()
        extractor.pending = CompletableDeferred()
        vm.onAnalyze()
        assertEquals(WorkState.None, state.download)
    }

    // Audio (T5.4)

    private suspend fun TestScope.audioMode() {
        analyzed()
        vm.onModeChange(DownloadMode.AUDIO)
    }

    @Test
    fun audioStartsWithMp3AtAutoBitrate() = test {
        audioMode()
        assertEquals(AudioFormat.MP3, state.audioOption?.format)
        assertEquals(null, state.audioBitrate)
        // Origen AAC de 129 kbps: automático = 128, el menor ≥ al original (con 8 kbps de tolerancia).
        assertEquals(128, state.effectiveBitrate)
        assertFalse(state.bitrateExceedsSource)
        assertTrue(state.canDownload)
    }

    @Test
    fun higherBitrateThanSourceWarns() = test {
        audioMode()
        vm.onSelectBitrate(320)
        assertEquals(320, state.effectiveBitrate)
        assertTrue(state.bitrateExceedsSource)
        // 320 kbps × 100 s / 8
        assertEquals(4_000_000L, state.audioEstimatedBytes)
        vm.onSelectBitrate(null)
        assertFalse(state.bitrateExceedsSource)
    }

    @Test
    fun bitrateNotOfferedIsIgnored() = test {
        audioMode()
        vm.onSelectBitrate(999)
        assertEquals(null, state.audioBitrate)
    }

    @Test
    fun m4aCopiesAndEstimatesWithTheSourceSize() = test {
        audioMode()
        vm.onSelectAudioFormat(state.audioOptions.indexOfFirst { it.format == AudioFormat.M4A })
        assertTrue(state.audioOption!!.copiesWithoutReencoding)
        assertEquals(null, state.effectiveBitrate)
        assertEquals(50L, state.audioEstimatedBytes)
    }

    @Test
    fun audioDownloadSendsTheAudioSelection() = test {
        audioMode()
        vm.onSelectBitrate(320)
        vm.onDownload()
        advanceUntilIdle()
        val request = downloader.requests.single()
        assertEquals(DownloadSelection.Audio(AudioFormat.MP3, 320, listOf("140")), request.selection)
        assertEquals(4_000_000L, request.expectedBytes)
    }

    @Test
    fun changingFormatResetsBitrate() = test {
        audioMode()
        vm.onSelectBitrate(320)
        vm.onSelectAudioFormat(state.audioOptions.indexOfFirst { it.format == AudioFormat.OGG })
        assertEquals(null, state.audioBitrate)
    }

    @Test
    fun modeCannotChangeWhileDownloading() = test {
        analyzed()
        vm.onDownload()
        advanceUntilIdle()
        vm.onModeChange(DownloadMode.AUDIO)
        assertEquals(DownloadMode.VIDEO, state.mode)
    }
}
