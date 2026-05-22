package com.rain.sdk.internal.provider

import com.google.common.truth.Truth.assertThat
import com.rain.sdk.internal.core.PortalManager
import com.rain.sdk.internal.error.RainError
import com.rain.sdk.internal.helpers.TestFixtures
import com.rain.sdk.models.RainTransaction
import com.rain.sdk.models.RainTransactionOrder
import com.rain.sdk.models.RainTransactionResult
import com.rain.sdk.utils.EthereumConverter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger
import kotlin.math.pow

class PortalWalletProviderTest {

    private lateinit var portalManager: PortalManager
    private lateinit var portalWalletProvider: PortalWalletProvider

    @Before
    fun setUp() {
        portalManager = mockk()
        portalWalletProvider = PortalWalletProvider(portalManager)
    }

    @Test
    fun `sendNativeToken should call portalManager sendTransaction with correct params`() = runBlocking {
        // Given
        val chainId = 43114
        val fromAddress = "0x1234567890123456789012345678901234567890"
        val toAddress = "0x0987654321098765432109876543210987654321"
        val amountInEth = 1.5
        val expectedValueWeiHex = EthereumConverter.convertEthToWeiHex(amountInEth)
        val expectedTxHash = "0xHash"

        coEvery { portalManager.getAddress() } returns fromAddress
        coEvery { 
            portalManager.sendTransaction(
                chainId = chainId,
                from = fromAddress,
                to = toAddress,
                data = "0x",
                value = expectedValueWeiHex
            )
        } returns expectedTxHash

        // When
        val result = portalWalletProvider.sendNativeToken(chainId, toAddress, amountInEth)

        // Then
        assertEquals(expectedTxHash, result)
        coVerify { 
            portalManager.sendTransaction(
                chainId = chainId,
                from = fromAddress,
                to = toAddress,
                data = "0x",
                value = expectedValueWeiHex
            )
        }
    }

    @Test
    fun `sendToken should call portalManager sendTransaction with ABI encoded data`() = runBlocking {
        // Given
        val chainId = 43114
        val fromAddress = "0x1234567890123456789012345678901234567890"
        val toAddress = "0x0987654321098765432109876543210987654321"
        val contractAddress = "0x1111111111111111111111111111111111111111"
        val amount = 100.0
        val decimals = 6
        val expectedTxHash = "0xERC20Hash"
        
        // Calculate expected data
        val tokenAmount = amount.toBigDecimal()
            .multiply(java.math.BigDecimal.TEN.pow(decimals))
            .toBigInteger()
        val function = Function(
            "transfer",
            listOf(Address(toAddress), Uint256(tokenAmount)),
            emptyList<TypeReference<*>>()
        )
        val expectedData = FunctionEncoder.encode(function)

        coEvery { portalManager.getAddress() } returns fromAddress
        coEvery { 
            portalManager.sendTransaction(
                chainId = chainId,
                from = fromAddress,
                to = contractAddress,
                data = expectedData,
                value = "0x0"
            )
        } returns expectedTxHash

        // When
        val result = portalWalletProvider.sendToken(chainId, contractAddress, toAddress, amount, decimals)

        // Then
        assertEquals(expectedTxHash, result)
        coVerify {
            portalManager.sendTransaction(
                chainId = chainId,
                from = fromAddress,
                to = contractAddress,
                data = expectedData,
                value = "0x0"
            )
        }
    }

    // ---- Balance delegations ----------------------------------------------------

    @Test
    fun `getAddress delegates to PortalManager`() = runBlocking {
        coEvery { portalManager.getAddress() } returns TestFixtures.WALLET_ADDRESS

        val address = portalWalletProvider.getAddress()

        assertThat(address).isEqualTo(TestFixtures.WALLET_ADDRESS)
        coVerify { portalManager.getAddress() }
    }

    @Test
    fun `getNativeBalance delegates to PortalManager and returns its result`() = runBlocking {
        coEvery { portalManager.getNativeBalance(43114) } returns 1.5

        val balance = portalWalletProvider.getNativeBalance(chainId = 43114)

        assertThat(balance).isEqualTo(1.5)
        coVerify { portalManager.getNativeBalance(43114) }
    }

    @Test
    fun `getNativeBalance propagates PortalManager errors`() {
        coEvery { portalManager.getNativeBalance(any()) } throws RainError.ProviderError(
            RuntimeException("portal indexer failed")
        )

        assertThrows(RainError.ProviderError::class.java) {
            runBlocking { portalWalletProvider.getNativeBalance(chainId = 43114) }
        }
    }

