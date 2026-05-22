package com.rain.sdk.internal.provider

import com.rain.sdk.interfaces.RainClient
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.models.RainTransaction
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.utils.EthereumConverter
import com.turnkey.types.TEthSendTransactionBody
import com.turnkey.types.TGetActivitiesBody
import com.turnkey.types.TGetSendTransactionStatusBody
import com.turnkey.types.TGetWalletAddressBalancesBody
import com.turnkey.types.V1ActivityType
import com.turnkey.types.V1AddressFormat
import com.turnkey.types.V1AssetBalance
import com.turnkey.types.V1HashFunction
import com.turnkey.types.V1Pagination
import com.turnkey.types.V1PayloadEncoding
import com.turnkey.types.V1SignRawPayloadResult
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function as Web3jFunction
import org.web3j.abi.datatypes.generated.Uint256
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turnkey-based implementation of [WalletProvider].
 * Used when the SDK is initialized with `initializeTurnkey(...)`.
 */
internal class TurnkeyWalletProvider(
    private val turnkey: TurnkeyContextProtocol,
    private val rpcEndpoints: Map<Int, String>,
    private val walletAddressOverride: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val pollingIntervalMs: Long = POLLING_INTERVAL_MS
) : WalletProvider {

    private companion object {
        const val DEFAULT_NATIVE_DECIMALS = 18
        const val DEFAULT_POLLING_ATTEMPTS = 30
        const val POLLING_INTERVAL_MS = 1_000L
        const val FALLBACK_GAS_LIMIT = 21_000L
        const val JSON_MEDIA_TYPE = "application/json"
    }

    private data class ActivityDraft(
        val id: String,
        val timestampSeconds: Double,
        val from: String,
        val to: String,
        val value: String?,
        val data: String?,
        val chainId: Int,
        val sendTransactionStatusId: String?
    )

    // ---------- address ----------

    override suspend fun getAddress(): String {
        walletAddressOverride?.takeIf { it.isNotEmpty() }?.let { return it }

        resolveEthereumWalletAddress(turnkey.wallets)?.let { return it }

        turnkey.refreshWallets()

        return resolveEthereumWalletAddress(turnkey.wallets)
            ?: throw RainError.WalletUnavailable("No Ethereum wallet available from Turnkey context")
    }

    private fun resolveEthereumWalletAddress(wallets: List<com.turnkey.core.models.Wallet>): String? {
        return wallets
            .flatMap { it.accounts }
            .firstOrNull { it.addressFormat == V1AddressFormat.ADDRESS_FORMAT_ETHEREUM }
            ?.address
    }

    // ---------- high-level send ----------

    override suspend fun sendNativeToken(
        chainId: Int,
        toAddress: String,
        amountInEth: Double
    ): String {
        val from = getAddress()
        val valueHex = EthereumConverter.convertEthToWeiHex(amountInEth)
        return sendTransaction(
            chainId = chainId,
            from = from,
            to = toAddress,
            data = "0x",
            value = valueHex
        )
    }

    override suspend fun sendToken(
        chainId: Int,
        contractAddress: String,
        toAddress: String,
        amount: Double,
        decimals: Int
    ): String {
        val from = getAddress()
        val tokenAmount = amount.toBigDecimal()
            .multiply(BigDecimal.TEN.pow(decimals))
            .toBigInteger()
        val function = Web3jFunction(
            "transfer",
            listOf(Address(toAddress), Uint256(tokenAmount)),
            emptyList<TypeReference<*>>()
        )
        val data = FunctionEncoder.encode(function)
        return sendTransaction(
            chainId = chainId,
            from = from,
            to = contractAddress,
            data = data,
            value = "0x0"
        )
    }

    // ---------- low-level send / sign / fee ----------

    override suspend fun sendTransaction(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): String {
        val (session, client) = resolveSessionAndClient()
        val sendBody = buildSendTransactionBody(
            session = session,
            chainId = chainId,
            from = from,
            to = to,
            data = data,
            value = value
        )
        val response = client.ethSendTransaction(sendBody)
        val statusId = response.result.sendTransactionStatusId
        return pollForTransactionHash(client, session.organizationId, statusId)
    }

    override suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String {
        val signature = turnkey.signRawPayload(
            signWith = walletAddress,
            payload = typedDataJson,
            encoding = V1PayloadEncoding.PAYLOAD_ENCODING_EIP712,
            hashFunction = V1HashFunction.HASH_FUNCTION_NO_OP
        )
        return ethereumSignatureHex(signature)
    }

    override suspend fun estimateTransactionFee(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): Double {
        val estimateHex = extractHexResult(
            rpcRequest(
                chainId = chainId,
                method = "eth_estimateGas",
                params = listOf(rpcTransactionObject(from, to, data, value))
            ),
            "eth_estimateGas"
        )
        val gasPriceHex = extractHexResult(
            rpcRequest(
                chainId = chainId,
                method = "eth_gasPrice",
                params = emptyList<Any>()
            ),
            "eth_gasPrice"
        )

        val gasLimit = BigInteger(estimateHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val gasPrice = BigInteger(gasPriceHex.removePrefix("0x").ifEmpty { "0" }, 16)
        return EthereumConverter.convertWeiToEth(gasLimit.multiply(gasPrice))
    }

    // ---------- balances ----------

    override suspend fun getNativeBalance(chainId: Int): Double {
        val walletAddress = getAddress()
        return try {
            val balances = fetchBalances(chainId, walletAddress)
            val caip2 = caip2For(chainId)
            val native = balances.firstOrNull { isNativeAsset(it, caip2) }
            decimalStringToDouble(
                balance = native?.balance,
                decimals = native?.decimals?.toInt() ?: DEFAULT_NATIVE_DECIMALS
            )
        } catch (e: Exception) {
            // Turnkey's balance indexer (`/public/v1/query/get_wallet_address_balances`) only
            // covers a curated set of chains and 403s for the rest (e.g. Avalanche Fuji 43113).
            // Native balance is one RPC call away, so fall back to eth_getBalance over the
            // configured RPC endpoint — works on any EVM chain we have a URL for.
            Timber.w(e, "Rain SDK: Turnkey balance indexer failed for chainId=$chainId, using eth_getBalance fallback")
            val hex = extractHexResult(
                rpcRequest(chainId, "eth_getBalance", listOf(walletAddress, "latest")),
                "eth_getBalance"
            )
            EthereumConverter.convertHexToDouble(hex, DEFAULT_NATIVE_DECIMALS)
        }
    }

    override suspend fun getERC20Balance(
        chainId: Int,
        tokenAddress: String,
        decimals: Int?
    ): Double {
        val walletAddress = getAddress()
        val function = Web3jFunction(
            "balanceOf",
            listOf(Address(walletAddress)),
            listOf(object : TypeReference<Uint256>() {})
        )
        val callData = FunctionEncoder.encode(function)
        val callParams = mapOf("to" to tokenAddress, "data" to callData)
        val response = rpcRequest(
            chainId = chainId,
            method = "eth_call",
            params = listOf(callParams, "latest")
        )
        val hex = extractHexResult(response, "eth_call")
        return EthereumConverter.convertHexToDouble(hex, decimals ?: RainClient.DEFAULT_ERC20_DECIMALS)
    }

    override suspend fun getERC20Balances(chainId: Int): Map<String, Double> {
        val walletAddress = getAddress()
        val balances = fetchBalances(chainId, walletAddress)
        val caip2 = caip2For(chainId)

        return balances.mapNotNull { balance ->
            val caip19 = balance.caip19 ?: return@mapNotNull null
            val tokenAddress = tokenAddressFromCaip19(caip19, caip2) ?: return@mapNotNull null
            tokenAddress to decimalStringToDouble(
                balance = balance.balance,
                decimals = balance.decimals?.toInt() ?: RainClient.DEFAULT_ERC20_DECIMALS
            )
        }.toMap()
    }

    // ---------- transactions ----------

    override suspend fun getTransactions(
        chainId: Int,
        limit: Int?,
        offset: Int?,
        order: RainTransactionOrder?
    ): RainTransactionResult {
        val (session, client) = resolveSessionAndClient()
        val requestedLimit = minOf(maxOf(((limit ?: 10) + (offset ?: 0)), 1), 100)
        val activities = client.getActivities(
            TGetActivitiesBody(
                organizationId = session.organizationId,
                filterByType = listOf(V1ActivityType.ACTIVITY_TYPE_ETH_SEND_TRANSACTION),
                paginationOptions = V1Pagination(limit = requestedLimit.toString())
            )
        )

        val drafts = activities.activities.mapNotNull { activity ->
            val intent = activity.intent.ethSendTransactionIntent ?: return@mapNotNull null
            val txChainId = chainIdFromCaip2(intent.caip2)
            if (txChainId != chainId) return@mapNotNull null

            val seconds = activity.createdAt.seconds.toDoubleOrNull() ?: 0.0
            val nanos = activity.createdAt.nanos.toDoubleOrNull() ?: 0.0
            ActivityDraft(
                id = activity.id,
                timestampSeconds = seconds + nanos / 1_000_000_000.0,
                from = intent.from,
                to = intent.to,
                value = intent.value,
                data = intent.data,
                chainId = txChainId,
                sendTransactionStatusId = activity.result.ethSendTransactionResult?.sendTransactionStatusId
            )
        }

        val sorted = when (order ?: RainTransactionOrder.DESC) {
            RainTransactionOrder.ASC -> drafts.sortedBy { it.timestampSeconds }
            RainTransactionOrder.DESC -> drafts.sortedByDescending { it.timestampSeconds }
        }

        val sliced = sorted
            .drop(offset ?: 0)
            .let { if (limit != null) it.take(limit) else it }

        val transactions = sliced.map { draft ->
            val txHash = runCatching {
                resolveTransactionHash(client, session.organizationId, draft.sendTransactionStatusId)
            }.getOrNull()

            RainTransaction(
                hash = txHash ?: draft.id,
                blockNumber = null,
                blockTimestamp = iso8601(draft.timestampSeconds),
                from = draft.from,
                to = draft.to,
                value = decimalStringToDouble(draft.value, DEFAULT_NATIVE_DECIMALS).toString(),
                gas = null,
                gasPrice = null,
                chainId = draft.chainId.toString(),
                symbol = null,
                tokenAddress = draft.to.takeIf { !draft.data.isNullOrEmpty() && draft.data != "0x" },
                metadata = null
            )
        }
        return RainTransactionResult(transactions = transactions)
    }

    // ---------- helpers ----------

    private fun resolveSessionAndClient(): Pair<com.turnkey.core.models.Session, TurnkeyClientProtocol> {
        val session = turnkey.session
            ?: throw RainError.TokenExpired()
        val client = turnkey.turnkeyClient
            ?: throw RainError.TokenExpired()
        return session to client
    }

    private suspend fun fetchBalances(chainId: Int, walletAddress: String): List<V1AssetBalance> {
        val (session, client) = resolveSessionAndClient()
        val response = client.getWalletAddressBalances(
            TGetWalletAddressBalancesBody(
                organizationId = session.organizationId,
                address = walletAddress,
                caip2 = caip2For(chainId)
            )
        )
        return response.balances.orEmpty()
    }

    private suspend fun buildSendTransactionBody(
        session: com.turnkey.core.models.Session,
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): TEthSendTransactionBody {
        val nonceHex = extractHexResult(
            rpcRequest(chainId, "eth_getTransactionCount", listOf(from, "pending")),
            "eth_getTransactionCount"
        )
        val estimateGasHex = extractHexResult(
            rpcRequest(chainId, "eth_estimateGas", listOf(rpcTransactionObject(from, to, data, value))),
            "eth_estimateGas"
        )
        val gasPriceHex = extractHexResult(
            rpcRequest(chainId, "eth_gasPrice", emptyList<Any>()),
            "eth_gasPrice"
        )

        val nonce = decimalStringFromHex(nonceHex)
        val estimatedGas = BigInteger(
            estimateGasHex.removePrefix("0x").ifEmpty { "0" },
            16
        ).takeIf { it > BigInteger.ZERO } ?: BigInteger.valueOf(FALLBACK_GAS_LIMIT)
        val bufferedGas = estimatedGas.add(estimatedGas.divide(BigInteger.valueOf(5)))
        val gasLimit = (if (bufferedGas == BigInteger.ZERO) estimatedGas else bufferedGas).toString()
        val gasPrice = decimalStringFromHex(gasPriceHex)

        return TEthSendTransactionBody(
            organizationId = session.organizationId,
            caip2 = caip2For(chainId),
            data = data.ifEmpty { "0x" },
            from = from,
            gasLimit = gasLimit,
            maxFeePerGas = gasPrice,
            maxPriorityFeePerGas = gasPrice,
            nonce = nonce,
            sponsor = false,
            to = to,
            value = decimalStringFromHex(value)
        )
    }

    private suspend fun resolveTransactionHash(
        client: TurnkeyClientProtocol,
        organizationId: String,
        sendTransactionStatusId: String?
    ): String? {
        val statusId = sendTransactionStatusId ?: return null
        val status = client.getSendTransactionStatus(
            TGetSendTransactionStatusBody(
                organizationId = organizationId,
                sendTransactionStatusId = statusId
            )
        )
        return status.eth?.txHash
    }

    private suspend fun pollForTransactionHash(
        client: TurnkeyClientProtocol,
        organizationId: String,
        sendTransactionStatusId: String
    ): String {
        for (attempt in 0 until DEFAULT_POLLING_ATTEMPTS) {
            val status = client.getSendTransactionStatus(
                TGetSendTransactionStatusBody(
                    organizationId = organizationId,
                    sendTransactionStatusId = sendTransactionStatusId
                )
            )

            val txHash = status.eth?.txHash
            if (!txHash.isNullOrEmpty()) return txHash

            val normalized = status.txStatus.uppercase()
            val failed = normalized.contains("FAILED") ||
                normalized.contains("REJECTED") ||
                status.txError != null ||
                status.error?.message != null
            if (failed) {
                val message = status.txError
                    ?: status.error?.message
                    ?: "Turnkey transaction submission failed"
                throw RainError.ProviderError(IllegalStateException(message))
            }

            if (attempt + 1 < DEFAULT_POLLING_ATTEMPTS) {
                delay(pollingIntervalMs)
            }
        }
        throw RainError.InternalError("Turnkey transaction status polling timed out")
    }

    // ---------- RPC ----------

    private suspend fun rpcRequest(
        chainId: Int,
        method: String,
        params: List<Any>
    ): JSONObject {
        val rpcUrl = rpcEndpoints[chainId]
            ?: throw RainError.InvalidConfig("No RPC endpoint configured for chainId=$chainId")

        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", paramsToJsonArray(params))
        }

        val request = Request.Builder()
            .url(rpcUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull()))
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .build()

        val raw = try {
            httpClient.newCall(request).execute().use { it.body?.string() ?: "{}" }
        } catch (e: Exception) {
            Timber.e(e, "Rain SDK: Turnkey RPC request failed for $method on chainId=$chainId")
            throw RainError.NetworkError(message = "RPC request failed for $method", cause = e)
        }

        // Empty or non-JSON bodies happen when the connection drops mid-response or a
        // proxy strips the payload — treat them as a network failure rather than letting
        // a raw JSONException leak to the caller.
        val response = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            Timber.e(e, "Rain SDK: Turnkey RPC returned non-JSON body for $method on chainId=$chainId")
            throw RainError.NetworkError(message = "RPC request failed for $method", cause = e)
        }
        if (response.has("error") && !response.isNull("error")) {
            val err = response.getJSONObject("error")
            val code = err.optInt("code", -1)
            val message = err.optString("message", "Unknown RPC error")

            // Ethereum JSON-RPC nodes return "execution reverted" (typically with code -32000
            // or 3) for contract reverts. Surface this as WithdrawalRevertedByNetwork so
            // callers can distinguish a network-side revert from a generic RPC error.
            if (message.contains("revert", ignoreCase = true)) {
                throw RainError.WithdrawalRevertedByNetwork(
                    details = "Withdrawal reverted by the network: $message"
                )
            }
            throw RainError.InternalError("RPC error [$code]: $message")
        }
        return response
    }

    private fun extractHexResult(response: JSONObject, method: String): String {
        val result = response.opt("result")
        if (result !is String) {
            throw RainError.InternalError("Unexpected RPC result for method $method")
        }
        return result
    }

    private fun rpcTransactionObject(
        from: String,
        to: String,
        data: String,
        value: String
    ): Map<String, String> {
        // Native transfers carry no calldata. Omit the "data" field entirely (rather than
        // sending "0x" or "") so eth_estimateGas matches the request shape RPC nodes expect
        // for a value-only transfer.
        val tx = mutableMapOf("from" to from, "to" to to, "value" to value)
        if (data.isNotEmpty() && data != "0x") tx["data"] = data
        return tx
    }

    private fun paramsToJsonArray(params: List<Any>): JSONArray {
        val array = JSONArray()
        params.forEach { value ->
            when (value) {
                is Map<*, *> -> {
                    val obj = JSONObject()
                    value.forEach { (k, v) -> obj.put(k.toString(), v) }
                    array.put(obj)
                }
                else -> array.put(value)
            }
        }
        return array
    }

    // ---------- formatting ----------

    private fun caip2For(chainId: Int): String = "eip155:$chainId"

    private fun isNativeAsset(balance: V1AssetBalance, caip2: String): Boolean {
        val caip19 = balance.caip19 ?: return false
        return caip19.startsWith("$caip2/slip44:")
    }

    private fun tokenAddressFromCaip19(caip19: String, caip2: String): String? {
        if (!caip19.startsWith("$caip2/erc20:")) return null
        return caip19.substringAfterLast(":", "").takeIf { it.isNotEmpty() }
    }

    private fun chainIdFromCaip2(caip2: String): Int {
        return caip2.substringAfterLast(":", "").toIntOrNull() ?: 0
    }

    private fun decimalStringToDouble(balance: String?, decimals: Int): Double {
        if (balance.isNullOrEmpty()) return 0.0
        return BigDecimal(balance).movePointLeft(decimals).toDouble()
    }

    private fun decimalStringFromHex(hex: String): String {
        val cleaned = hex.removePrefix("0x").ifEmpty { "0" }
        return BigInteger(cleaned, 16).toString()
    }

    private fun ethereumSignatureHex(signature: V1SignRawPayloadResult): String {
        val r = normalizeHexComponent(signature.r, 64)
        val s = normalizeHexComponent(signature.s, 64)
        val v = String.format(Locale.ROOT, "%02x", normalizedRecoveryId(signature.v))
        return "0x$r$s$v"
    }

    private fun normalizeHexComponent(value: String, length: Int): String {
        // Strip every "0x" occurrence (not just a leading prefix) — Turnkey occasionally
        // returns components like "0x...0x..." that need full normalization before padding.
        val clean = value.lowercase().replace("0x", "")
        return if (clean.length >= length) clean.takeLast(length)
        else clean.padStart(length, '0')
    }

    private fun normalizedRecoveryId(value: String): Int {
        val clean = value.lowercase()
        val parsed = when {
            clean.startsWith("0x") -> clean.removePrefix("0x").toIntOrNull(16)
            else -> clean.toIntOrNull() ?: clean.toIntOrNull(16)
        } ?: return 27
        return if (parsed >= 27) parsed else parsed + 27
    }

    private fun iso8601(seconds: Double): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date((seconds * 1000).toLong()))
    }
}
