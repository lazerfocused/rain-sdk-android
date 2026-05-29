package com.rain.sdk.utils

import java.math.BigInteger

/**
 * Utility for converting between different Ethereum units and formats.
 *
 * Pure functions only — no Portal/Turnkey/web3j dependencies — so the converter is safe
 * to call from any layer.
 */
object EthereumConverter {

    /**
     * Normalizes an optional hex string by stripping invalid values down to `"0x0"`.
     * Used by Portal/Turnkey adapters before parsing.
     */
    fun normalizedHexString(hex: String?): String =
        hex?.takeIf { it.startsWith("0x") && it.length > 2 } ?: "0x0"

    /**
     * Converts a Wei hex string to ETH (Double). Falls back to manual BigInteger parsing
     * if the input has odd formatting.
     */
    fun convertWeiHexToEth(ethBalanceHexValue: String): Double {
        val cleanedHex = ethBalanceHexValue.removePrefix("0x").ifEmpty { "0" }
        return BigInteger(cleanedHex, 16).toBigDecimal().movePointLeft(18).toDouble()
    }

    /**
     * Converts a Wei hex string to its unit-less Double value.
     */
    fun convertWeiHexToDouble(ethBalanceHexValue: String): Double {
        val cleanedHex = ethBalanceHexValue.removePrefix("0x").ifEmpty { "0" }
        return BigInteger(cleanedHex, 16).toDouble()
    }

    /** Converts a Wei BigInteger to ETH (Double). */
    fun convertWeiToEth(wei: BigInteger): Double =
        wei.toBigDecimal().movePointLeft(18).toDouble()

    /** Converts an ETH Double value to a Wei hex string. */
    fun convertEthToWeiHex(ethBalance: Double): String {
        val wei = ethBalance.toBigDecimal().multiply(1e18.toBigDecimal()).toBigInteger()
        return "0x${wei.toString(16)}"
    }

    /**
     * Converts a hex string to a Double with the specified number of decimals.
     *
     * @param hex The hex string (e.g. "0x...")
     * @param decimals The number of decimal places
     */
    fun convertHexToDouble(hex: String, decimals: Int): Double {
        val cleanedHex = hex.removePrefix("0x").ifEmpty { "0" }
        return BigInteger(cleanedHex, 16).toBigDecimal().movePointLeft(decimals).toDouble()
    }
}
