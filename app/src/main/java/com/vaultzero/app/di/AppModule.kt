package com.vaultzero.app.di

import android.content.Context
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.data.repository.VaultRepositoryImpl
import com.vaultzero.app.domain.repository.VaultRepository
import com.vaultzero.app.presentation.biometric.BiometricCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    @Provides
    @Singleton
    fun provideVaultRepository(
        biometricCrypto: BiometricCrypto,
        @ApplicationContext context: Context,
        crypto: CryptoManager
    ): VaultRepository = VaultRepositoryImpl(biometricCrypto, context, crypto)
}
