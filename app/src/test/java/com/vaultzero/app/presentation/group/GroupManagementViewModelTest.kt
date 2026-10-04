package com.vaultzero.app.presentation.group

import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getGroups() } returns flowOf(
            listOf(VaultGroup(id = "g1", name = "Work"))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads groups on init`() = runTest {
        val viewModel = GroupManagementViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.groups.size)
        assertEquals("Work", viewModel.uiState.value.groups.first().name)
    }

    @Test
    fun `addGroup saves and refreshes`() = runTest {
        coEvery { repository.getGroups() } returns flowOf(
            listOf(VaultGroup(id = "g1", name = "Work"), VaultGroup(id = "g2", name = "Personal"))
        )

        val viewModel = GroupManagementViewModel(repository)
        viewModel.onNewGroupNameChanged("Personal")
        viewModel.addGroup()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.saveGroup(any()) }
        assertEquals(2, viewModel.uiState.value.groups.size)
        assertEquals("", viewModel.uiState.value.newGroupName)
    }

    @Test
    fun `deleteGroup removes group`() = runTest {
        coEvery { repository.getGroups() } returns flowOf(emptyList())

        val viewModel = GroupManagementViewModel(repository)
        viewModel.deleteGroup("g1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.deleteGroup("g1") }
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }
}
