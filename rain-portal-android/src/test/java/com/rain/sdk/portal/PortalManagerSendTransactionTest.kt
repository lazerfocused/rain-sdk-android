package com.rain.sdk.portal

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.error.RainError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.portalhq.android.Portal
import io.portalhq.android.exceptions.PortalException
import io.portalhq.android.mpc.data.FeatureFlags
import io.portalhq.android.provider.data.PortalProviderResult
import io.portalhq.android.provider.data.PortalProviderRpcResponse
import io.portalhq.android.provider.data.PortalRequestMethod
import io.portalhq.android.provider.data.RequestOptions
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Hash resolution inside [PortalManager.sendTransaction]: a hash the chain can answer for is
 * returned untouched, a UserOperation hash resolves through the EntryPoint's
 * `UserOperationEvent` to the transaction it was mined in, a reverted operation surfaces
 * as [RainError.TransactionSimulationFailed] rather than as a mined transaction, and an
 * unresolved one is [RainError.TransactionPending], never a hash no node can answer for.
 * Also the pre-flight `eth_call`: what it reports must say whether the send is worth retrying.
 */
class PortalManagerSendTransactionTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    private val chainId = 43114
    private val submittedHash = "0x" + "a".repeat(64)
    private val minedHash = "0x" + "b".repeat(64)

    private fun managerWith(portal: Portal): PortalManager {
        // Zero retry interval: the full scan window and every retry run instantly.
        val manager = spyk(PortalManager(retryIntervalMs = 0))
        every { manager.createPortal(any(), any(), any(), any(), any()) } returns portal
        manager.initialize(
            apiKey = "session-token",
            legacyEthChainId = chainId,
            rpcConfig = mapOf("eip155:$chainId" to "https://rpc.test"),
            featureFlags = FeatureFlags(isMultiBackupEnabled = true),
            autoApprove = true
        )
        return manager
    }

    private fun rpc(result: Any?) = PortalProviderResult(
        id = "id",
        result = PortalProviderRpcResponse(jsonrpc = "2.0", result = result)
    )

    /** UserOperationEvent data: nonce, success, actualGasCost, actualGasUsed. */
    private fun eventData(succeeded: Boolean): String {
        val zeroWord = "0".repeat(64)
        val successWord = "0".repeat(63) + if (succeeded) "1" else "0"
        return "0x" + zeroWord + successWord + zeroWord + zeroWord
    }

    private fun portalForSend(): Portal {
        val portal = mockk<Portal>(relaxed = true)
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_call, any(), null as RequestOptions?)
        } returns rpc("0x")
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_blockNumber, any(), null as RequestOptions?)
        } returns rpc("0x10")
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_sendTransaction, any(), null as RequestOptions?)
        } returns PortalProviderResult(id = "id", result = submittedHash)
        return portal
    }

    private fun send(manager: PortalManager): String = runBlocking {
        manager.sendTransaction(
            chainId = chainId,
            from = TestFixtures.WALLET_ADDRESS,
            to = TestFixtures.CONTRACT_ADDRESS,
            data = "0x095ea7b3deadbeef"
        )
    }

    @Test
    fun `a hash the chain knows is returned without scanning EntryPoint logs`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getTransactionByHash, any(), null as RequestOptions?)
        } returns rpc(mapOf("hash" to submittedHash))

        assertThat(send(managerWith(portal))).isEqualTo(submittedHash)
        coVerify(exactly = 0) {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        }
    }

    @Test
    fun `a UserOperation hash resolves to the transaction it was mined in`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getTransactionByHash, any(), null as RequestOptions?)
        } returns rpc(null)
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        } returns rpc(listOf(mapOf("transactionHash" to minedHash, "data" to eventData(succeeded = true))))

        assertThat(send(managerWith(portal))).isEqualTo(minedHash)
    }

    /**
     * Handing the raw UserOperation hash on would send the caller's receipt poll after a hash no
     * node knows: an 80-second false timeout for an approval a slow bundler still mines.
     */
    @Test
    fun `a UserOperation that does not resolve within the scan window is pending, carrying its hash`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getTransactionByHash, any(), null as RequestOptions?)
        } returns rpc(null)
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        } returns rpc(emptyList<Any>())

        val error = runCatching { send(managerWith(portal)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RainError.TransactionPending::class.java)
        assertThat((error as RainError.TransactionPending).statusId).isEqualTo(submittedHash)
        // The whole scan window was spent before giving up.
        coVerify(exactly = 20) {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        }
    }

    /** The pre-submit block read has no side effects, so one failure must not switch the scan off. */
    @Test
    fun `a failed block-number read is retried before the scan is given up`() {
        val portal = portalForSend()
        var blockNumberCalls = 0
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_blockNumber, any(), null as RequestOptions?)
        } answers {
            if (++blockNumberCalls == 1) throw IOException("connection reset") else rpc("0x10")
        }
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getTransactionByHash, any(), null as RequestOptions?)
        } returns rpc(mapOf("hash" to submittedHash))

        assertThat(send(managerWith(portal))).isEqualTo(submittedHash)
        assertThat(blockNumberCalls).isEqualTo(2)
    }

    /**
     * Retries exhausted: there is no lower bound to scan from, so the adapter hands Portal's hash
     * on as-is rather than spending the window on a scan it cannot run. Documented fallback; a
     * UserOperation hash then surfaces from the caller's own confirmation as TransactionPending.
     */
    @Test
    fun `when every block-number read fails the send returns Portal's hash without scanning`() {
        val portal = portalForSend()
        var blockNumberCalls = 0
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_blockNumber, any(), null as RequestOptions?)
        } answers {
            blockNumberCalls++
            throw IOException("connection reset")
        }

        assertThat(send(managerWith(portal))).isEqualTo(submittedHash)
        assertThat(blockNumberCalls).isEqualTo(3)
        coVerify(exactly = 0) {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        }
    }

    // ---- the pre-flight eth_call -----------------------------------------------------------

    /**
     * A network failure is retryable and nothing was broadcast; reporting it as a failed
     * simulation would tell the withdrawal flow the send reverted and must not be retried.
     */
    @Test
    fun `a network failure on the pre-flight is NetworkError, not a failed simulation`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_call, any(), null as RequestOptions?)
        } throws IOException("connection reset")

        val error = runCatching { send(managerWith(portal)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RainError.NetworkError::class.java)
        coVerify(exactly = 0) {
            portal.request(any(), PortalRequestMethod.eth_sendTransaction, any(), null as RequestOptions?)
        }
    }

    @Test
    fun `a RainError raised on the pre-flight passes through unchanged`() {
        val portal = portalForSend()
        val classified = RainError.NetworkError("already classified upstream")
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_call, any(), null as RequestOptions?)
        } throws classified

        val error = runCatching { send(managerWith(portal)) }.exceptionOrNull()

        assertThat(error).isSameInstanceAs(classified)
    }

    @Test
    fun `a revert on the pre-flight is still a failed simulation`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_call, any(), null as RequestOptions?)
        } throws PortalException.Api.RpcError(code = 3, message = "execution reverted")

        val error = runCatching { send(managerWith(portal)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RainError.TransactionSimulationFailed::class.java)
    }

    @Test
    fun `a reverted UserOperation throws rather than reporting a mined transaction`() {
        val portal = portalForSend()
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getTransactionByHash, any(), null as RequestOptions?)
        } returns rpc(null)
        coEvery {
            portal.request(any(), PortalRequestMethod.eth_getLogs, any(), null as RequestOptions?)
        } returns rpc(listOf(mapOf("transactionHash" to minedHash, "data" to eventData(succeeded = false))))

        val error = runCatching { send(managerWith(portal)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(RainError.TransactionSimulationFailed::class.java)
    }
}
