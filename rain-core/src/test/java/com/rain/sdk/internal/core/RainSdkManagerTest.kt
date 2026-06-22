package com.rain.sdk.internal.core

import android.webkit.URLUtil
import com.google.common.truth.Truth.assertThat
import com.rain.sdk.RainChain
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.helpers.StubWalletProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Manager-contract tests independent of any specific provider. Portal initialization moved
 * to `:rain-portal` (`initializePortal` / `PortalProvider`); RPC validation is exercised here
 * via the wallet-agnostic [RainSdkManager.initialize].
 */
class RainSdkManagerTest {

  private lateinit var sdkManager: RainSdkManager

  @Before
  fun setUp() {
    RainConfig.reset()
    // URLUtil is an Android static; the unit-test JVM stubs it, so mock it green by default.
    mockkStatic(URLUtil::class)
    every { URLUtil.isValidUrl(any()) } returns true
    sdkManager = RainSdkManager()
  }

  @After
  fun tearDown() {
    unmockkAll()
    RainConfig.reset()
  }

  @Test
  fun `isInitialized starts false on a fresh manager`() {
    assertThat(RainSdkManager().isInitialized).isFalse()
  }

  @Test
  fun `turnkey getter throws SdkNotInitialized before any init`() {
    assertThrows(RainError.SdkNotInitialized::class.java) { RainSdkManager().turnkey }
  }

  // ---- RPC validation (via wallet-agnostic initialize) -------------------------

  @Test(expected = RainError.InvalidConfig::class)
  fun `initialize throws InvalidConfig when rpcEndpoints is empty`() {
    sdkManager.initialize(rpcEndpoints = emptyMap())
  }

  @Test(expected = RainError.InvalidConfig::class)
  fun `initialize throws InvalidConfig when chainId is negative`() {
    sdkManager.initialize(rpcEndpoints = mapOf(-1 to "https://rpc.com"))
  }

  @Test(expected = RainError.InvalidConfig::class)
  fun `initialize throws InvalidConfig when rpc url is invalid`() {
    every { URLUtil.isValidUrl("invalid-url") } returns false
    sdkManager.initialize(rpcEndpoints = mapOf(RainChain.AVALANCHE_MAINNET to "invalid-url"))
  }

  // ---- wallet-agnostic initialize (bring-your-own-provider) --------------------

  @Test
  fun `initialize then setWalletProvider enables wallet calls without Portal or Turnkey`(): Unit = runBlocking {
    sdkManager.initialize(rpcEndpoints = mapOf(RainChain.AVALANCHE_MAINNET to "https://rpc.com"))
    assertThat(sdkManager.isInitialized).isTrue()

    val stub = StubWalletProvider()
    stub.addressToReturn = "0xb0b0000000000000000000000000000000000000"
    sdkManager.setWalletProvider(stub)

    assertThat(sdkManager.getWalletAddress())
      .isEqualTo("0xb0b0000000000000000000000000000000000000")
  }

  @Test
  fun `initialize clears any previously installed wallet provider`() {
    sdkManager.setWalletProvider(StubWalletProvider())
    sdkManager.initialize(rpcEndpoints = mapOf(RainChain.AVALANCHE_MAINNET to "https://rpc.com"))

    // Wallet-agnostic mode starts with no provider; calls fail until one is installed.
    assertThrows(RainError.SdkNotInitialized::class.java) {
      runBlocking { sdkManager.getWalletAddress() }
    }
  }
}
