package com.rain.sdk.models

/** Describes an on-chain ERC-20 token the SDK can read balances for. */
internal data class TokenSpec(
    /** EIP-155 chain ID. */
    val chainId: Int,
    /** Token contract address. */
    val address: String,
    /** Token symbol (e.g. "USDC", "DAI"). */
    val symbol: String,
    /** Number of decimal places (e.g. 6 for USDC, 18 for DAI). */
    val decimals: Int,
    /** Optional human-readable token name. */
    val name: String? = null
)
