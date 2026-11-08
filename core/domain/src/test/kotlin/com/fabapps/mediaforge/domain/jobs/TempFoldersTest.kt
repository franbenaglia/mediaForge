package com.fabapps.mediaforge.domain.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class TempFoldersTest {
    @Test
    fun `folders of running jobs are kept`() {
        assertEquals(listOf("old", "gone"), TempFolders.removable(listOf("old", "now", "gone"), setOf("now")))
    }

    @Test
    fun `nothing running means everything goes`() {
        assertEquals(listOf("a", "b"), TempFolders.removable(listOf("a", "b"), emptySet()))
    }

    @Test
    fun `odd names are never removed`() {
        assertEquals(emptyList<String>(), TempFolders.removable(listOf("", ".", ".."), emptySet()))
    }
}
