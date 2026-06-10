package com.rain.sdk.internal.utils

/**
 * Numeric extraction from CAIP-2 chain-ID strings
 * (https://standards.chainagnostic.org/CAIPs/caip-2).
 *
 * The SDK routes on CAIP-2 strings (e.g. `eip155:1`, `solana:<genesis-hash>`) everywhere
 * internally. A numeric chain ID is only needed in two places — the EIP-712 domain and
 * Portal's `legacyEthChainId` — and both are EVM (`eip155:N`). This enum is the single place
 * that extracts that numeric value. Mirrors the iOS SDK's `ChainIDFormat`.
 */
internal enum class ChainIdFormat(val prefix: String) {
    EIP155("eip155");

    /** Parse a CAIP-2 string in this namespace into its numeric chain ID, or `null` if it doesn't match. */
    fun parse(value: String): Int? {
        val parts = value.split(":")
        return if (parts.size != 2 || parts[0] != prefix) null else parts[1].toIntOrNull()
    }
}
