package com.rain.sdk.portal

import com.rain.sdk.RainChain
import com.rain.sdk.internal.network.chainreader.EvmChainReader
import com.rain.sdk.internal.provider.Capability
import com.rain.sdk.internal.provider.ProviderId
import com.rain.sdk.internal.provider.RainTransactionFeeEstimatingProvider
import com.rain.sdk.internal.provider.RainTypedDataSignerProvider
import com.rain.sdk.internal.provider.WalletProvider
import com.rain.sdk.internal.tokenstore.TokenMetadataStore
import com.rain.sdk.models.Balance
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.models.Token
import com.rain.sdk.models.TokenInfo
import com.rain.sdk.utils.EthereumConverter
import io.portalhq.android.Portal
import io.portalhq.android.mpc.data.FeatureFlags
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigDecimal

/**
 * [WalletProvider] backed by the Portal SDK. EVM-only (Portal has no Solana support), so it
 * advertises typed-data signing, fee estimation, and multi-chain — but not Solana transfers.
 *
 * Construct via [create] (builds its own Portal instance, chain reader, and token store from
 * the RPC endpoints), or directly with a [PortalManager] for testing.
 */
class PortalProvider internal constructor(
    private val portalManager: PortalManager,
    private val tokenStore: TokenMetadataStore
) : WalletProvider,
    RainTypedDataSignerProvider,
    RainTransactionFeeEstimatingProvider {

    override val id: ProviderId = ProviderId.PORTAL
    override val capabilities: Set<Capability> = setOf(
        Capability.TYPED_DATA_SIGNING,
        Capability.FEE_ESTIMATION,
        Capability.MULTI_CHAIN
    )

    /** The underlying Portal instance, for clients that need direct Portal access. */
    val portal: Portal get() = portalManager.getPortalInstance()

    override suspend fun getWalletAddress(): String = portalManager.getAddress()

    override suspend fun sendNativeToken(
        chainId: Int,
        toAddress: String,
        amountInEth: Double
    ): String {
        val fromAddress = getWalletAddress()
        val valueWeiHex = EthereumConverter.convertEthToWeiHex(amountInEth)
        // For native transfers, data is "0x".
        return portalManager.sendTransaction(
            chainId = chainId,
            from = fromAddress,
            to = toAddress,
            data = "0x",
            value = valueWeiHex
        )
    }

    override suspend fun sendToken(
        chainId: Int,
        contractAddress: String,
        toAddress: String,
        amount: Double,
        decimals: Int
    ): String {
        val fromAddress = getWalletAddress()

        // Encode ERC-20 transfer(address, uint256).
        val tokenAmount = amount.toBigDecimal()
            .multiply(BigDecimal.TEN.pow(decimals))
            .toBigInteger()
        val function = Function(
            "transfer",
            listOf(Address(toAddress), Uint256(tokenAmount)),
            emptyList<TypeReference<*>>()
        )
        val data = FunctionEncoder.encode(function)

        // For ERC-20 transfers, "to" is the contract address and value is 0x0.
        return portalManager.sendTransaction(
            chainId = chainId,
            from = fromAddress,
            to = contractAddress,
            data = data,
            value = "0x0"
        )
    }

    override suspend fun getBalance(chainId: Int, token: Token): Balance =
        portalManager.getBalance(chainId, token, tokenStore)

    override suspend fun getBalances(chainId: Int): List<Balance> =
        portalManager.getBalances(chainId, tokenStore)

    override suspend fun getTransactions(
        chainId: Int,
        limit: Int?,
        offset: Int?,
        order: RainTransactionOrder?
    ): RainTransactionResult =
        portalManager.getTransactions(chainId, limit, offset, order)

    override suspend fun signTypedData(
        chainId: Int,
        walletAddress: String,
        typedDataJson: String
    ): String =
        portalManager.signTypedData(chainId, walletAddress, typedDataJson)

    override suspend fun sendTransaction(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): String =
        portalManager.sendTransaction(chainId, from, to, data, value)

    override suspend fun estimateTransactionFee(
        chainId: Int,
        from: String,
        to: String,
        data: String,
        value: String
    ): Double =
        portalManager.estimateTransactionFee(chainId, from, to, data, value)

    companion object {
        private const val EIP155 = "eip155"

        /**
         * Builds a fully wired [PortalProvider]: initializes the Portal SDK with [portalSessionToken]
         * and [rpcEndpoints], then constructs its own EVM chain reader + token metadata store.
         *
         * @param chainId optional default chain; falls back to Avalanche Mainnet, else the first endpoint.
         * @param seedTokens host-registered token metadata to seed the store with.
         */
        fun create(
            portalSessionToken: String,
            rpcEndpoints: Map<Int, String>,
            chainId: Int? = null,
            seedTokens: List<TokenInfo> = emptyList()
        ): PortalProvider {
            val legacyChainId = chainId
                ?: if (rpcEndpoints.containsKey(RainChain.AVALANCHE_MAINNET)) RainChain.AVALANCHE_MAINNET
                else rpcEndpoints.keys.first()
            val rpcConfig = rpcEndpoints.mapKeys { "$EIP155:${it.key}" }

            val manager = PortalManager()
            manager.initialize(
                apiKey = portalSessionToken,
                legacyEthChainId = legacyChainId,
                rpcConfig = rpcConfig,
                featureFlags = FeatureFlags(isMultiBackupEnabled = true),
                autoApprove = true
            )

            val reader = EvmChainReader(rpcEndpoints)
            val store = TokenMetadataStore(chainReader = reader, seedTokens = seedTokens)
            return PortalProvider(manager, store)
        }
    }
}
