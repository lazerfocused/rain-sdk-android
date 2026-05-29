package com.rain.sdk.internal.helpers

import com.rain.sdk.internal.network.chainreader.ChainReader
import com.rain.sdk.models.TokenSpec

/**
 * In-memory [ChainReader] stub for routing tests in `TurnkeyWalletProviderTest` and
 * `RainSdkManager*Test`. Records every call so tests can assert that the adapter routed
 * the request through here rather than through Turnkey's balance indexer.
 */
internal class MockChainReader(
    var nativeBalance: Double = 0.0,
    var erc20Balance: Double = 0.0,
    var balances: Map<String, Double> = emptyMap()
) : ChainReader {

    data class NativeCall(val chainId: Int, val walletAddress: String)
    data class Erc20Call(
        val chainId: Int,
        val tokenAddress: String,
        val walletAddress: String,
        val decimals: Int?
    )
    data class BalancesCall(
        val chainId: Int,
        val walletAddress: String,
        val tokens: List<TokenSpec>
    )

    val nativeCalls = mutableListOf<NativeCall>()
    val erc20Calls = mutableListOf<Erc20Call>()
    val balancesCalls = mutableListOf<BalancesCall>()

    override suspend fun getNativeBalance(chainId: Int, walletAddress: String): Double {
        nativeCalls += NativeCall(chainId, walletAddress)
        return nativeBalance
    }

    override suspend fun getERC20Balance(
        chainId: Int,
        tokenAddress: String,
        walletAddress: String,
        decimals: Int?
    ): Double {
        erc20Calls += Erc20Call(chainId, tokenAddress, walletAddress, decimals)
        return erc20Balance
    }

    override suspend fun getBalances(
        chainId: Int,
        walletAddress: String,
        tokens: List<TokenSpec>
    ): Map<String, Double> {
        balancesCalls += BalancesCall(chainId, walletAddress, tokens)
        return balances
    }
}
