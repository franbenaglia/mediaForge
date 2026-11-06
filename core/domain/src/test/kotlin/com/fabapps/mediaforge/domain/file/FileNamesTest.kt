package com.fabapps.mediaforge.domain.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun keepsNormalTitle() {
        assertEquals("Big Buck Bunny.mp4", FileNames.sanitize("Big Buck Bunny", "mp4"))
    }

    @Test
    fun removesPathSeparatorsAndTraversal() {
        val name = FileNames.sanitize("../../etc/passwd", "mp3")
        assertFalse(name.contains('/'))
        assertFalse(name.startsWith("."))
        assertEquals("etc passwd.mp3", name)
    }

    @Test
    fun removesBackslashAndReservedCharacters() {
        assertEquals("a b c d e f g h.mp3", FileNames.sanitize("a\\b:c*d?e\"f<g>h", "mp3"))
    }

    @Test
    fun removesControlCharactersAndCollapsesSpaces() {
        assertEquals("line one two.mp3", FileNames.sanitize("line\none\t\u0000  two", "mp3"))
    }

    @Test
    fun removesTrailingDotsAndSpaces() {
        assertEquals("title.mp3", FileNames.sanitize("title. . ", "mp3"))
    }

    @Test
    fun emptyOrOnlyInvalidUsesFallback() {
        assertEquals("media.mp3", FileNames.sanitize("", "mp3"))
        assertEquals("media.mp3", FileNames.sanitize("///...", "mp3"))
    }

    @Test
    fun reservedWindowsNamesAreChanged() {
        assertEquals("CON_.mp3", FileNames.sanitize("con".uppercase(), "mp3"))
    }

    @Test
    fun normalizesExtension() {
        assertEquals("a.mp3", FileNames.sanitize("a", ".MP3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExtensionWithSeparator() {
        FileNames.sanitize("a", "mp3/../x")
    }

    @Test
    fun truncatesToByteLimitWithoutBreakingCharacters() {
        val title = "ñ".repeat(300) + "😀".repeat(50)
        val name = FileNames.sanitize(title, "flac")
        assertTrue(name.toByteArray().size <= FileNames.MAX_NAME_BYTES)
        assertTrue(name.endsWith(".flac"))
        // Sin caracteres de reemplazo: no se cortó un carácter de varios bytes.
        assertFalse(name.contains('�'))
        assertEquals(name, String(name.toByteArray()))
    }

    @Test
    fun truncatesEmojiAtCodePointBoundary() {
        val name = FileNames.sanitize("😀".repeat(100), "mp3")
        assertTrue(name.toByteArray().size <= FileNames.MAX_NAME_BYTES)
        // Cada emoji ocupa 4 bytes: entran 62 en 251 bytes, sin medio emoji suelto.
        assertEquals("😀".repeat(62) + ".mp3", name)
    }

    @Test
    fun uniqueReturnsSameNameWhenFree() {
        assertEquals("a.mp3", FileNames.unique("a.mp3") { false })
    }

    @Test
    fun uniqueAddsCounterBeforeExtension() {
        val taken = setOf("a.mp3", "a (1).mp3")
        assertEquals("a (2).mp3", FileNames.unique("a.mp3") { it in taken })
    }

    @Test
    fun uniqueKeepsByteLimit() {
        val long = FileNames.sanitize("x".repeat(400), "mp3")
        val result = FileNames.unique(long) { it == long }
        assertTrue(result.toByteArray().size <= FileNames.MAX_NAME_BYTES)
        assertTrue(result.endsWith(" (1).mp3"))
    }
}
