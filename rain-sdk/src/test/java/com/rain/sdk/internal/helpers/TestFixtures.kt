package com.rain.sdk.internal.helpers

import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.core.RainSdkManager
import org.junit.Assume.assumeTrue
import java.util.Base64

/**
 * Skips the calling test when the host JVM is older than JDK 24.
 *
 * Turnkey's published AAR — and `ErrorMapper`, which references `TurnkeyKotlinError` — are
 * compiled to class-file major version 68 (Java 24). Any test that transitively touches
 * those classes must gate on this so it skips cleanly on the JDK 21 bundled with Android
 * Studio rather than blowing up with `UnsupportedClassVersionError`.
 */
internal fun assumeJdk24() {
    val major = System.getProperty("java.version")?.substringBefore('.')?.toIntOrNull() ?: 0
    assumeTrue(
        "Turnkey SDK / ErrorMapper transitively load JDK-24 classes. Current: $major",
        major >= 24
    )
}

/**
 * Shared test fixtures: canonical addresses, salts, and signatures so manager-contract
 * tests don't have to invent valid bytes themselves.
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
 * Manager factories for tests.
 *
 * Uses [RainSdkManager.setWalletProviderForTest] (a `@VisibleForTesting` seam) to inject
 * a fake provider so tests don't have to drive `initializePortal` / `initializeTurnkey`.
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
