package com.vaultzero.app.presentation.home

import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: VaultRepository
    private val entriesFlow = MutableStateFlow(
        listOf(
            VaultEntry(id = "1", title = "Bank", username = "user", groupId = "g1"),
            VaultEntry(id = "2", title = "Email", username = "mail", groupId = "g1"),
            VaultEntry(id = "3", title = "WiFi", username = "", groupId = null)
        )
    )
    private val groupsFlow = MutableStateFlow(listOf(VaultGroup(id = "g1", name = "Personal")))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.entries } returns entriesFlow
        every { repository.groups } returns groupsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads entries and groups on init`() = runTest {
        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.entries.size)
        assertEquals(1, viewModel.uiState.value.groups.size)
    }

    @Test
    fun `search filters entries by title`() = runTest {
        val viewModel = HomeViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("bank")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals("Bank", viewModel.uiState.value.entries.first().title)
    }

    @Test
    fun `lock calls repository lock`() = runTest {
        val viewModel = HomeViewModel(repository)
        viewModel.lock()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.lock() }
    }
}
