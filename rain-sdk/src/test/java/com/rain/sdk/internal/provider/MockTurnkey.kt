package com.rain.sdk.internal.provider

import com.turnkey.core.TurnkeyContext
import com.turnkey.core.models.Session
import com.turnkey.core.models.Wallet
import com.turnkey.types.Externaldatav1Timestamp
import com.turnkey.types.TEthSendTransactionBody
import com.turnkey.types.TEthSendTransactionResponse
import com.turnkey.types.TGetActivitiesBody
import com.turnkey.types.TGetActivitiesResponse
import com.turnkey.types.TGetSendTransactionStatusBody
import com.turnkey.types.TGetSendTransactionStatusResponse
import com.turnkey.types.TGetWalletAddressBalancesBody
import com.turnkey.types.TGetWalletAddressBalancesResponse
import com.turnkey.types.V1Activity
import com.turnkey.types.V1ActivityStatus
import com.turnkey.types.V1ActivityType
import com.turnkey.types.V1AddressFormat
import com.turnkey.types.V1AssetBalance
import com.turnkey.types.V1Curve
import com.turnkey.types.V1EthSendTransactionIntent
import com.turnkey.types.V1EthSendTransactionResult
import com.turnkey.types.V1EthSendTransactionStatus
import com.turnkey.types.V1HashFunction
import com.turnkey.types.V1Intent
import com.turnkey.types.V1PathFormat
import com.turnkey.types.V1PayloadEncoding
import com.turnkey.types.V1Result
import com.turnkey.types.V1SignRawPayloadResult
import com.turnkey.types.V1WalletAccount
import java.util.UUID

internal class MockTurnkeyClient(
    var mockBalances: List<V1AssetBalance> = emptyList(),
    var mockSendTransactionStatusId: String = "send-status-id",
    var mockTransactionHash: String = "0x" + "d".repeat(64),
    var mockActivities: List<V1Activity> = emptyList()
) : TurnkeyClientProtocol {

    val walletAddressBalanceCalls = mutableListOf<TGetWalletAddressBalancesBody>()
    val ethSendTransactionCalls = mutableListOf<TEthSendTransactionBody>()
    val sendTransactionStatusCalls = mutableListOf<TGetSendTransactionStatusBody>()
    val getActivitiesCalls = mutableListOf<TGetActivitiesBody>()

    override suspend fun getWalletAddressBalances(
        input: TGetWalletAddressBalancesBody
    ): TGetWalletAddressBalancesResponse {
        walletAddressBalanceCalls += input
        return TGetWalletAddressBalancesResponse(balances = mockBalances)
    }

    override suspend fun ethSendTransaction(
        input: TEthSendTransactionBody
    ): TEthSendTransactionResponse {
        ethSendTransactionCalls += input
        return TEthSendTransactionResponse(
            activity = MockTurnkey.makeActivity(
                id = UUID.randomUUID().toString(),
                from = input.from,
                to = input.to,
                caip2 = input.caip2,
                value = input.value,
                data = input.data,
                sendTransactionStatusId = mockSendTransactionStatusId
            ),
            result = V1EthSendTransactionResult(sendTransactionStatusId = mockSendTransactionStatusId)
        )
    }

    override suspend fun getSendTransactionStatus(
        input: TGetSendTransactionStatusBody
    ): TGetSendTransactionStatusResponse {
        sendTransactionStatusCalls += input
        return TGetSendTransactionStatusResponse(
            error = null,
            eth = V1EthSendTransactionStatus(txHash = mockTransactionHash),
            txError = null,
            txStatus = "TX_STATUS_BROADCASTED"
        )
    }

    override suspend fun getActivities(
        input: TGetActivitiesBody
    ): TGetActivitiesResponse {
        getActivitiesCalls += input
        return TGetActivitiesResponse(activities = mockActivities)
    }
}

/**
 * Returns a `RainSdkManager.turnkeyContextFactory` lambda that resolves to the supplied [mock]
 * regardless of which real `TurnkeyContext` singleton is passed in. Lives here (not in the test
 * file) so the Kotlin compiler emits the lambda's synthetic body on this class — keeping
 * `TurnkeyContext` out of the test class's method signatures so JDK 21's reflection scan can
 * succeed and `Assume.assumeTrue(jdk24+)` can skip cleanly.
 */
