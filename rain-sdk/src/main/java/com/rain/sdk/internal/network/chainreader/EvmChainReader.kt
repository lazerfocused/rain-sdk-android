package com.rain.sdk.internal.network.chainreader

import com.rain.sdk.interfaces.RainClient
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.utils.isValidEthereumAddress
import com.rain.sdk.models.TokenSpec
import com.rain.sdk.utils.EthereumConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * EVM implementation of [ChainReader].
 *
 * Primary path — Multicall3 (`aggregate3`) for batched balance reads, so a wallet holding
 * N tokens on a chain costs one RPC round-trip regardless of N. Used when
 * [isMulticall3CanonicallyDeployed] returns true for the target chain.
 *
 * Fallback — parallel `eth_call` (`balanceOf`) and `eth_getBalance`, used on chains outside
 * the canonical deployment list.
 *
 * Native balance failures are fatal — they indicate a chain-wide problem (bad RPC, wrong
 * chain ID). Per-token failures (a single `balanceOf` reverts) are logged via Timber and
 * the token is omitted from the result, so one bad [TokenRegistry] entry doesn't break
 * balance reads for the whole chain.
 */
internal class EvmChainReader(
    private val jsonRpcClient: JsonRpcClient = JsonRpcClient(),
    private val rpcUrlResolver: (Int) -> String?
) : ChainReader {

    private companion object {
        /** Decimals used for native balances. Every chain the SDK targets today uses 18. */
        const val DEFAULT_NATIVE_DECIMALS = 18
    }

    /** Convenience constructor backed by a static `chainId → rpcUrl` map. */
    constructor(
        rpcEndpoints: Map<Int, String>,
        jsonRpcClient: JsonRpcClient = JsonRpcClient()
    ) : this(jsonRpcClient, { rpcEndpoints[it] })

    override suspend fun getNativeBalance(chainId: Int, walletAddress: String): Double {
        val rpcUrl = resolveRpcUrl(chainId)
        validateAddress(walletAddress, "wallet address")
        val hex = jsonRpcClient.callForHexResult(
            rpcUrl = rpcUrl,
            method = "eth_getBalance",
            params = listOf(walletAddress, "latest")
        )
        return EthereumConverter.convertHexToDouble(hex, DEFAULT_NATIVE_DECIMALS)
    }

    override suspend fun getERC20Balance(
        chainId: Int,
        tokenAddress: String,
        walletAddress: String,
        decimals: Int?
    ): Double {
        val rpcUrl = resolveRpcUrl(chainId)
        validateAddress(walletAddress, "wallet address")
        validateAddress(tokenAddress, "token address")
        val callData = Multicall3.encodeBalanceOf(walletAddress)
        val callParams = mapOf("to" to tokenAddress, "data" to callData)
        val hex = jsonRpcClient.callForHexResult(
            rpcUrl = rpcUrl,
            method = "eth_call",
            params = listOf(callParams, "latest")
        )
        return EthereumConverter.convertHexToDouble(
            hex,
            decimals ?: RainClient.DEFAULT_ERC20_DECIMALS
        )
    }

    override suspend fun getBalances(
        chainId: Int,
        walletAddress: String,
        tokens: List<TokenSpec>
    ): Map<String, Double> {
        val rpcUrl = resolveRpcUrl(chainId)
        validateAddress(walletAddress, "wallet address")
        return if (isMulticall3CanonicallyDeployed(chainId)) {
            fetchViaMulticall3(rpcUrl, chainId, walletAddress, tokens)
        } else {
            fetchViaParallelCalls(rpcUrl, walletAddress, tokens)
        }
    }

    // ---------- Multicall3 path ----------

    private suspend fun fetchViaMulticall3(
        rpcUrl: String,
        chainId: Int,
        walletAddress: String,
        tokens: List<TokenSpec>
    ): Map<String, Double> {
        // `allowFailure = true` so we get back per-call status. Native failure is fatal;
        // per-token failures are logged and omitted from the result (see decode loop below).
        val calls = buildList {
            add(
                Multicall3.Call3(
                    target = Multicall3.CANONICAL_ADDRESS,
                    allowFailure = true,
                    callData = Multicall3.encodeGetEthBalance(walletAddress)
                )
            )
            tokens.forEach { token ->
                add(
                    Multicall3.Call3(
                        target = token.address,
                        allowFailure = true,
                        callData = Multicall3.encodeBalanceOf(walletAddress)
                    )
                )
            }
        }

        val aggregateCallData = Multicall3.encodeAggregate3(calls)
        val callParams = mapOf("to" to Multicall3.CANONICAL_ADDRESS, "data" to aggregateCallData)
        val hex = jsonRpcClient.callForHexResult(
            rpcUrl = rpcUrl,
            method = "eth_call",
            params = listOf(callParams, "latest")
        )
        val results = Multicall3.decodeAggregate3Result(hex)

        // Expect native + one entry per token.
        val expectedCount = tokens.size + 1
        if (results.size != expectedCount) {
            throw RainError.InternalError(
                "Multicall3 returned ${results.size} results, expected $expectedCount on chain $chainId"
            )
        }

        val output = mutableMapOf<String, Double>()
        // Index 0 is the native balance.
        val nativeResult = results[0]
        if (!nativeResult.success) {
            throw RainError.InternalError(
                "Multicall3 native balance call reverted on chain $chainId"
            )
        }
        output[""] = EthereumConverter.convertHexToDouble(
            nativeResult.returnData,
            DEFAULT_NATIVE_DECIMALS
        )
        tokens.forEachIndexed { i, token ->
            val result = results[i + 1]
            if (!result.success) {
                Timber.w(
                    "Rain SDK: balanceOf reverted for token ${token.symbol} (${token.address}) on chain $chainId — omitting from result"
                )
                return@forEachIndexed
            }
            output[token.address] = EthereumConverter.convertHexToDouble(
                result.returnData,
                token.decimals
            )
        }
        return output
    }

    // ---------- Parallel fallback path ----------

    /**
     * Fans out `eth_getBalance` (native) and per-token `eth_call balanceOf` requests
     * concurrently via `async`. Native failure is fatal; per-token failures are logged and
     * the token is omitted from the result.
     */
    private suspend fun fetchViaParallelCalls(
        rpcUrl: String,
        walletAddress: String,
        tokens: List<TokenSpec>
    ): Map<String, Double> = coroutineScope {
        // Native first, on its own — its failure is fatal and shouldn't be swallowed by a
        // group that's also tolerating per-token errors.
        val nativeBalance = fetchNativeBalance(rpcUrl, walletAddress)

        // `balanceOf(walletAddress)` calldata is identical across every token — encode once.
        val balanceOfCallData = Multicall3.encodeBalanceOf(walletAddress)

        val tokenJobs: List<Pair<TokenSpec, Deferred<Double?>>> = tokens.map { token ->
            token to async {
                try {
                    fetchCall(
                        rpcUrl = rpcUrl,
                        targetAddress = token.address,
                        callData = balanceOfCallData,
                        decimals = token.decimals
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(
                        e,
                        "Rain SDK: balanceOf failed for token ${token.symbol} (${token.address}) — omitting from result"
                    )
                    null
                }
            }
        }

        val output = mutableMapOf<String, Double>("" to nativeBalance)
        tokenJobs.forEach { (token, deferred) ->
            deferred.await()?.let { output[token.address] = it }
        }
        output
    }

    private suspend fun fetchNativeBalance(rpcUrl: String, walletAddress: String): Double {
        val hex = jsonRpcClient.callForHexResult(
            rpcUrl = rpcUrl,
            method = "eth_getBalance",
            params = listOf(walletAddress, "latest")
        )
        return EthereumConverter.convertHexToDouble(hex, DEFAULT_NATIVE_DECIMALS)
    }

    /**
     * Issues a single `eth_call` against [targetAddress] with the given pre-encoded
     * [callData] and parses the hex uint256 result as a [Double] scaled by [decimals].
     * Not specific to ERC-20 — any read function whose return decodes as a uint256 fits.
     */
    private suspend fun fetchCall(
        rpcUrl: String,
        targetAddress: String,
        callData: String,
        decimals: Int
    ): Double {
        val callParams = mapOf("to" to targetAddress, "data" to callData)
        val hex = jsonRpcClient.callForHexResult(
            rpcUrl = rpcUrl,
            method = "eth_call",
            params = listOf(callParams, "latest")
        )
        return EthereumConverter.convertHexToDouble(hex, decimals)
    }

    // ---------- Helpers ----------

    /**
     * Resolves and validates the RPC URL for [chainId]. Throws [RainError.InvalidConfig] —
     * with the correct chain ID — if the chain isn't configured or its URL is unparseable,
     * so parse failures don't surface from [JsonRpcClient] without chain context.
     */
    private fun resolveRpcUrl(chainId: Int): String {
        val rpcUrl = rpcUrlResolver(chainId)
            ?: throw RainError.InvalidConfig("No RPC endpoint configured for chainId=$chainId")
        if (rpcUrl.toHttpUrlOrNull() == null) {
            throw RainError.InvalidConfig("Invalid RPC URL for chainId=$chainId: $rpcUrl")
        }
        return rpcUrl
    }

    private fun validateAddress(address: String, label: String) {
        if (!address.isValidEthereumAddress) {
            throw RainError.InternalError("Invalid Ethereum $label: $address")
        }
    }
}
