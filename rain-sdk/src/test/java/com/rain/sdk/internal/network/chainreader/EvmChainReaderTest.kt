package com.rain.sdk.internal.network.chainreader

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.helpers.MockRpcServer
import com.rain.sdk.models.TokenSpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Covers the two read paths the reader exposes:
 *  - Multicall3 batched aggregate3 (canonically-deployed chain — Ethereum mainnet, id = 1)
 *  - Parallel `eth_call` fallback (chain outside the deployment list — Avalanche Fuji 43113)
 */
class EvmChainReaderTest {

    private lateinit var rpc: MockRpcServer

    private val wallet = "0x1111111111111111111111111111111111111111"
    private val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val dai = "0x6b175474e89094c44da98b954eedeac495271d0f"

    @Before
    fun setUp() {
        rpc = MockRpcServer().also { it.start() }
    }

    @After
    fun tearDown() {
        rpc.shutdown()
    }

    private fun makeReader(chainId: Int): EvmChainReader =
        EvmChainReader(rpcEndpoints = mapOf(chainId to rpc.urlFor(chainId)))

    // ---------- single-token paths ----------

    @Test
    fun `getNativeBalance parses eth_getBalance hex into ether units`(): Unit = runBlocking {
        // 0xDE0B6B3A7640000 = 1e18 wei = 1 ETH
        rpc.stub("eth_getBalance", "0xde0b6b3a7640000")
        val reader = makeReader(chainId = 1)

        val balance = reader.getNativeBalance(1, wallet)

        assertThat(balance).isWithin(1e-9).of(1.0)
        assertThat(rpc.recordedMethods).containsExactly("eth_getBalance")
    }

    @Test
    fun `getERC20Balance issues eth_call and scales by token decimals`() = runBlocking {
        // 0xf4240 = 1_000_000 (1.00 USDC at 6 decimals), padded to a 32-byte uint256.
        rpc.stub("eth_call", "0x" + "0".repeat(59) + "f4240")
        val reader = makeReader(chainId = 1)

        val balance = reader.getERC20Balance(1, usdc, wallet, decimals = 6)

        assertThat(balance).isWithin(1e-9).of(1.0)
    }

    // ---------- parallel-fallback path ----------

    @Test
    fun `getBalances on a non-Multicall3 chain fans out one balanceOf per token`() = runBlocking {
        // Use a chain not in CANONICALLY_DEPLOYED_CHAIN_IDS so the parallel fallback runs.
        val chainId = 43113 // Avalanche Fuji testnet
        rpc.stub("eth_getBalance", "0x0") // native = 0
        // 0xf4240 = 1_000_000 → 1.0 at 6 decimals. Both tokens share the same stubbed
        // eth_call response (MockRpcServer dispatches by method, not by request body).
        rpc.stub("eth_call", "0x" + "0".repeat(59) + "f4240")
        val reader = EvmChainReader(rpcEndpoints = mapOf(chainId to rpc.urlFor(chainId)))

        val balances = reader.getBalances(
            chainId = chainId,
            walletAddress = wallet,
            tokens = listOf(
                TokenSpec(chainId, usdc, "USDC", 6),
                TokenSpec(chainId, dai, "DAI", 6)
            )
        )

        assertThat(balances[""]).isEqualTo(0.0)
        assertThat(balances[usdc]).isWithin(1e-9).of(1.0)
        assertThat(balances[dai]).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `getBalances surfaces native failures but omits per-token failures`() = runBlocking {
        // Native call works; the eth_call shared by both tokens returns an error.
        val chainId = 43113
        rpc.stub("eth_getBalance", "0xde0b6b3a7640000") // 1 ETH
        rpc.stubNetworkFailure("eth_call")
        val reader = EvmChainReader(rpcEndpoints = mapOf(chainId to rpc.urlFor(chainId)))

        val balances = reader.getBalances(
            chainId = chainId,
            walletAddress = wallet,
            tokens = listOf(TokenSpec(chainId, usdc, "USDC", 6))
        )

        assertThat(balances).containsKey("")
        assertThat(balances).doesNotContainKey(usdc)
    }

    @Test
    fun `getBalances treats native eth_getBalance failure as fatal`() {
        val chainId = 43113
        rpc.stubNetworkFailure("eth_getBalance")
        val reader = EvmChainReader(rpcEndpoints = mapOf(chainId to rpc.urlFor(chainId)))

        val ex = runCatching {
            runBlocking {
                reader.getBalances(
                    chainId = chainId,
                    walletAddress = wallet,
                    tokens = listOf(TokenSpec(chainId, usdc, "USDC", 6))
                )
            }
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(RainError.NetworkError::class.java)
    }

    // ---------- guards ----------

    @Test
    fun `getNativeBalance throws InvalidConfig when chain has no rpc configured`() {
        val reader = EvmChainReader(rpcEndpoints = emptyMap())
        assertThrows(RainError.InvalidConfig::class.java) {
            runBlocking { reader.getNativeBalance(1, wallet) }
        }
    }

    @Test
    fun `getNativeBalance throws InternalError on syntactically invalid wallet address`() {
        val reader = makeReader(chainId = 1)
        assertThrows(RainError.InternalError::class.java) {
            runBlocking { reader.getNativeBalance(1, walletAddress = "not-an-address") }
        }
    }
}