internal fun mockTurnkeyFactory(mock: TurnkeyContextProtocol): (TurnkeyContext) -> TurnkeyContextProtocol =
    { _: TurnkeyContext -> mock }

internal class MockTurnkey(
    override var wallets: List<Wallet> = listOf(defaultWallet()),
    override var session: Session? = defaultSession(),
    override var turnkeyClient: TurnkeyClientProtocol? = MockTurnkeyClient(),
    var mockSignature: V1SignRawPayloadResult = V1SignRawPayloadResult(
        r = "1".repeat(64),
        s = "2".repeat(64),
        v = "28"
    )
) : TurnkeyContextProtocol {

    data class SignRawPayloadCall(
        val signWith: String,
        val payload: String,
        val encoding: V1PayloadEncoding,
        val hashFunction: V1HashFunction
    )

    var refreshWalletsCallCount: Int = 0
    val signRawPayloadCalls = mutableListOf<SignRawPayloadCall>()

    override suspend fun refreshWallets() {
        refreshWalletsCallCount++
    }

    override suspend fun signRawPayload(
        signWith: String,
        payload: String,
        encoding: V1PayloadEncoding,
        hashFunction: V1HashFunction
    ): V1SignRawPayloadResult {
        signRawPayloadCalls += SignRawPayloadCall(signWith, payload, encoding, hashFunction)
        return mockSignature
    }

    companion object {
        const val DEFAULT_WALLET_ADDRESS = "0x1234567890123456789012345678901234567890"
        const val DEFAULT_ORG_ID = "org-id"

        fun defaultSession(): Session = Session(
            userId = "user-id",
            organizationId = DEFAULT_ORG_ID,
            expiry = System.currentTimeMillis() / 1000.0 + 3600,
            expirationSeconds = "3600",
            publicKey = "pubkey",
            token = "jwt",
            sessionType = "read_write"
        )

        fun defaultWallet(): Wallet = Wallet(
            id = "wallet-id",
            name = "wallet",
            accounts = listOf(
                V1WalletAccount(
                    address = DEFAULT_WALLET_ADDRESS,
                    addressFormat = V1AddressFormat.ADDRESS_FORMAT_ETHEREUM,
                    createdAt = Externaldatav1Timestamp(nanos = "0", seconds = "0"),
                    curve = V1Curve.CURVE_SECP256K1,
                    organizationId = DEFAULT_ORG_ID,
                    path = "m/44'/60'/0'/0/0",
                    pathFormat = V1PathFormat.PATH_FORMAT_BIP32,
                    publicKey = null,
                    updatedAt = Externaldatav1Timestamp(nanos = "0", seconds = "0"),
                    walletAccountId = "wallet-account-id",
                    walletDetails = null,
                    walletId = "wallet-id"
                )
            )
        )

        fun makeActivity(
            id: String,
            from: String,
            to: String,
            caip2: String,
            value: String?,
            data: String?,
            sendTransactionStatusId: String
        ): V1Activity = V1Activity(
            canApprove = false,
            canReject = false,
            createdAt = Externaldatav1Timestamp(nanos = "0", seconds = "1714521600"),
            fingerprint = "fingerprint",
            id = id,
            intent = V1Intent(
                ethSendTransactionIntent = V1EthSendTransactionIntent(
                    caip2 = caip2,
                    data = data,
                    from = from,
                    gasLimit = "21000",
                    gasStationNonce = null,
                    maxFeePerGas = "1000000000",
                    maxPriorityFeePerGas = "1000000000",
                    nonce = "1",
                    sponsor = false,
                    to = to,
                    value = value
                )
            ),
            organizationId = DEFAULT_ORG_ID,
            result = V1Result(
                ethSendTransactionResult = V1EthSendTransactionResult(
                    sendTransactionStatusId = sendTransactionStatusId
                )
            ),
            status = V1ActivityStatus.ACTIVITY_STATUS_COMPLETED,
            type = V1ActivityType.ACTIVITY_TYPE_ETH_SEND_TRANSACTION,
            updatedAt = Externaldatav1Timestamp(nanos = "0", seconds = "1714521600"),
            votes = emptyList()
        )
    }
}
