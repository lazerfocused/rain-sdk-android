package com.rain.sdk.portal

import com.rain.sdk.interfaces.RainClient
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Orchestration of the `initializePortal` extension. The non-empty-token path constructs a
 * real Portal SDK instance (covered by integration/manual testing), so the unit test exercises
 * the empty-token branch: it must initialize the SDK in wallet-agnostic mode and register no
 * provider.
 */
class InitializePortalTest {

    @Test
    fun `empty token initializes wallet-agnostic and registers no provider`() {
        val client = mockk<RainClient>(relaxed = true)
        val endpoints = mapOf(1 to "https://rpc.example")

        client.initializePortal(portalSessionToken = "", rpcEndpoints = endpoints)

        verify(exactly = 1) { client.initialize(endpoints) }
        verify(exactly = 0) { client.register(any()) }
    }
}