    @Test
    fun `getERC20Balance forwards chainId-token-decimals and returns PortalManager result`() = runBlocking {
        coEvery {
            portalManager.getERC20Balance(43114, TestFixtures.USDC_ADDRESS, 6)
        } returns 100.0

        val balance = portalWalletProvider.getERC20Balance(
            chainId = 43114,
            tokenAddress = TestFixtures.USDC_ADDRESS,
            decimals = 6
        )

        assertThat(balance).isEqualTo(100.0)
        coVerify { portalManager.getERC20Balance(43114, TestFixtures.USDC_ADDRESS, 6) }
    }

    @Test
    fun `getERC20Balances returns whatever PortalManager returned`() = runBlocking {
        val map = mapOf(TestFixtures.USDC_ADDRESS to 25.0, TestFixtures.TOKEN_ADDRESS to 7.5)
        coEvery { portalManager.getERC20Balances(1) } returns map

        val result = portalWalletProvider.getERC20Balances(chainId = 1)

        assertThat(result).isEqualTo(map)
    }

    @Test
    fun `getERC20Balances propagates PortalManager errors`() {
        coEvery { portalManager.getERC20Balances(any()) } throws RainError.ProviderError(
            RuntimeException("getAssets failed")
        )

        assertThrows(RainError.ProviderError::class.java) {
            runBlocking { portalWalletProvider.getERC20Balances(chainId = 1) }
        }
    }

    // ---- Transaction history delegation -----------------------------------------

    @Test
    fun `getTransactions forwards pagination + order to PortalManager`() = runBlocking {
        val expected = RainTransactionResult(
            transactions = listOf(
                RainTransaction(
                    hash = "0xabc",
                    blockNumber = "100",
                    from = "0xfrom",
                    to = "0xto",
                    value = "1.0",
                    chainId = "43114"
                )
            )
        )
        coEvery {
            portalManager.getTransactions(43114, 5, 2, RainTransactionOrder.DESC)
        } returns expected

        val result = portalWalletProvider.getTransactions(
            chainId = 43114,
            limit = 5,
            offset = 2,
            order = RainTransactionOrder.DESC
        )

        assertThat(result).isSameInstanceAs(expected)
        coVerify { portalManager.getTransactions(43114, 5, 2, RainTransactionOrder.DESC) }
    }

    // ---- Signing + low-level sendTransaction + fee estimation -------------------

    @Test
    fun `signTypedData delegates to PortalManager with chain + wallet + JSON`() = runBlocking {
        val expectedSig = "0x" + "1".repeat(130)
        coEvery {
            portalManager.signTypedData(1, TestFixtures.WALLET_ADDRESS, """{"foo":"bar"}""")
        } returns expectedSig

        val signature = portalWalletProvider.signTypedData(
            chainId = 1,
            walletAddress = TestFixtures.WALLET_ADDRESS,
            typedDataJson = """{"foo":"bar"}"""
        )

        assertThat(signature).isEqualTo(expectedSig)
    }

    @Test
    fun `sendTransaction (low-level) delegates to PortalManager`() = runBlocking {
        val expectedHash = "0x" + "f".repeat(64)
        coEvery {
            portalManager.sendTransaction(
                chainId = 1,
                from = TestFixtures.WALLET_ADDRESS,
                to = TestFixtures.CONTRACT_ADDRESS,
                data = "0xdeadbeef",
                value = "0x0"
            )
        } returns expectedHash

        val txHash = portalWalletProvider.sendTransaction(
            chainId = 1,
            from = TestFixtures.WALLET_ADDRESS,
            to = TestFixtures.CONTRACT_ADDRESS,
            data = "0xdeadbeef",
            value = "0x0"
        )

        assertThat(txHash).isEqualTo(expectedHash)
    }

    @Test
    fun `sendTransaction propagates TransactionSimulationFailed from PortalManager`() {
        coEvery {
            portalManager.sendTransaction(any(), any(), any(), any(), any())
        } throws RainError.TransactionSimulationFailed(RuntimeException("revert"))

        assertThrows(RainError.TransactionSimulationFailed::class.java) {
            runBlocking {
                portalWalletProvider.sendTransaction(
                    chainId = 1,
                    from = TestFixtures.WALLET_ADDRESS,
                    to = TestFixtures.CONTRACT_ADDRESS,
                    data = "0x",
                    value = "0x0"
                )
            }
        }
    }

    @Test
    fun `estimateTransactionFee delegates to PortalManager`() = runBlocking {
        coEvery {
            portalManager.estimateTransactionFee(1, TestFixtures.WALLET_ADDRESS, TestFixtures.CONTRACT_ADDRESS, "0x", "0x0")
        } returns 0.00042

        val fee = portalWalletProvider.estimateTransactionFee(
            chainId = 1,
            from = TestFixtures.WALLET_ADDRESS,
            to = TestFixtures.CONTRACT_ADDRESS,
            data = "0x",
            value = "0x0"
        )

        assertThat(fee).isWithin(1e-12).of(0.00042)
    }
}
