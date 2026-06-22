package com.rain.sdk.privy

import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.provider.Capability
import com.rain.sdk.internal.provider.ProviderId
import com.rain.sdk.internal.provider.RainTypedDataSignerProvider
import com.rain.sdk.internal.provider.WalletProvider
import com.rain.sdk.models.Balance
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.models.Token

/**
 * Scaffold Privy provider.
 *
 * Implements the [WalletProvider] port plus the typed-data signing capability, but every
 * method throws [RainError.NotImplemented] — there is no real Privy SDK dependency yet. Its
 * purpose is to prove a new provider module slots into the registry and costs existing
 * (Turnkey / Portal) clients nothing. Register it like any other provider:
 *
 * ```kotlin
 * rainClient.initialize(rpcEndpoints)
 * rainClient.register(PrivyProvider())
 * ```
 */
class PrivyProvider : WalletProvider, RainTypedDataSignerProvider {

    override val id: ProviderId = ProviderId.PRIVY

    /** Embedded-key-oriented capability set (advertised even though the methods are stubs). */
    override val capabilities: Set<Capability> = setOf(
        Capability.TYPED_DATA_SIGNING,
        Capability.EXPORT,
        Capability.RECOVERY
    )

    override suspend fun getWalletAddress(): String = notImplemented("getWalletAddress")

    override suspend fun sendNativeToken(
        chainId: Int,
        toAddress: String,
        amountInEth: Double
    ): String = notImplemented("sendNativeToken")

    override suspend fun sendToken(
        chainId: Int,
        contractAddress: String,
        toAddress: String,
        amount: Double,
        decimals: Int
    ): String = notImplemented("sendToken")

    override suspend fun getBalance(chainId: Int, token: Token): Balance =
        notImplemented("getBalance")

    override suspend fun getBalances(chainId: Int): List<Balance> =
        notImplemented("getBalances")

    override suspend fun getTransactions(
        chainId: Int,
        limit: Int?,
        offset: Int?,
        order: RainTransactionOrder?
    ): RainTransactionResult = notImplemented("getTransactions")

    override suspend fun sendTransaction(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): String = notImplemented("sendTransaction")

    override suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String = notImplemented("signTypedData")

    private fun notImplemented(method: String): Nothing =
        throw RainError.NotImplemented("PrivyProvider.$method")
}
