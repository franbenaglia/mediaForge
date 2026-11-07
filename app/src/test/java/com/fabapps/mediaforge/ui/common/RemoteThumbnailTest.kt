package com.fabapps.mediaforge.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteThumbnailTest {
    @Test
    fun onlyHttpsIsLoaded() {
        assertTrue(isLoadable("https://i.ytimg.com/vi/aqz-KE-bpKQ/maxresdefault.jpg"))
        assertTrue(isLoadable("HTTPS://s1.dmcdn.net/v/x.jpg"))
        assertFalse(isLoadable("http://i.ytimg.com/vi/x.jpg"))
        assertFalse(isLoadable("file:///sdcard/x.jpg"))
        assertFalse(isLoadable("content://media/external/images/1"))
        assertFalse(isLoadable(""))
    }
}
