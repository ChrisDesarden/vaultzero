package com.vaultzero.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardHelperTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var helper: ClipboardHelper

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        clipboardManager = mockk(relaxed = true)
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        helper = ClipboardHelper(context)
    }

    @Test
    fun `copySensitive sets primary clip`() {
        helper.copySensitive("Test Label", "secret", 0)
        val clipSlot = slot<ClipData>()
        verify { clipboardManager.setPrimaryClip(capture(clipSlot)) }
        assertEquals("Test Label", clipSlot.captured.description.label)
        assertEquals("secret", clipSlot.captured.getItemAt(0).text)
    }

    @Test
    fun `copy sets primary clip without timeout`() {
        helper.copy("Label", "text")
        val clipSlot = slot<ClipData>()
        verify { clipboardManager.setPrimaryClip(capture(clipSlot)) }
        assertEquals("Label", clipSlot.captured.description.label)
        assertEquals("text", clipSlot.captured.getItemAt(0).text)
    }

    @Test
    fun `clearPrimaryClip sets empty clip`() = runTest {
        helper.clearPrimaryClip()
        val clipSlot = slot<ClipData>()
        verify { clipboardManager.setPrimaryClip(capture(clipSlot)) }
        assertEquals("", clipSlot.captured.getItemAt(0).text)
    }
}
