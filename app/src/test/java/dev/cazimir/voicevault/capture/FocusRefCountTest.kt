package dev.cazimir.voicevault.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRefCountTest {

    @Test
    fun `first acquire takes the count 0 to 1 and requests`() {
        val refCount = FocusRefCount()
        assertTrue(refCount.acquire())
    }

    @Test
    fun `second overlapping acquire does not re-request`() {
        val refCount = FocusRefCount()
        refCount.acquire()
        assertFalse(refCount.acquire())
    }

    @Test
    fun `release while another capture still holds it does not abandon`() {
        val refCount = FocusRefCount()
        refCount.acquire()
        refCount.acquire()
        assertFalse(refCount.release())
    }

    @Test
    fun `release that drops the count to zero abandons`() {
        val refCount = FocusRefCount()
        refCount.acquire()
        refCount.acquire()
        refCount.release()
        assertTrue(refCount.release())
    }

    @Test
    fun `a repeated release below zero is a no-op`() {
        val refCount = FocusRefCount()
        refCount.acquire()
        assertTrue(refCount.release())
        assertFalse(refCount.release())
    }
}
