package com.rain.sdk.internal.constants

import com.rain.sdk.models.TokenSpec

/**
 * Static registry of ERC-20 tokens the SDK knows how to read balances for.
 *
 * Used by `ChainReader` to batch-fetch balances on chains that aren't covered by a
 * wallet provider's native balance API (e.g. chains outside Turnkey's allowlist).
 *
 * Scope:
 * - EVM chains only. Solana and Stellar entries are intentionally omitted —
 *   Turnkey already covers Solana, and the SDK has no Solana or Horizon client.
 *
 * Maintenance:
 * This list lives in-tree, so the SDK owns updates. When tokens are added, removed,
 * or migrated, edit this file and ship a new SDK release. Decimals are best-effort
 * based on the canonical issuer's documentation at time of authoring.
 *
 * Multicall3 status:
 * Chains where Multicall3 is deployed at the canonical address are listed in
 * `Multicall3.CANONICALLY_DEPLOYED_CHAIN_IDS` — those use the batched `aggregate3` path.
 * Any chain not in that set uses the parallel `eth_call` fallback.
 */
internal object TokenRegistry {

    val tokensByChainId: Map<Int, List<TokenSpec>> = mapOf(
        // Ethereum
        1 to listOf(
            TokenSpec(1, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", 6, "USDC"),
            TokenSpec(1, "0x6c3ea9036406852006290770BEdFcAbA0e23A0e8", "PYUSD", 6, "PayPal USD"),
            TokenSpec(1, "0xdAC17F958D2ee523a2206206994597C13D831ec7", "USDT", 6, "Tether USD"),
            TokenSpec(1, "0xfdcC3dd6671eaB0709A4C0f3F53De9a333d80798", "SBC", 18, "SBC"),
            TokenSpec(1, "0x4F604735c1cF31399C6E711D5962b2B3E0225AD3", "USDGLO", 18, "Glo Dollar"),
            TokenSpec(1, "0xC9E3df3D230980B45adC623C81C3DF4A73a5350f", "USD+", 6, "USD+"),
            TokenSpec(1, "0xe343167631d89B6Ffc58B88d6b7fB0228795491D", "USDG", 6, "Global Dollar")
        ),
        // Optimism
        10 to listOf(
            TokenSpec(10, "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85", "USDC", 6, "USDC"),
            TokenSpec(10, "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1", "DAI", 18, "Dai Stablecoin"),
            TokenSpec(10, "0x94b008aA00579c1307B0EF2c499aD98a8ce58e58", "USDT", 6, "Tether USD"),
            TokenSpec(10, "0xf9FB20B8E097904f0aB7d12e9DbeE88f2dcd0F16", "SBC", 18, "SBC"),
            TokenSpec(10, "0x4F604735c1cF31399C6E711D5962b2B3E0225AD3", "USDGLO", 18, "Glo Dollar")
        ),
        // BNB Chain
        56 to listOf(
            TokenSpec(56, "0x8AC76a51cc950d9822D68b83Fe1AD97B32Cd580d", "USDC", 18, "USDC"),
            TokenSpec(56, "0x55d398326f99059fF775485246999027B3197955", "USDT", 18, "Tether USD")
        ),
        // Polygon
        137 to listOf(
            TokenSpec(137, "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359", "USDC", 6, "USDC"),
            TokenSpec(137, "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174", "USDC.e", 6, "Bridged USDC"),
            TokenSpec(137, "0xc2132D05D31c914a87C6611C10748AEb04B58e8F", "USDT", 6, "Tether USD"),
            TokenSpec(137, "0xfdcC3dd6671eaB0709A4C0f3F53De9a333d80798", "SBC", 18, "SBC"),
            TokenSpec(137, "0x4F604735c1cF31399C6E711D5962b2B3E0225AD3", "USDGLO", 18, "Glo Dollar"),
            TokenSpec(137, "0x949E7b96C3946A0A035d33094FcB58418d50c505", "rUSD", 6, "Rain USD")
        ),
        // Monad
        143 to listOf(
            TokenSpec(143, "0x754704Bc059F8C67012fEd69BC8A327a5aafb603", "USDC", 6, "USDC")
        ),
        // zkSync Era
        324 to listOf(
            TokenSpec(324, "0x1d17CBcF0D6D143135aE902365D2E5e2A16538D4", "USDC", 6, "USDC")
        ),
        // Base
        8453 to listOf(
            TokenSpec(8453, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", "USDC", 6, "USDC"),
            TokenSpec(8453, "0xFa2ACD0861Bd3219D5764d349D3a970AE8321620", "DKUSD", 18, "DKUSD"),
            TokenSpec(8453, "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb", "DAI", 18, "Dai Stablecoin"),
            TokenSpec(8453, "0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2", "USDT", 6, "Tether USD"),
            TokenSpec(8453, "0xfdcC3dd6671eaB0709A4C0f3F53De9a333d80798", "SBC", 18, "SBC"),
            TokenSpec(8453, "0xd899C2254C1F4B11FfF038571D6cb02aB8860eC8", "rUSD", 6, "Rain USD"),
            TokenSpec(8453, "0x4F604735c1cF31399C6E711D5962b2B3E0225AD3", "USDGLO", 18, "Glo Dollar")
        ),
        // Plasma
        9745 to listOf(
            TokenSpec(9745, "0xB8CE59FC3717ada4C02eaDF9682A9e934F625ebb", "USDT0", 6, "USDT0")
        ),
        // Arbitrum
        42161 to listOf(
            TokenSpec(42161, "0xaf88d065e77c8cC2239327C5EDb3A432268e5831", "USDC", 6, "USDC"),
            TokenSpec(42161, "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1", "DAI", 18, "Dai Stablecoin"),
            TokenSpec(42161, "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9", "USDT", 6, "Tether USD"),
            TokenSpec(42161, "0xfdcC3dd6671eaB0709A4C0f3F53De9a333d80798", "SBC", 18, "SBC"),
            TokenSpec(42161, "0x4F604735c1cF31399C6E711D5962b2B3E0225AD3", "USDGLO", 18, "Glo Dollar"),
            TokenSpec(42161, "0xC9E3df3D230980B45adC623C81C3DF4A73a5350f", "USD+", 6, "USD+")
        ),
        // Celo
        42220 to listOf(
            TokenSpec(42220, "0xceba9300f2b948710d2653dd7b07f33a8b32118c", "USDC", 6, "USDC"),
            TokenSpec(42220, "0x48065fbBE25f71C9282DDF5e1CD6D6A887483D5e", "USDT", 6, "Tether USD")
        ),
        // Avalanche
        43114 to listOf(
            TokenSpec(43114, "0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", "USDC", 6, "USDC"),
            TokenSpec(43114, "0xd586E7F844cEa2F87f50152665BCbc2C279D8d70", "DAI", 18, "Dai Stablecoin"),
            TokenSpec(43114, "0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7", "USDT", 6, "Tether USD"),
            TokenSpec(43114, "0xf9FB20B8E097904f0aB7d12e9DbeE88f2dcd0F16", "SBC", 18, "SBC"),
            TokenSpec(43114, "0xB31f66AA3C1e785363F0875A1B74E27b85FD66c7", "WAVAX", 18, "Wrapped AVAX"),
            TokenSpec(43114, "0x5E817F2AbCCB9095585D26c2a3ce234a440574Fc", "FRNT", 18, "FRNT"),
            TokenSpec(43114, "0xFd56187DCe1A7c5Ad5aaE9cA3A8827267e69E58a", "TenantToken", 18, "TenantToken (Raindrop)")
        ),
        // Ink
        57073 to listOf(
            TokenSpec(57073, "0x2D270e6886d130D724215A266106e6832161EAEd", "USDC", 6, "USDC"),
            TokenSpec(57073, "0xe343167631d89B6Ffc58B88d6b7fB0228795491D", "USDG", 6, "Global Dollar")
        )
    )

    /** Returns the configured tokens for a chain, or empty if none are known. */
    fun tokensFor(chainId: Int): List<TokenSpec> = tokensByChainId[chainId] ?: emptyList()
}
