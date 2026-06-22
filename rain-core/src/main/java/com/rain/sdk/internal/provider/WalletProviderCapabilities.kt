package com.rain.sdk.internal.provider

/**
 * Stable identifier for a wallet provider. Mirrors the iOS `ProviderID`. Baseline Turnkey
 * ships in `:rain-core`; Portal/Privy live in their own modules.
 */
enum class ProviderId { TURNKEY, PORTAL, PRIVY }

/**
 * Optional behaviours a provider may advertise. A provider lists what it supports in
 * [WalletProvider.capabilities]; each maps to an optional capability interface the adapter
 * may implement. Mirrors the iOS `Capability` set (codes kept aligned across platforms).
 */
enum class Capability {
    TYPED_DATA_SIGNING,   // RainTypedDataSignerProvider
    FEE_ESTIMATION,       // RainTransactionFeeEstimatingProvider
    SOLANA_TRANSFERS,     // RainSolanaTransfersProvider
    EXPORT,
    RECOVERY,
    MULTI_CHAIN,
    BIOMETRIC_GATE
}

/**
 * Optional capability: EIP-712 typed-data signing. A provider that signs typed data
 * implements this and lists [Capability.TYPED_DATA_SIGNING]. Flows that need it cast the
 * active provider to this interface, throwing [com.rain.sdk.internal.error.RainError.NotImplemented]
 * when absent.
 */
interface RainTypedDataSignerProvider {
    suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String
}

/**
 * Optional capability: transaction fee estimation in the chain's native token.
 */
interface RainTransactionFeeEstimatingProvider {
    suspend fun estimateTransactionFee(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): Double
}

/**
 * Optional capability marker: the provider supports Solana transfers. On Android, Solana
 * sends/balances/history route through the standard [WalletProvider] methods by chain ID
 * (see [com.rain.sdk.internal.constants.SolanaChains]); this marker plus
 * [Capability.SOLANA_TRANSFERS] advertise that support so consumers can branch without a
 * vendor check.
 */
interface RainSolanaTransfersProvider
