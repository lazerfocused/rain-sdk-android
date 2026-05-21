package com.rain.sdk.internal.helpers

import com.rain.sdk.internal.provider.WalletProvider
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult

/**
 * Provider-agnostic stub for manager-contract tests. Records calls and returns
 * configured values so tests can verify `RainSdkManager` routes to the active
 * `WalletProvider` and surfaces its result, independent of Portal- or
 * Turnkey-specific behavior.
 *
 * Mirrors iOS's `StubWalletProvider.swift`.
 */
internal open class StubWalletProvider : WalletProvider {

    data class SendTokenCall(
        val chainId: Int,
        val contractAddress: String,
        val toAddress: String,
        val amount: Double,
        val decimals: Int
    )

    data class GetErc20BalanceCall(
        val chainId: Int,
        val tokenAddress: String,
        val decimals: Int?
    )

    data class GetTransactionsCall(
        val chainId: Int,
        val limit: Int?,
        val offset: Int?,
        val order: RainTransactionOrder?
    )

    data class SignTypedDataCall(
        val chainId: Int,
        val walletAddress: String,
        val typedDataJson: String
    )

    data class SendTransactionCall(
        val chainId: Int,
        val from: String,
        val to: String,
        val data: String,
        val value: String
    )

    data class EstimateTransactionFeeCall(
        val chainId: Int,
        val from: String,
        val to: String,
        val data: String,
        val value: String
    )

    var addressToReturn: String = TestFixtures.WALLET_ADDRESS
    var nativeBalanceToReturn: Double = 0.0
    var erc20BalanceToReturn: Double = 0.0
    var erc20BalancesToReturn: Map<String, Double> = emptyMap()
    var transactionsToReturn: RainTransactionResult = RainTransactionResult(transactions = emptyList())
    var sendNativeTokenHashToReturn: String = "0x" + "0".repeat(64)
    var sendTokenHashToReturn: String = "0x" + "0".repeat(64)
    var sendTransactionHashToReturn: String = "0x" + "0".repeat(64)
    var signTypedDataToReturn: String = "0x" + "0".repeat(130)
    var estimateTransactionFeeToReturn: Double = 0.0

    val sendNativeTokenCalls = mutableListOf<SendTokenCall>()
    val sendTokenCalls = mutableListOf<SendTokenCall>()
    val getNativeBalanceCalls = mutableListOf<Int>()
    val getErc20BalanceCalls = mutableListOf<GetErc20BalanceCall>()
    val getErc20BalancesCalls = mutableListOf<Int>()
    val getTransactionsCalls = mutableListOf<GetTransactionsCall>()
    val signTypedDataCalls = mutableListOf<SignTypedDataCall>()
    val sendTransactionCalls = mutableListOf<SendTransactionCall>()
    val estimateTransactionFeeCalls = mutableListOf<EstimateTransactionFeeCall>()

    override suspend fun getAddress(): String = addressToReturn

    override suspend fun sendNativeToken(
        chainId: Int,
        toAddress: String,
        amountInEth: Double
    ): String {
        sendNativeTokenCalls += SendTokenCall(
            chainId = chainId,
            contractAddress = "",
            toAddress = toAddress,
            amount = amountInEth,
            decimals = 18
        )
        return sendNativeTokenHashToReturn
    }

    override suspend fun sendToken(
        chainId: Int,
        contractAddress: String,
        toAddress: String,
        amount: Double,
        decimals: Int
    ): String {
        sendTokenCalls += SendTokenCall(chainId, contractAddress, toAddress, amount, decimals)
        return sendTokenHashToReturn
    }

    override suspend fun getNativeBalance(chainId: Int): Double {
        getNativeBalanceCalls += chainId
        return nativeBalanceToReturn
    }

    override suspend fun getERC20Balance(
        chainId: Int,
        tokenAddress: String,
        decimals: Int?
    ): Double {
        getErc20BalanceCalls += GetErc20BalanceCall(chainId, tokenAddress, decimals)
        return erc20BalanceToReturn
    }

    override suspend fun getERC20Balances(chainId: Int): Map<String, Double> {
        getErc20BalancesCalls += chainId
        return erc20BalancesToReturn
    }

    override suspend fun getTransactions(
        chainId: Int,
        limit: Int?,
        offset: Int?,
        order: RainTransactionOrder?
    ): RainTransactionResult {
        getTransactionsCalls += GetTransactionsCall(chainId, limit, offset, order)
        return transactionsToReturn
    }

    override suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String {
        signTypedDataCalls += SignTypedDataCall(chainId, walletAddress, typedDataJson)
        return signTypedDataToReturn
    }

    override suspend fun sendTransaction(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): String {
        sendTransactionCalls += SendTransactionCall(chainId, from, to, data, value)
        return sendTransactionHashToReturn
    }

    override suspend fun estimateTransactionFee(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): Double {
        estimateTransactionFeeCalls += EstimateTransactionFeeCall(chainId, from, to, data, value)
        return estimateTransactionFeeToReturn
    }
}
