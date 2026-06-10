package com.rain.sdk.internal.constants

import com.rain.sdk.RainChain
import com.rain.sdk.internal.solana.SolanaConverter
import com.rain.sdk.models.NativeCurrency

/**
 * Per-cluster reference data for Solana, the analogue of [TokenRegistry]/`RainConstants` for
 * EVM. Solana has no EIP-155 integer chain ID, so the SDK keys clusters by their CAIP-2
 * (genesis-hash) identifiers, which is what Turnkey's Solana APIs (`sol_send_transaction`,
 * balances) expect.
 */
internal object SolanaChains {
    /** Known Solana cluster CAIP-2 identifiers (base58 of each cluster's genesis hash). */
    val KNOWN_CAIP2: Set<String> = setOf(
        RainChain.SOLANA_MAINNET,
        RainChain.SOLANA_TESTNET,
        RainChain.SOLANA_DEVNET
    )

    /** SOL native currency — 9 decimals (vs 18 for EVM), identical across clusters. */
    val NATIVE_CURRENCY = NativeCurrency(
        symbol = "SOL",
        name = "Solana",
        decimals = SolanaConverter.SOL_DECIMALS
    )

    /** True when [caip2] is a Solana cluster (`solana:<genesis>`). */
    fun isSolanaChain(caip2: String): Boolean = caip2.startsWith("solana:")
}
