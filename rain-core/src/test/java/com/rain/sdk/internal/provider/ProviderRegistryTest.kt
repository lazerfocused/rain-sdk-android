package com.rain.sdk.internal.provider

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.core.RainSdkManager
import com.rain.sdk.internal.error.ErrorMapper
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.error.RainErrorCode
import com.rain.sdk.internal.helpers.StubWalletProvider
import com.rain.sdk.internal.transaction.TransactionCoordinator
import com.rain.sdk.internal.transaction.TransactionExecutor
import com.rain.sdk.internal.transaction.TransactionSigner
import com.rain.sdk.internal.transaction.TransactionValidator
import com.rain.sdk.models.Balance
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.models.Token
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

/**
 * Covers the provider registry (register / provider(id) / providers(matching)) and the
 * capability-routing fallback that throws [RainError.NotImplemented] when the active provider
 * does not implement an optional capability interface.
 */
class ProviderRegistryTest {

    @Test
    fun `register resolves provider by id`() {
        val manager = RainSdkManager()
        val turnkey = StubWalletProvider(id = ProviderId.TURNKEY)
        val portal = StubWalletProvider(id = ProviderId.PORTAL)

        manager.register(turnkey)
        manager.register(portal)

        assertThat(manager.provider(ProviderId.TURNKEY)).isSameInstanceAs(turnkey)
        assertThat(manager.provider(ProviderId.PORTAL)).isSameInstanceAs(portal)
    }

    @Test
    fun `register replaces a provider with the same id`() {
        val manager = RainSdkManager()
        val first = StubWalletProvider(id = ProviderId.TURNKEY)
        val second = StubWalletProvider(id = ProviderId.TURNKEY)

        manager.register(first)
        manager.register(second)

        assertThat(manager.provider(ProviderId.TURNKEY)).isSameInstanceAs(second)
    }

    @Test
    fun `provider throws WalletUnavailable for an unregistered id`() {
        val manager = RainSdkManager()
        assertThrows(RainError.WalletUnavailable::class.java) {
            manager.provider(ProviderId.PRIVY)
        }
    }

    @Test
    fun `providers matching filters by capability`() {
        val manager = RainSdkManager()
        val signer = StubWalletProvider(
            id = ProviderId.TURNKEY,
            capabilities = setOf(Capability.TYPED_DATA_SIGNING, Capability.SOLANA_TRANSFERS)
        )
        val estimator = StubWalletProvider(
            id = ProviderId.PORTAL,
            capabilities = setOf(Capability.FEE_ESTIMATION, Capability.MULTI_CHAIN)
        )
        manager.register(signer)
        manager.register(estimator)

        assertThat(manager.providers(Capability.SOLANA_TRANSFERS)).containsExactly(signer)
        assertThat(manager.providers(Capability.MULTI_CHAIN)).containsExactly(estimator)
        assertThat(manager.providers(Capability.EXPORT)).isEmpty()
    }

    @Test
    fun `signTypedData throws NotImplemented when provider lacks the capability`() = runBlocking {
        val signer = TransactionSigner({ bareProvider() }, ErrorMapper())
        try {
            signer.signTypedData(chainId = 1, walletAddress = "0xabc", typedDataJson = "{}")
            fail("expected NotImplemented")
        } catch (e: RainError.NotImplemented) {
            assertThat(e.errorCode).isEqualTo(RainErrorCode.NOT_IMPLEMENTED)
        }
    }

    @Test
    fun `estimateGas throws NotImplemented when provider lacks the capability`() = runBlocking {
        val em = ErrorMapper()
        val coordinator = TransactionCoordinator(
            walletProvider = { bareProvider() },
            validator = TransactionValidator(),
            signer = TransactionSigner({ bareProvider() }, em),
            executor = TransactionExecutor({ bareProvider() }, em)
        )
        try {
            coordinator.estimateGas(chainId = 1, from = "0xfrom", to = "0xto", data = "0x")
            fail("expected NotImplemented")
        } catch (e: RainError.NotImplemented) {
            assertThat(e.errorCode).isEqualTo(RainErrorCode.NOT_IMPLEMENTED)
        }
    }

    /** A minimal provider implementing only the base port — no optional capability interfaces. */
    private fun bareProvider(): WalletProvider = object : WalletProvider {
        override val id = ProviderId.PRIVY
        override val capabilities = emptySet<Capability>()
        override suspend fun getWalletAddress() = "0xabc"
        override suspend fun sendNativeToken(chainId: Int, toAddress: String, amountInEth: Double) = "0x0"
        override suspend fun sendToken(chainId: Int, contractAddress: String, toAddress: String, amount: Double, decimals: Int) = "0x0"
        override suspend fun getBalance(chainId: Int, token: Token) =
            Balance(Token.Native, chainId, BigInteger.ZERO, 18, "ETH", "Ether")
        override suspend fun getBalances(chainId: Int) = emptyList<Balance>()
        override suspend fun getTransactions(chainId: Int, limit: Int?, offset: Int?, order: RainTransactionOrder?) =
            RainTransactionResult(transactions = emptyList())
        override suspend fun sendTransaction(chainId: Int, from: String, to: String, data: String, value: String) = "0x0"
    }
}
