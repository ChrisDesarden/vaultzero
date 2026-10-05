package com.vaultzero.app.di

import android.content.Context
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.data.repository.SqlCipherVaultRepository
import com.vaultzero.app.domain.repository.VaultRepository
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
        @ApplicationContext context: Context,
        crypto: CryptoManager
    ): VaultRepository = SqlCipherVaultRepository(context, crypto)
}
