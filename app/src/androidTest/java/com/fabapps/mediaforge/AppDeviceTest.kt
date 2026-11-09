package com.fabapps.mediaforge

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La app real en el teléfono (main.md §12.3, T11.4): navegación, enlace compartido y recreación.
 * No cambia ajustes ni historial; `test-device.sh` deja la app instalada con sus datos.
 */
@RunWith(AndroidJUnit4::class)
class AppDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun launcher() = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)

    /** Ítem de la barra inferior: el título también está arriba, pero ese no se toca. */
    private fun ComposeTestRule.tab(label: Int): SemanticsNodeInteraction =
        onNode(hasText(text(label)) and hasClickAction())

    private fun ComposeTestRule.urlField(): SemanticsNodeInteraction = onNode(hasSetTextAction())

    private fun ComposeTestRule.waitFor(label: String) = waitUntil(TIMEOUT_MS) {
        onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun everyTabOpensItsScreen() {
        ActivityScenario.launch<MainActivity>(launcher()).use {
            compose.waitFor(text(R.string.home_headline))
            val tabs = listOf(
                R.string.nav_downloads,
                R.string.nav_converter,
                R.string.nav_history,
                R.string.nav_settings,
                R.string.nav_home,
            )
            tabs.forEach { label ->
                compose.tab(label).performClick()
                compose.waitForIdle()
                compose.tab(label).assertIsSelected()
            }
            compose.waitFor(text(R.string.home_headline))
        }
    }

    @Test
    fun sharedLinkOpensHomeWithTheUrl() {
        val shared = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Mirá esto https://www.youtube.com/watch?v=aqz-KE-bpKQ ¡buenísimo!")
        ActivityScenario.launch<MainActivity>(shared).use {
            // Solo la URL, sin el resto del texto; el análisis arranca solo (ADR-018).
            compose.waitFor("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            compose.tab(R.string.nav_home).assertIsSelected()
        }
    }

    @Test
    fun recreatingKeepsTheTabAndTheTypedLink() {
        ActivityScenario.launch<MainActivity>(launcher()).use { scenario ->
            compose.waitFor(text(R.string.home_headline))
            compose.urlField().performTextReplacement("https://example.com/video")
            scenario.recreate()
            compose.waitFor("https://example.com/video")

            compose.tab(R.string.nav_converter).performClick()
            compose.waitForIdle()
            scenario.recreate()
            compose.waitForIdle()
            compose.tab(R.string.nav_converter).assertIsSelected()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
