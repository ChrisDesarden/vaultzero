package com.vaultzero.app.presentation.settings

import com.vaultzero.app.biometric.BiometricHelper
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.repository.VaultRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: VaultRepository
    private lateinit var biometricHelper: BiometricHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        biometricHelper = mockk(relaxed = true)
        every { biometricHelper.canAuthenticate() } returns true
        every { repository.theme } returns flowOf(AppTheme.SYSTEM)
        every { repository.autoLockTimeout } returns flowOf(AutoLockTimeout.FIVE_MINUTES)
        every { repository.passwordDefaults } returns flowOf(PasswordGeneratorDefaults())
        every { repository.biometricEnabled } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings on init`() = runTest {
        val viewModel = SettingsViewModel(repository, biometricHelper)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppTheme.SYSTEM, viewModel.uiState.value.theme)
        assertEquals(AutoLockTimeout.FIVE_MINUTES, viewModel.uiState.value.autoLockTimeout)
    }

    @Test
    fun `setTheme saves new theme`() = runTest {
        val viewModel = SettingsViewModel(repository, biometricHelper)
        viewModel.setTheme(AppTheme.DARK)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.setTheme(AppTheme.DARK) }
    }

    @Test
    fun `setAutoLockTimeout saves timeout`() = runTest {
        val viewModel = SettingsViewModel(repository, biometricHelper)
        viewModel.setAutoLockTimeout(AutoLockTimeout.ONE_HOUR)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.setAutoLockTimeout(AutoLockTimeout.ONE_HOUR) }
    }

    @Test
    fun `togglePasswordOption saves updated defaults`() = runTest {
        val viewModel = SettingsViewModel(repository, biometricHelper)
        viewModel.togglePasswordOption(SettingsViewModel.PasswordOption.UPPERCASE, false)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.setPasswordDefaults(any()) }
    }
}
