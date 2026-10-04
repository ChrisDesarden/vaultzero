package com.vaultzero.app.presentation.vault

import app.cash.turbine.test
import com.vaultzero.app.clipboard.ClipboardHelper
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: VaultRepository
    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var viewModel: EntryDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        clipboardHelper = mockk(relaxed = true)
        viewModel = EntryDetailViewModel(repository, clipboardHelper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadEntry populates state`() = runTest {
        val entry = VaultEntry(id = "e1", title = "Test", username = "user", password = "pass")
        coEvery { repository.getEntry("e1") } returns entry

        viewModel.loadEntry("e1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.entry)
        assertEquals("Test", viewModel.uiState.value.entry?.title)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadEntry with missing id leaves entry null`() = runTest {
        coEvery { repository.getEntry("missing") } returns null

        viewModel.loadEntry("missing")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.entry)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `copyPassword emits copied event`() = runTest {
        viewModel.events.test {
            viewModel.copyPassword("secret")
            assertEquals(EntryDetailViewModel.EntryDetailEvent.Copied, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteEntry emits deleted event`() = runTest {
        coEvery { repository.deleteEntry("e1") } returns Unit

        viewModel.events.test {
            viewModel.deleteEntry("e1")
            assertEquals(EntryDetailViewModel.EntryDetailEvent.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
