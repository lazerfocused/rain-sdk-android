package com.rain.sdk.internal.core

import com.rain.sdk.internal.error.RainError
import com.rain.sdk.interfaces.RainClient
import com.rain.sdk.internal.error.ErrorMapper
import com.rain.sdk.internal.transaction.TransactionCoordinator
import com.rain.sdk.internal.transaction.TransactionExecutor
import com.rain.sdk.internal.transaction.TransactionSigner
import com.rain.sdk.internal.transaction.TransactionValidator
import com.rain.sdk.internal.transaction.WithdrawCollateralRequest
import com.rain.sdk.models.RainAdminSignature
import com.rain.sdk.models.RainTokenTransferResult
import com.rain.sdk.models.RainWithdrawAddresses
import com.rain.sdk.models.RainWithdrawResult
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.internal.provider.PortalWalletProvider
import com.rain.sdk.internal.provider.TurnkeyContextAdapter
import com.rain.sdk.internal.provider.TurnkeyContextProtocol
import com.rain.sdk.internal.provider.TurnkeyWalletProvider
import com.rain.sdk.internal.provider.WalletProvider
import com.turnkey.core.TurnkeyContext
import io.portalhq.android.Portal
import io.portalhq.android.mpc.data.FeatureFlags
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import android.graphics.Bitmap
import com.rain.sdk.utils.QRGenerator

/**
 * Internal implementation of [RainClient].
 *
 * This class acts as a thin facade that delegates to specialized components:
 * - PortalManager: Manages Portal SDK interactions
 * - ConfigManager: Handles configuration and validation
 * - TransactionCoordinator: Orchestrates transaction flows (routes through the active wallet provider)
 *
 * Wallet provider is registered at initialization time (Portal or Turnkey) and used for
 * sign + send + balance + history operations.
 */
