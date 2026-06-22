package com.rain.sdk.portal

import com.rain.sdk.interfaces.RainClient

/**
 * Initializes the Rain SDK with a Portal wallet provider.
 *
 * Validates [rpcEndpoints] and marks the SDK initialized via core's wallet-agnostic
 * [RainClient.initialize], then — when [portalSessionToken] is non-empty — builds a
 * [PortalProvider] (initializing the Portal SDK and its chain reader / token store) and
 * registers it as the active provider.
 *
 * Replaces the former core `RainClient.initializePortal`. Portal integrators add the
 * `xyz.rain:rain-portal` dependency and `import com.rain.sdk.portal.initializePortal`.
 *
 * @param portalSessionToken a valid Portal session token; empty leaves the SDK in
 *   wallet-agnostic mode with no Portal provider registered.
 * @param rpcEndpoints map of numeric chain IDs to RPC URLs.
 * @param chainId optional default chain ID; otherwise resolved from [rpcEndpoints].
 */
fun RainClient.initializePortal(
    portalSessionToken: String = "",
    rpcEndpoints: Map<Int, String>,
    chainId: Int? = null
) {
    initialize(rpcEndpoints)
    if (portalSessionToken.isNotEmpty()) {
        register(PortalProvider.create(portalSessionToken, rpcEndpoints, chainId))
    }
}
