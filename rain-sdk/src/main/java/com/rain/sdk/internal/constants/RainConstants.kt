package com.rain.sdk.internal.constants

internal object RainConstants {
    const val FUNC_ADMIN_NONCE = "adminNonce"
    const val FUNC_WITHDRAW_ASSET = "withdrawAsset"

    // Network Config
    const val NETWORK_TIMEOUT_SECONDS = 30L

    /**
     * Chains for which the Turnkey `get-balances` API returns data.
     * On any other chain, balance reads fall through to `ChainReader`.
     * Source: https://docs.turnkey.com/api-reference/queries/get-balances
     */
    val TURNKEY_SUPPORTED_CHAINS: Set<String> = setOf(
        "eip155:1",        // Ethereum Mainnet
        "eip155:11155111", // Sepolia
        "eip155:8453",     // Base Mainnet
        "eip155:84532",    // Base Sepolia
        "eip155:137",      // Polygon Mainnet
        "eip155:80002"     // Polygon Amoy
    )
}
