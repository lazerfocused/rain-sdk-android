package com.rain.sdk.internal.network.chainreader

import com.rain.sdk.models.TokenSpec

/**
 * Provider-agnostic, read-only on-chain query surface.
 *
 * One place for reading state from any chain the SDK consumer has configured. Used by
 * `TurnkeyWalletProvider` to fill in balances on chains outside the Turnkey `get-balances`
 * allowlist, and available to any future wallet-provider adapter that needs the same
 * fallback.
 *
 * V1 surface is balances. Future reads (token metadata, allowances, generic `eth_call`
 * wrappers) belong on this interface so call sites don't fragment.
 *
 * Implementations exist per chain family. [EvmChainReader] covers all EIP-155 chains via
 * JSON-RPC. A future Solana/Stellar reader can implement this alongside it; until then,
 * `chainId: Int` matches the rest of the SDK's EVM-centric typing.
 */
internal interface ChainReader {
    /**
     * Native balance (e.g. ETH on Ethereum, AVAX on Avalanche). Result is in
     * human-readable form (e.g. `1.5` for 1.5 ETH).
     */
    suspend fun getNativeBalance(chainId: Int, walletAddress: String): Double

    /**
     * Single ERC-20 balance via `balanceOf(address)`. [decimals] defaults to
     * [com.rain.sdk.interfaces.RainClient.Companion.DEFAULT_ERC20_DECIMALS] when null.
     */
    suspend fun getERC20Balance(
        chainId: Int,
        tokenAddress: String,
        walletAddress: String,
        decimals: Int?
    ): Double

    /**
     * Batched balances for many tokens on one chain, in a single round-trip when possible.
     *
     * Returns a dictionary keyed by token contract address; the empty-string key holds
     * the native balance (matching the existing `RainSdkManager.getBalances` convention).
     */
    suspend fun getBalances(
        chainId: Int,
        walletAddress: String,
        tokens: List<TokenSpec>
    ): Map<String, Double>
}
