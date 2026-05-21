package com.rain.sdk.internal.helpers

import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.core.RainSdkManager
import java.util.Base64

/**
 * Shared test fixtures mirroring iOS's `TestFixtures.swift`.
 * Provides canonical addresses, salts, and signatures so manager-contract
 * tests don't need to invent valid bytes themselves.
 */
internal object TestFixtures {
    const val WALLET_ADDRESS = "0x1234567890123456789012345678901234567890"
    const val CONTRACT_ADDRESS = "0x1234567890123456789012345678901234567890"
    const val PROXY_ADDRESS = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
    const val CONTROLLER_ADDRESS = "0x5555555555555555555555555555555555555555"
    const val RECIPIENT_ADDRESS = "0xfedcbafedcbafedcbafedcbafedcbafedcbafedc"
    const val TOKEN_ADDRESS = "0x9876543210987654321098765432109876543210"
    const val USDC_ADDRESS = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

    /** 32-byte salt encoded as base64. Used by withdrawCollateral / estimateWithdrawalFee. */
    val validSaltBase64: String = Base64.getEncoder().encodeToString(ByteArray(32) { 0xAA.toByte() })

    /** 65-byte signature encoded as hex with `0x` prefix. */
    val validSignatureHex: String = "0x" + "01".repeat(65)
}

/**
 * Manager factories matching iOS's `TestManagers`.
 *
 * Uses [RainSdkManager.setWalletProviderForTest] (a `@VisibleForTesting` seam) to inject
 * a fake provider, mirroring iOS's public `setWalletProvider(...)` API.
 */
internal object TestManagers {

    /**
     * Returns a manager with [stub] installed as the active wallet provider and the SDK
     * marked initialized, so tests can exercise `RainSdkManager` routing without standing
     * up a real Portal or Turnkey context.
     */
    fun stubProviderManager(
        stub: StubWalletProvider = StubWalletProvider()
    ): Pair<RainSdkManager, StubWalletProvider> {
        val manager = RainSdkManager()
        manager.setWalletProviderForTest(stub)
        // RainSdkManager.isInitialized delegates to ConfigManager → RainConfig singleton.
        RainConfig.getInstance().markInitialized()
        return manager to stub
    }
}
