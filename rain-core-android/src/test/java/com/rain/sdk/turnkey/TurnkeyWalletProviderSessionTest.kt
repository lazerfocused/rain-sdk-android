package com.rain.sdk.turnkey

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.helpers.MockChainReader
import com.rain.sdk.internal.helpers.assumeJdk24
import com.rain.sdk.models.Token
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Session-hardening behavior through the wallet provider: expiry checks, refresh-on-401,
 * and typed session death on every Turnkey-backed path.
 */
class TurnkeyWalletProviderSessionTest {

    @Before
    fun requireJdk24() = assumeJdk24()

    private fun makeProvider(
        turnkey: MockTurnkey,
        policy: TurnkeySessionPolicy = TurnkeySessionPolicy(),
        onSessionExpired: (() -> Unit)? = null,
        history: TurnkeyHistoryProtocol = ThrowingTurnkeyHistory,
    ): TurnkeyWalletProvider = TurnkeyWalletProvider(
        turnkey = turnkey,
        rpcEndpoints = mapOf(1 to "https://eth.example/rpc"),
        httpClient = OkHttpClient(),
        chainReader = MockChainReader(),
        history = history,
        sessionCoordinator = TurnkeySessionCoordinator(
            turnkey = turnkey,
            policy = policy,
            onSessionExpired = onSessionExpired,
            retryDelay = { /* no real sleeping in tests */ },
        )
    )

    @Test
    fun `sendTransaction with an expired session and failing refresh throws TokenExpired`() {
        val turnkey = MockTurnkey(session = MockTurnkey.expiredSession())
        turnkey.refreshSessionError = RuntimeException("refresh rejected")
        var hookCalls = 0
        val provider = makeProvider(turnkey, onSessionExpired = { hookCalls++ })

        assertThrows(RainError.TokenExpired::class.java) {
            runBlocking {
                provider.sendTransaction(1, "0xfrom", "0xto", "0x", "0x0")
            }
        }
        val client = turnkey.turnkeyClient as MockTurnkeyClient
        assertThat(client.ethSendTransactionCalls).isEmpty()
        assertThat(hookCalls).isEqualTo(1)
    }

    @Test
    fun `getBalance refreshes on 401 and retries the balances read`() = runBlocking {
        val turnkey = MockTurnkey()
        val client = turnkey.turnkeyClient as MockTurnkeyClient
        client.walletAddressBalancesError = RuntimeException("HTTP error from /balances: 401")
        turnkey.onRefreshSession = {
            turnkey.session = MockTurnkey.defaultSession()
            client.walletAddressBalancesError = null
        }
        val provider = makeProvider(turnkey)

        val balance = provider.getBalance(chainId = 1, token = Token.Native)

        assertThat(balance.chainId).isEqualTo(1)
        assertThat(turnkey.refreshSessionCallCount).isEqualTo(1)
        assertThat(client.walletAddressBalanceCalls).hasSize(2)
    }

    @Test
    fun `getTransactions surfaces TokenExpired from indexed history without the activity fallback`() {
        val turnkey = MockTurnkey()
        turnkey.refreshSessionError = RuntimeException("refresh rejected")
        val history = object : TurnkeyHistoryProtocol {
            override suspend fun listEthTransactionHistory(
                organizationId: String,
                sessionPublicKey: String,
                address: String,
                caip2: String,
                limit: Int
            ): TurnkeyEthHistoryResponse = throw TurnkeyHistoryError(401, "unauthorized")

            override suspend fun listSolTransactionHistory(
                organizationId: String,
                sessionPublicKey: String,
                address: String,
                caip2: String,
                limit: Int
            ): TurnkeySolHistoryResponse = throw TurnkeyHistoryError(401, "unauthorized")
        }
        val provider = makeProvider(turnkey, history = history)

        assertThrows(RainError.TokenExpired::class.java) {
            runBlocking { provider.getTransactions(chainId = 1, limit = 10, offset = 0, order = null) }
        }
        val client = turnkey.turnkeyClient as MockTurnkeyClient
        assertThat(client.getActivitiesCalls).isEmpty()
    }

    @Test
    fun `getTransactions falls back to activities when indexed history is feature-gated`() =
        runBlocking {
            val turnkey = MockTurnkey()
            val provider = makeProvider(turnkey, history = ThrowingTurnkeyHistory)

            val transactions =
                provider.getTransactions(chainId = 1, limit = 10, offset = 0, order = null)

            assertThat(transactions).isEmpty()
            val client = turnkey.turnkeyClient as MockTurnkeyClient
            assertThat(client.getActivitiesCalls).hasSize(1)
        }

    @Test
    fun `getWalletAddress with expired session and no cached wallets throws TokenExpired`() {
        val turnkey = MockTurnkey(wallets = emptyList(), session = MockTurnkey.expiredSession())
        turnkey.refreshSessionError = RuntimeException("refresh rejected")
        val provider = makeProvider(turnkey)

        assertThrows(RainError.TokenExpired::class.java) {
            runBlocking { provider.getWalletAddress() }
        }
        assertThat(turnkey.refreshWalletsCallCount).isEqualTo(0)
    }

    @Test
    fun `expired session on sends is refreshed transparently when refresh succeeds`() = runBlocking {
        val turnkey = MockTurnkey(session = MockTurnkey.expiredSession())
        turnkey.onRefreshSession = { turnkey.session = MockTurnkey.defaultSession() }
        val provider = makeProvider(turnkey)

        // signTypedData exercises the write path without needing RPC stubs.
        val signature = provider.signTypedData(1, "0xabc", "{}")

        assertThat(signature).startsWith("0x")
        assertThat(turnkey.refreshSessionCallCount).isEqualTo(1)
        assertThat(turnkey.signRawPayloadCalls).hasSize(1)
    }
}
