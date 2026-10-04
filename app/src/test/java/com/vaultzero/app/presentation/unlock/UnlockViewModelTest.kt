package com.vaultzero.app.presentation.unlock

import app.cash.turbine.test
import com.vaultzero.app.biometric.BiometricHelper
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: VaultRepository
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var viewModel: UnlockViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        biometricHelper = mockk(relaxed = true)
        every { biometricHelper.canAuthenticate() } returns true
        coEvery { repository.isVaultCreated() } returns true
        viewModel = UnlockViewModel(repository, biometricHelper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows biometric button when available`() {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showBiometricButton)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `password change updates state`() {
        viewModel.onPasswordChanged("secret123")
        assertEquals("secret123", viewModel.uiState.value.password)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `unlock emits success event when password is correct`() = runTest {
        coEvery { repository.unlock("correct") } returns UnlockResult.Success
        viewModel.onPasswordChanged("correct")

        viewModel.events.test {
            viewModel.submit()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UnlockViewModel.UnlockEvent.Unlocked, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unlock shows error when password is wrong`() = runTest {
        coEvery { repository.unlock("wrong") } returns UnlockResult.Error("Incorrect password")
        viewModel.onPasswordChanged("wrong")
        viewModel.submit()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Incorrect password", viewModel.uiState.value.error)
    }

    @Test
    fun `biometric success emits unlocked event`() = runTest {
        viewModel.events.test {
            viewModel.onBiometricSuccess()
            assertEquals(UnlockViewModel.UnlockEvent.Unlocked, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