internal class RainSdkManager(
  private val portalManager: PortalManager = PortalManager(),
  private val configManager: ConfigManager = ConfigManager(),
  private val errorMapper: ErrorMapper = ErrorMapper()
) : RainClient {

  /**
   * Hook for tests to inject a fake `TurnkeyContextProtocol` without touching the real
   * Turnkey singleton. Kept as a settable property (rather than a constructor default)
   * so that simply instantiating RainSdkManager doesn't transitively class-load the
   * Turnkey types — which would force JDK 24+ on test JVMs.
   */
  @Suppress("PrivatePropertyName")
  internal var turnkeyContextFactory: ((TurnkeyContext) -> TurnkeyContextProtocol)? = null

  private var walletProvider: WalletProvider? = null
  private var turnkeyContext: TurnkeyContext? = null

  private val signer = TransactionSigner({ walletProvider }, errorMapper)
  private val executor = TransactionExecutor({ walletProvider }, errorMapper)
  private val transactionCoordinator: TransactionCoordinator =
    TransactionCoordinator(
      walletProvider = { walletProvider },
      validator = TransactionValidator(),
      signer = signer,
      executor = executor
    )

  override val isInitialized: Boolean
    get() = configManager.isInitialized

  override val portal: Portal
    get() = portalManager.getPortalInstance()

  override val turnkey: TurnkeyContext
    get() = turnkeyContext ?: throw RainError.SdkNotInitialized()

  override fun initializePortal(
    portalSessionToken: String,
    rpcEndpoints: Map<Int, String>,
    chainId: Int?,
  ) {
    try {
      // Validate and setup RPC endpoints
      val eip155RpcConfig = configManager.validateAndSetupRpcEndpoints(rpcEndpoints)

      // Determine legacy chain ID
      val legacyChainId = configManager.determineLegacyChainId(chainId, rpcEndpoints)

      // Initialize Portal instance if token is provided
      if (portalSessionToken.isNotEmpty()) {
        portalManager.initialize(
          apiKey = portalSessionToken,
          legacyEthChainId = legacyChainId,
          rpcConfig = eip155RpcConfig,
          featureFlags = FeatureFlags(isMultiBackupEnabled = true),
          autoApprove = true
        )

        // Initialize wallet provider - Default to Portal
        walletProvider = PortalWalletProvider(portalManager)
        turnkeyContext = null
      }

      // Mark SDK as initialized
      configManager.markInitialized()

      Timber.d("Rain SDK: Initialized successfully")
    } catch (e: RainError) {
      Timber.e(e, "Rain SDK: Initialization error")
      throw e
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Timber.e(e, "Rain SDK: Portal SDK error")
      throw RainError.ProviderError(e)
    }
  }

  override suspend fun initializeTurnkey(
    turnkey: TurnkeyContext,
    rpcEndpoints: Map<Int, String>,
    chainId: Int?,
    walletAddress: String?
  ) {
    try {
      // Validate and setup RPC endpoints (also writes them into RainConfig)
      configManager.validateAndSetupRpcEndpoints(rpcEndpoints)

      val context = turnkeyContextFactory?.invoke(turnkey) ?: TurnkeyContextAdapter(turnkey)
      val provider = TurnkeyWalletProvider(
        turnkey = context,
        rpcEndpoints = rpcEndpoints,
        walletAddressOverride = walletAddress
      )

      // Probe — ensures Turnkey has an EVM wallet available before swapping the provider in.
      withContext(Dispatchers.IO) {
        provider.getAddress()
      }

      walletProvider = provider
      turnkeyContext = turnkey
      // Turnkey path doesn't use Portal — make sure stale state is cleared.
      portalManager.destroy()

      configManager.markInitialized()
      Timber.d("Rain SDK: Initialized Turnkey wallet provider successfully")
    } catch (e: RainError) {
      Timber.e(e, "Rain SDK: Turnkey initialization error")
      throw e
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Timber.e(e, "Rain SDK: Turnkey initialization error")
      throw errorMapper.mapSigningError(e)
    }
  }

  override suspend fun withdrawCollateral(
    chainId: Int,
    addresses: RainWithdrawAddresses,
    amount: Double,
    decimals: Int,
    adminSignature: RainAdminSignature,
    nonce: BigInteger?,
    autoSend: Boolean
  ): RainWithdrawResult {
    if (!isInitialized) {
      throw RainError.SdkNotInitialized()
    }

    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    val walletAddress = provider.getAddress()

    // Create request object
    val request = WithdrawCollateralRequest(
      chainId = chainId,
      addresses = addresses,
      amount = amount,
      decimals = decimals,
      adminSignature = adminSignature,
      walletAddress = walletAddress,
      nonce = nonce
    )

    // Delegate to coordinator with autoSend parameter
    val (txHash, txData) = transactionCoordinator.executeWithdrawCollateral(request, autoSend)

    return RainWithdrawResult(
      transactionHash = txHash,
      transactionData = txData
    )
  }

  override suspend fun estimateGas(
    chainId: Int,
    from: String,
    to: String,
    data: String
  ): Double {
    if (!isInitialized) {
      throw RainError.SdkNotInitialized()
    }

    return transactionCoordinator.estimateGas(
      chainId = chainId,
      from = from,
      to = to,
      data = data
    )
  }

  override suspend fun getAddress(): String {
    if (!isInitialized) {
      throw RainError.SdkNotInitialized()
    }
    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    return try {
      provider.getAddress()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to get wallet address")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun sendNativeToken(
    chainId: Int,
    toAddress: String,
    amount: Double
  ): RainTokenTransferResult {
    if (!isInitialized) {
      throw RainError.SdkNotInitialized()
    }

    val provider = walletProvider ?: throw RainError.SdkNotInitialized()

    return try {
      val txHash = provider.sendNativeToken(chainId, toAddress, amount)
      RainTokenTransferResult(transactionHash = txHash)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to send native token")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun sendToken(
    chainId: Int,
    contractAddress: String,
    toAddress: String,
    amount: Double,
    decimals: Int
  ): RainTokenTransferResult {
    if (!isInitialized) {
      throw RainError.SdkNotInitialized()
    }

    val provider = walletProvider ?: throw RainError.SdkNotInitialized()

    return try {
      val txHash = provider.sendToken(chainId, contractAddress, toAddress, amount, decimals)
      RainTokenTransferResult(transactionHash = txHash)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to send ERC-20 token")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun getNativeBalance(chainId: Int): Double {
    if (!isInitialized) throw RainError.SdkNotInitialized()
    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    return try {
      provider.getNativeBalance(chainId)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to get native balance")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun getERC20Balance(chainId: Int, tokenAddress: String, decimals: Int?): Double {
    if (!isInitialized) throw RainError.SdkNotInitialized()
    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    return try {
      provider.getERC20Balance(chainId, tokenAddress, decimals)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to get ERC-20 balance")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun getERC20Balances(chainId: Int): Map<String, Double> {
    if (!isInitialized) throw RainError.SdkNotInitialized()
    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    return try {
      provider.getERC20Balances(chainId)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to get ERC-20 balances")
      throw errorMapper.mapTransactionError(e)
    }
  }

  override suspend fun generateAddressQRCode(address: String?, width: Int, height: Int): Bitmap {
    if (!isInitialized) throw RainError.SdkNotInitialized()

    val targetAddress = address ?: getAddress()

    return withContext(Dispatchers.Default) {
        try {
            QRGenerator.generateQRCode(targetAddress, width, height)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Rain SDK: Failed to generate QR code")
            throw RainError.ProviderError(e)
        }
    }
  }

  override suspend fun getTransactions(
    chainId: Int,
    limit: Int?,
    offset: Int?,
    order: RainTransactionOrder?
  ): RainTransactionResult {
    if (!isInitialized) throw RainError.SdkNotInitialized()
    val provider = walletProvider ?: throw RainError.SdkNotInitialized()
    return try {
      provider.getTransactions(chainId, limit, offset, order)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      if (e is RainError) throw e
      Timber.e(e, "Rain SDK: Failed to get transactions")
      throw errorMapper.mapTransactionError(e)
    }
  }
}
