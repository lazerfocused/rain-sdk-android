package com.rain.sdk.privy

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.provider.Capability
import com.rain.sdk.internal.provider.ProviderId
import com.rain.sdk.models.Token
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivyProviderTest {

    private val provider = PrivyProvider()

    @Test
    fun `id is privy`() {
        assertThat(provider.id).isEqualTo(ProviderId.PRIVY)
    }

    @Test
    fun `advertises typed-data signing, export, recovery`() {
        assertThat(provider.capabilities).containsExactly(
            Capability.TYPED_DATA_SIGNING,
            Capability.EXPORT,
            Capability.RECOVERY
        )
    }

    @Test
    fun `getWalletAddress throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.getWalletAddress() }
        }
    }

    @Test
    fun `sendNativeToken throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.sendNativeToken(1, "0xabc", 1.0) }
        }
    }

    @Test
    fun `sendToken throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.sendToken(1, "0xc", "0xabc", 1.0, 6) }
        }
    }

    @Test
    fun `getBalance throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.getBalance(1, Token.Native) }
        }
    }

    @Test
    fun `getTransactions throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.getTransactions(1, null, null, null) }
        }
    }

    @Test
    fun `signTypedData throws NotImplemented`() {
        assertThrows(RainError.NotImplemented::class.java) {
            runBlocking { provider.signTypedData(1, "0xabc", "{}") }
        }
    }
}
