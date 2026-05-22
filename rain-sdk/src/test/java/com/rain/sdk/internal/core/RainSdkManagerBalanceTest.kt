package com.rain.sdk.internal.core

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.helpers.StubWalletProvider
import com.rain.sdk.internal.helpers.TestFixtures
import com.rain.sdk.internal.helpers.TestManagers
import com.rain.sdk.internal.helpers.assumeJdk24
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Manager-contract tests for balance APIs — validation, mode guards, and routing
 * through the active [WalletProvider]. Provider-specific success paths live in
 * `PortalWalletProviderTest` / `TurnkeyWalletProviderTest`.
 *
 * Note: an un-initialized manager surfaces every balance call as
 * [RainError.SdkNotInitialized] (via the `walletProvider ?: throw SdkNotInitialized()`
 * guard), not as `WalletUnavailable`.
 */
class RainSdkManagerBalanceTest {

    @Before
    fun setUp() {
        RainConfig.reset()
    }

    @After
    fun tearDown() {
        RainConfig.reset()
    }

    // ---- guards: not initialized --------------------------------------------------

    @Test
    fun `getNativeBalance throws SdkNotInitialized before initialization`() {
        val manager = RainSdkManager()
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getNativeBalance(chainId = 1) }
        }
    }

    @Test
    fun `getERC20Balance throws SdkNotInitialized before initialization`() {
        val manager = RainSdkManager()
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getERC20Balance(chainId = 1, tokenAddress = TestFixtures.USDC_ADDRESS) }
        }
    }

    @Test
    fun `getERC20Balances throws SdkNotInitialized before initialization`() {
        val manager = RainSdkManager()
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getERC20Balances(chainId = 1) }
        }
    }

    @Test
    fun `getBalances throws SdkNotInitialized before initialization`() {
        val manager = RainSdkManager()
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getBalances(chainId = 1) }
        }
    }

    // ---- happy paths via the stub provider ----------------------------------------

    @Test
    fun `getNativeBalance returns whatever the provider returned`(): Unit = runBlocking {
        val (manager, stub) = TestManagers.stubProviderManager()
        stub.nativeBalanceToReturn = 42.5

        val balance = manager.getNativeBalance(chainId = 1)

        assertThat(balance).isEqualTo(42.5)
        assertThat(stub.getNativeBalanceCalls).containsExactly(1)
    }

    @Test
    fun `getERC20Balance forwards token-chain-decimals and returns provider result`(): Unit = runBlocking {
        val (manager, stub) = TestManagers.stubProviderManager()
        stub.erc20BalanceToReturn = 7.0

        val balance = manager.getERC20Balance(
            chainId = 1,
            tokenAddress = TestFixtures.USDC_ADDRESS,
            decimals = 6
        )

        assertThat(balance).isEqualTo(7.0)
        assertThat(stub.getErc20BalanceCalls).hasSize(1)
        val call = stub.getErc20BalanceCalls.single()
        assertThat(call.chainId).isEqualTo(1)
        assertThat(call.tokenAddress).isEqualTo(TestFixtures.USDC_ADDRESS)
        assertThat(call.decimals).isEqualTo(6)
    }

    @Test
    fun `getERC20Balances returns whatever the provider returned`(): Unit = runBlocking {
        val (manager, stub) = TestManagers.stubProviderManager()
        stub.erc20BalancesToReturn = mapOf(TestFixtures.USDC_ADDRESS to 100.0)

        val balances = manager.getERC20Balances(chainId = 1)

        assertThat(balances).isEqualTo(mapOf(TestFixtures.USDC_ADDRESS to 100.0))
    }

    @Test
    fun `getBalances merges native balance under empty key with ERC20 balances`(): Unit = runBlocking {
        val (manager, stub) = TestManagers.stubProviderManager()
        stub.nativeBalanceToReturn = 1.5
        stub.erc20BalancesToReturn = mapOf(TestFixtures.USDC_ADDRESS to 100.0)

        val balances = manager.getBalances(chainId = 1)

        assertThat(balances).hasSize(2)
        assertThat(balances[""]).isEqualTo(1.5)
        assertThat(balances[TestFixtures.USDC_ADDRESS]).isEqualTo(100.0)
    }

    @Test
    fun `getBalances still returns native under empty key when no ERC20 balances`(): Unit = runBlocking {
        val (manager, stub) = TestManagers.stubProviderManager()
        stub.nativeBalanceToReturn = 0.25
        stub.erc20BalancesToReturn = emptyMap()

        val balances = manager.getBalances(chainId = 1)

        assertThat(balances).containsExactly("", 0.25)
    }

    @Test
    fun `getBalances wraps unexpected provider failures via ErrorMapper`() {
        assumeJdk24()
        val failing = object : StubWalletProvider() {
            override suspend fun getERC20Balances(chainId: Int): Map<String, Double> {
                throw RuntimeException("indexer 503")
            }
        }
        val (manager, _) = TestManagers.stubProviderManager(failing)

        val ex = runCatching { runBlocking { manager.getBalances(chainId = 1) } }.exceptionOrNull()
        // Generic RuntimeException → ProviderError per ErrorMapper.mapTransactionError.
        assertThat(ex).isInstanceOf(RainError.ProviderError::class.java)
    }

    @Test
    fun `getNativeBalance rethrows RainError WalletUnavailable without re-wrapping`() {
        val failing = object : StubWalletProvider() {
            override suspend fun getNativeBalance(chainId: Int): Double {
                throw RainError.WalletUnavailable("no wallet")
            }
        }
        val (manager, _) = TestManagers.stubProviderManager(failing)
        assertThrows(RainError.WalletUnavailable::class.java) {
            runBlocking { manager.getNativeBalance(chainId = 1) }
        }
    }
}
