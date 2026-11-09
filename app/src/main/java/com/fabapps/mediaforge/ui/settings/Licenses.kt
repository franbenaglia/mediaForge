package com.fabapps.mediaforge.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fabapps.mediaforge.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un componente que viaja dentro del APK y su licencia (detalle en `docs/LICENSES.md`). */
data class ThirdPartyComponent(val name: String, val license: String)

/**
 * Lo que trae el APK, agrupado como en `docs/LICENSES.md` (T11.3). Los nombres y licencias son
 * nombres propios: no se traducen.
 */
object ThirdPartyComponents {
    val ENGINES = listOf(
        ThirdPartyComponent("yt-dlp", "Unlicense"),
        ThirdPartyComponent("youtubedl-android", "GPL-3.0"),
        ThirdPartyComponent("FFmpeg 7.1 (--enable-gpl --enable-version3)", "GPL-3.0-or-later"),
        ThirdPartyComponent("x264, x265, Xvid, vid.stab, Rubber Band, FFTW", "GPL-2.0-or-later"),
        ThirdPartyComponent("LAME, mpg123, FriBidi, GLib, GnuTLS, Nettle, GMP, libbluray, libssh…", "LGPL"),
        ThirdPartyComponent("libaom, dav1d, rav1e, SVT-AV1, libvpx, libwebp, Opus, Vorbis, Theora…", "BSD"),
        ThirdPartyComponent("OpenCORE AMR, VisualOn AMR-WB", "Apache-2.0"),
        ThirdPartyComponent("SRT, ZeroMQ", "MPL-2.0"),
        ThirdPartyComponent("HarfBuzz, Fontconfig, libxml2, libass, libsodium, Brotli…", "MIT / ISC"),
        ThirdPartyComponent("FreeType, libpng, zlib, XZ, bzip2, zimg", "FTL / libpng / Zlib / 0BSD"),
        ThirdPartyComponent("Python 3.12", "PSF-2.0"),
        ThirdPartyComponent("OpenSSL 3", "Apache-2.0"),
        ThirdPartyComponent("GNU Readline, GDBM", "GPL-3.0-or-later"),
        ThirdPartyComponent("ncurses, Expat, libffi, SQLite", "MIT / blessing"),
        ThirdPartyComponent("QuickJS", "MIT"),
    )

    val LIBRARIES = listOf(
        ThirdPartyComponent("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache-2.0"),
        ThirdPartyComponent("AndroidX, Jetpack Compose, Material 3, Room, DataStore", "Apache-2.0"),
        ThirdPartyComponent("Dagger / Hilt", "Apache-2.0"),
        ThirdPartyComponent("Coil, OkHttp, Okio", "Apache-2.0"),
        ThirdPartyComponent("Jackson, Apache Commons IO y Compress", "Apache-2.0"),
    )
}

@Composable
internal fun LicensesDialog(onDismiss: () -> Unit, onOpenDetails: () -> Unit, onOpenNotices: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licenses_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
                Group(stringResource(R.string.licenses_engines), ThirdPartyComponents.ENGINES)
                Group(stringResource(R.string.licenses_libraries), ThirdPartyComponents.LIBRARIES)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.licenses_close)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenDetails) { Text(stringResource(R.string.licenses_details)) }
                TextButton(onClick = onOpenNotices) { Text(stringResource(R.string.licenses_notices)) }
            }
        },
    )
}

@Composable
private fun Group(title: String, components: List<ThirdPartyComponent>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    components.forEach { component ->
        Column {
            Text(component.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                component.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Los avisos completos (`assets/third_party_notices.txt`, T12.4), en párrafos. */
fun interface NoticesSource {
    suspend fun paragraphs(): List<String>
}

/** Lee el archivo generado por `scripts/build-notices.sh`, fuera del hilo principal. */
class AssetNoticesSource @Inject constructor(@ApplicationContext private val context: Context) : NoticesSource {
    override suspend fun paragraphs(): List<String> = withContext(Dispatchers.IO) {
        context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() }
            .split("\n\n")
            .filter { it.isNotBlank() }
    }

    private companion object {
        const val NOTICES_ASSET = "third_party_notices.txt"
    }
}

@Composable
internal fun NoticesDialog(paragraphs: List<String>?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.licenses_notices),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.licenses_close)) }
                }
                if (paragraphs == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(paragraphs) { paragraph ->
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
