package com.rain.sdk.internal.core

import android.webkit.URLUtil
import com.rain.sdk.RainChain
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.config.RainConfig
import com.rain.sdk.internal.utils.ChainIdFormat
import kotlin.collections.iterator

/**
 * Manages SDK configuration and validation.
 * 
 * Handles RPC endpoint validation, chain configuration, and initialization state.
 */
internal class ConfigManager {
    
    private val config = RainConfig.getInstance()
    
    /**
     * Checks if the SDK has been initialized.
     */
    val isInitialized: Boolean
        get() = config.isInitialized
    
    /**
     * Validates and sets up RPC endpoints for all chains.
     *
     * @param rpcEndpoints Map of CAIP-2 chain IDs to RPC URLs
     * @return The validated CAIP-2 chain identifier -> RPC URL map (e.g. `"eip155:43114" -> "https://..."`)
     * @throws RainError.InvalidConfig if validation fails
     */
    fun validateAndSetupRpcEndpoints(
        rpcEndpoints: Map<String, String>
    ): Map<String, String> {
        if (rpcEndpoints.isEmpty()) {
            throw RainError.InvalidConfig("At least one RPC endpoint is required")
        }

        for ((chainId, url) in rpcEndpoints) {
            // Validate CAIP-2 chain ID
            if (!isValidCaip2(chainId)) {
                throw RainError.InvalidConfig("Invalid CAIP-2 Chain ID: $chainId")
            }

            // Validate URL
            if (!URLUtil.isValidUrl(url)) {
                throw RainError.InvalidConfig("Invalid RPC URL for chainId $chainId: $url")
            }

            config.setRpcUrl(chainId, url)
        }

        return rpcEndpoints.toMap()
    }

    /**
     * Determines the legacy (default) CAIP-2 chain ID to use for Portal.
     *
     * Priority:
     * 1. Use provided chainId if specified
     * 2. Use Avalanche Mainnet if available in endpoints
     * 3. Use the first available chain from endpoints
     *
     * @param providedChainId Optional CAIP-2 chain ID provided by user
     * @param rpcEndpoints Available RPC endpoints (CAIP-2 keyed)
     * @return The legacy CAIP-2 chain ID to use
     */
    fun determineLegacyChainId(
        providedChainId: String?,
        rpcEndpoints: Map<String, String>
    ): String {
        return providedChainId ?: if (rpcEndpoints.containsKey(RainChain.AVALANCHE_MAINNET)) {
            RainChain.AVALANCHE_MAINNET
        } else {
            rpcEndpoints.keys.first()
        }
    }

    /**
     * Gets the RPC URL for a specific chain.
     *
     * @param chainId The CAIP-2 chain ID
     * @return The RPC URL or null if not configured
     */
    fun getRpcUrl(chainId: String): String? {
        return config.getRpcUrl(chainId)
    }

    /** A recognizable CAIP-2 namespace: an `eip155:N` chain or a `solana:<genesis>` cluster. */
    private fun isValidCaip2(chainId: String): Boolean =
        chainId.isNotBlank() &&
            (ChainIdFormat.EIP155.parse(chainId) != null || chainId.startsWith("solana:"))
    
    /**
     * Marks the SDK as initialized.
     */
    fun markInitialized() {
        config.markInitialized()
    }
}
