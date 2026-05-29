package com.rain.sdk.internal.provider

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.helpers.MockChainReader
import com.rain.sdk.internal.helpers.assumeJdk24
import com.turnkey.types.V1AssetBalance
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test

/**
 * Verifies the chain-routing decision in [TurnkeyWalletProvider]:
 *  - Chains in [com.rain.sdk.internal.constants.RainConstants.TURNKEY_SUPPORTED_CHAINS] go
 *    through Turnkey's `get_wallet_address_balances`.
 *  - Anything else falls through to the injected [com.rain.sdk.internal.network.chainreader.ChainReader].
 *  - `getERC20Balance` is delegated to the ChainReader unconditionally — same RPC call
 *    everywhere, no reason to maintain two implementations.
 */
class TurnkeyWalletProviderRoutingTest {

    @Before
    fun requireJdk24() = assumeJdk24()

    private fun makeProvider(
        chainReader: MockChainReader,
        chainId: Int = 1
    ): TurnkeyWalletProvider {
        val turnkey = MockTurnkey()
        // The MockTurnkeyClient on `turnkey` returns no balances by default — fine for chain
        // ID 1 since we override behavior via the chainReader only when off-allowlist.
        return TurnkeyWalletProvider(
            turnkey = turnkey,
            rpcEndpoints = mapOf(chainId to "https://eth.example/rpc"),
            walletAddressOverride = MockTurnkey.DEFAULT_WALLET_ADDRESS,
            httpClient = OkHttpClient(),
            chainReader = chainReader
        )
    }

    // ---------- supported-chain path ----------

    @Test
    fun `getNativeBalance on Ethereum 1 routes through Turnkey balances, not ChainReader`() = runBlocking {
        val chainReader = MockChainReader(nativeBalance = 9.9)
        val turnkey = MockTurnkey()
        turnkey.turnkeyClient = MockTurnkeyClient(
            mockBalances = listOf(
                V1AssetBalance(
                    balance = "500000000000000000", // 0.5 ETH
                    caip19 = "eip155:1/slip44:60",
                    decimals = 18L,
                    display = null,
                    name = "Ethereum",
                    symbol = "ETH"
                )
            )
        )
        val provider = TurnkeyWalletProvider(
            turnkey = turnkey,
            rpcEndpoints = mapOf(1 to "https://eth.example/rpc"),
            walletAddressOverride = MockTurnkey.DEFAULT_WALLET_ADDRESS,
            httpClient = OkHttpClient(),
            chainReader = chainReader
        )

        val balance = provider.getNativeBalance(chainId = 1)

        assertThat(balance).isWithin(1e-9).of(0.5)
        // Turnkey-supported chain shouldn't have touched the ChainReader at all.
        assertThat(chainReader.nativeCalls).isEmpty()
    }

    // ---------- unsupported-chain fallback ----------

    @Test
    fun `getNativeBalance on Avalanche Fuji 43113 routes through ChainReader`() = runBlocking {
        val chainReader = MockChainReader(nativeBalance = 2.5)
        val provider = makeProvider(chainReader, chainId = 43113)

        val balance = provider.getNativeBalance(chainId = 43113)

        assertThat(balance).isWithin(1e-9).of(2.5)
        assertThat(chainReader.nativeCalls).hasSize(1)
        assertThat(chainReader.nativeCalls.single().chainId).isEqualTo(43113)
    }

    @Test
    fun `getERC20Balances on unsupported chain delegates to ChainReader and strips native key`() = runBlocking {
        val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        val chainReader = MockChainReader(
            balances = mapOf(
                "" to 1.0,        // native — must be stripped
                usdc to 100.0
            )
        )
        val provider = makeProvider(chainReader, chainId = 43113)

        val balances = provider.getERC20Balances(chainId = 43113)

        assertThat(balances).containsExactly(usdc, 100.0)
        assertThat(chainReader.balancesCalls).hasSize(1)
    }

    // ---------- unconditional delegation ----------

    @Test
    fun `getERC20Balance always delegates to ChainReader, even on Turnkey-supported chains`() = runBlocking {
        val chainReader = MockChainReader(erc20Balance = 42.0)
        val provider = makeProvider(chainReader, chainId = 1)

        val balance = provider.getERC20Balance(
            chainId = 1,
            tokenAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            decimals = 6
        )

        assertThat(balance).isEqualTo(42.0)
        assertThat(chainReader.erc20Calls).hasSize(1)
        val call = chainReader.erc20Calls.single()
        assertThat(call.chainId).isEqualTo(1)
        assertThat(call.decimals).isEqualTo(6)
    }

    // ---------- address cache ----------

    @Test
    fun `getAddress is cached after first resolve and skips wallet refresh on subsequent calls`() = runBlocking {
        val turnkey = MockTurnkey()
        var refreshCount = 0
        val cached = object : TurnkeyContextProtocol by turnkey {
            override suspend fun refreshWallets() {
                refreshCount++
                turnkey.wallets = listOf(MockTurnkey.defaultWallet())
            }
        }
        val provider = TurnkeyWalletProvider(
            turnkey = cached,
            rpcEndpoints = mapOf(1 to "https://eth.example/rpc"),
            httpClient = OkHttpClient(),
            chainReader = MockChainReader()
        )

        val first = provider.getAddress()
        val second = provider.getAddress()
        val third = provider.getAddress()

        assertThat(first).isEqualTo(second)
        assertThat(second).isEqualTo(third)
        // wallets already contained a usable address, so refresh shouldn't have run at all.
        assertThat(refreshCount).isEqualTo(0)
    }
}
