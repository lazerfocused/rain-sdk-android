package com.rain.sdk.internal.core

import android.webkit.URLUtil
import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.helpers.StubWalletProvider
import com.rain.sdk.internal.helpers.TestFixtures
import com.rain.sdk.internal.helpers.TestManagers
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Tests for `RainSdkManager.getAllBalances` — fan-out across configured chains, per-chain
 * failure isolation, and the `reset()` lifecycle hook.
 */
class RainSdkManagerGetAllBalancesTest {

    @Before
    fun setUp() {
        RainConfig.reset()
        mockkStatic(URLUtil::class)
        every { URLUtil.isValidUrl(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
        RainConfig.reset()
    }

    @Test
    fun `getAllBalances throws SdkNotInitialized before initialization`() {
        val manager = RainSdkManager()
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getAllBalances() }
        }
    }

    @Test
    fun `getAllBalances returns empty map when no chains were configured`(): Unit = runBlocking {
        // stubProviderManager marks the SDK initialized but doesn't go through
        // `initializePortal/Turnkey`, so configuredChainIds stays empty.
        val (manager, _) = TestManagers.stubProviderManager()
        assertThat(manager.getAllBalances()).isEmpty()
    }

    @Test
    fun `getAllBalances tolerates per-chain failures and surfaces other chains intact`(): Unit = runBlocking {
        val failingChain = 137
        val workingChain = 43114
        val stub = object : StubWalletProvider() {
            override suspend fun getNativeBalance(chainId: Int): Double = when (chainId) {
                workingChain -> 1.5
                else -> throw RuntimeException("native indexer down for $chainId")
            }
            override suspend fun getERC20Balances(chainId: Int): Map<String, Double> = when (chainId) {
                workingChain -> mapOf(TestFixtures.USDC_ADDRESS to 100.0)
                else -> throw RuntimeException("erc20 indexer down for $chainId")
            }
        }
        val (manager, _) = TestManagers.stubProviderManager(stub)
        // Inject chain IDs the same way `initializePortal` would.
        manager.setConfiguredChainIdsForTest(listOf(workingChain, failingChain))

        val balances = manager.getAllBalances()

        assertThat(balances.keys).containsExactly(workingChain, failingChain)
        // Failed chain → empty inner map (both calls threw).
        assertThat(balances[failingChain]).isEmpty()
        // Successful chain → native under "" plus the ERC-20.
        assertThat(balances[workingChain]).containsExactly(
            "", 1.5,
            TestFixtures.USDC_ADDRESS, 100.0
        )
    }

    @Test
    fun `reset clears wallet provider and configured chain IDs`(): Unit = runBlocking {
        val (manager, _) = TestManagers.stubProviderManager()
        manager.setConfiguredChainIdsForTest(listOf(1, 137))

        manager.reset()

        // After reset, getAddress / getAllBalances see no provider and no init state.
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getAddress() }
        }
        assertThrows(RainError.SdkNotInitialized::class.java) {
            runBlocking { manager.getAllBalances() }
        }
    }

    @Test
    fun `reset is idempotent`() {
        val manager = RainSdkManager()
        manager.reset()
        manager.reset()
        // No exception thrown — done.
    }
}
