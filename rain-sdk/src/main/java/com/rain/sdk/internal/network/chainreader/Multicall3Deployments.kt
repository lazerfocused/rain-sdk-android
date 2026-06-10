package com.rain.sdk.internal.network.chainreader

/**
 * Set of EVM chains where Multicall3 is known-deployed at [Multicall3.CANONICAL_ADDRESS].
 * Used to decide between the batched `aggregate3` path and the parallel `eth_call` fallback.
 *
 * Source: https://www.multicall3.com/deployments
 */
internal val CANONICALLY_DEPLOYED_CHAIN_IDS: Set<String> = setOf(
    "eip155:1",       // Ethereum
    "eip155:10",      // Optimism
    "eip155:56",      // BNB Chain
    "eip155:137",     // Polygon
    "eip155:143",     // Monad
    "eip155:324",     // zkSync Era
    "eip155:8453",    // Base
    "eip155:9745",    // Plasma
    "eip155:42161",   // Arbitrum
    "eip155:42220",   // Celo
    "eip155:43114",   // Avalanche
    "eip155:57073",   // Ink
)

/** True when Multicall3 is known-deployed at [Multicall3.CANONICAL_ADDRESS] on [chainId]. */
internal fun isMulticall3CanonicallyDeployed(chainId: String): Boolean =
    chainId in CANONICALLY_DEPLOYED_CHAIN_IDS
