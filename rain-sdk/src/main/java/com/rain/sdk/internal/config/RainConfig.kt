package com.rain.sdk.internal.config

import android.webkit.URLUtil
import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe configuration storage for Rain SDK.
 * 
 * This class manages global SDK settings including RPC endpoints,
 * authentication tokens, and initialization state. All operations
 * are thread-safe using proper synchronization.
 * 
 * @internal This class is internal to the SDK and should not be used directly.
 */
internal class RainConfig private constructor() {
    
    private val lock = Any()
    
    @Volatile
    private var _isInitialized: Boolean = false
    
    private val rpcUrls = ConcurrentHashMap<String, String>()
    
    /**
     * Checks if the SDK has been initialized.
     * Thread-safe read access.
     */
    val isInitialized: Boolean
        get() = _isInitialized
    
    /**
     * Registers an RPC endpoint for a specific chain.
     *
     * @param chainId CAIP-2 chain ID (e.g. `"eip155:1"`)
     * @param url RPC endpoint URL (must be valid)
     * @throws IllegalArgumentException if chainId is invalid or URL is malformed
     */
    fun setRpcUrl(chainId: String, url: String) {
        require(chainId.isNotBlank()) { "Chain ID must be non-blank" }
        require(URLUtil.isValidUrl(url)) { "Invalid RPC URL: $url" }
        rpcUrls[chainId] = url
    }

    /**
     * Gets the RPC endpoint for a specific chain.
     *
     * @param chainId CAIP-2 chain ID to look up
     * @return RPC URL if configured, null otherwise
     */
    fun getRpcUrl(chainId: String): String? = rpcUrls[chainId]
    
    /**
     * Marks the SDK as initialized.
     * This should be called after successful initialization.
     */
    fun markInitialized() {
        synchronized(lock) {
            _isInitialized = true
        }
    }
    
    /**
     * Clears all configuration data and resets initialization state.
     * Use this for testing or when reinitializing the SDK.
     */
    fun clear() {
        synchronized(lock) {
            rpcUrls.clear()
            _isInitialized = false
        }
    }
    
    companion object {
        @Volatile
        private var instance: RainConfig? = null
        
        /**
         * Gets the singleton instance of RainConfig.
         * Creates a new instance if one doesn't exist (double-checked locking).
         */
        fun getInstance(): RainConfig {
            return instance ?: synchronized(this) {
                instance ?: RainConfig().also { instance = it }
            }
        }
        
        /**
         * Resets the singleton instance.
         * For testing purposes only.
         */
        @VisibleForTesting
        internal fun reset() {
            synchronized(this) {
                instance?.clear()
                instance = null
            }
        }
    }
}
