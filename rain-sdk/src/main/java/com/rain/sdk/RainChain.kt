package com.rain.sdk

/**
 * Well-known CAIP-2 chain identifiers (https://standards.chainagnostic.org/CAIPs/caip-2).
 *
 * EVM chains are `eip155:<chainId>`; Solana clusters are `solana:<genesis-hash>` (there is no
 * EIP-155 numeric chain ID for Solana). The SDK routes on these CAIP-2 strings everywhere
 * internally.
 */
object RainChain {
    const val AVALANCHE_MAINNET = "eip155:43114"
    const val AVALANCHE_TESTNET = "eip155:43113"

    // Solana clusters, keyed by their CAIP-2 (genesis-hash based) identifiers.
    const val SOLANA_MAINNET = "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
    const val SOLANA_TESTNET = "solana:4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"
    const val SOLANA_DEVNET = "solana:EtWTRABZaYq6iMfeYKouRu166VU2xqa1"
}
