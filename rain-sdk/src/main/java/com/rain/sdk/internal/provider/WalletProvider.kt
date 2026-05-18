package com.rain.sdk.internal.provider

import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult

/**
 * Interface for abstracting wallet operations.
 * Allows the SDK to support multiple wallet providers (Portal, Magic, Web3Auth, etc.).
 */
internal interface WalletProvider {
    /**
     * Gets the current wallet address.
     */
    suspend fun getAddress(): String

    /**
     * Sends native token (e.g., AVAX).
     *
     * @param chainId The network ID (e.g., 43114 for Avalanche).
     * @param toAddress The recipient's wallet address.
     * @param amountInEth The amount of token to send (in Eth/Avax unit, not Wei).
     * @return The transaction hash of the transaction.
     */
    suspend fun sendNativeToken(
        chainId: Int,
        toAddress: String,
        amountInEth: Double
    ): String

    /**
     * Sends an ERC-20 token.
     *
     * @param chainId The network ID.
     * @param contractAddress The ERC-20 token contract address.
     * @param toAddress The recipient's wallet address.
     * @param amount The amount of token to send (in human-readable unit).
     * @param decimals The number of decimals the token uses.
     * @return The transaction hash.
     */
    suspend fun sendToken(
        chainId: Int,
        contractAddress: String,
        toAddress: String,
        amount: Double,
        decimals: Int
    ): String

    /**
     * Gets the native token balance.
     */
    suspend fun getNativeBalance(chainId: Int): Double

    /**
     * Gets the ERC-20 token balance.
     */
    suspend fun getERC20Balance(chainId: Int, tokenAddress: String, decimals: Int?): Double

    /**
     * Gets all ERC-20 token balances.
     */
    suspend fun getERC20Balances(chainId: Int): Map<String, Double>

    /**
     * Gets the transaction history for the specified chain.
     */
    suspend fun getTransactions(
        chainId: Int,
        limit: Int? = null,
        offset: Int? = null,
        order: RainTransactionOrder? = null
    ): RainTransactionResult

    /**
     * Signs EIP-712 typed data with the provider's wallet.
     *
     * @param chainId The chain ID
     * @param walletAddress The wallet address to sign with
     * @param typedDataJson The EIP-712 typed data as JSON string
     * @return The signature as a hex string (0x-prefixed)
     */
    suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String

    /**
     * Sends a low-level transaction with the provider's wallet.
     * Used by flows like withdraw-collateral that prepare their own calldata.
     *
     * @param chainId The chain ID
     * @param from The sender address
     * @param to The target contract address
     * @param data Hex-encoded calldata (or "0x" / empty for plain transfers)
     * @param value Hex-encoded wei value (e.g. "0x0")
     * @return The transaction hash
     */
    suspend fun sendTransaction(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): String

    /**
     * Estimates the total fee (in the chain's native token, e.g. ETH) for a transaction.
     *
     * @param chainId The chain ID
     * @param from The sender address
     * @param to The target contract address
     * @param data Hex-encoded calldata (or "0x" / empty for plain transfers)
     * @param value Hex-encoded wei value (e.g. "0x0")
     * @return Estimated fee in the chain's native token
     */
    suspend fun estimateTransactionFee(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): Double
}
