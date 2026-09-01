package com.rain.sdk.sample

import com.rain.sdk.portal.PortalSessionState
import com.rain.sdk.privy.PrivySessionState
import com.rain.sdk.turnkey.TurnkeySessionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Coarse health of the wallet session, for colouring the Home screen's session card. */
enum class SessionHealth { Healthy, Transitional, Dead, Unknown }

/** Provider-agnostic view of the wallet session; each provider's state type maps to it below. */
data class WalletSessionStatus(
    val label: String,
    val health: SessionHealth,
    val detail: String? = null,
)

/** Turnkey: JWT-backed, so `Active` carries an expiry. */
fun TurnkeySessionState.toStatus(): WalletSessionStatus = when (this) {
    is TurnkeySessionState.Loading ->
        WalletSessionStatus("Restoring session", SessionHealth.Transitional)
    is TurnkeySessionState.Active ->
        WalletSessionStatus(
            label = "Active",
            health = SessionHealth.Healthy,
            detail = "JWT expires at ${formatClock(expiresAtEpochSeconds)} (auto-refreshed by the SDK)"
        )
    is TurnkeySessionState.Expired ->
        WalletSessionStatus("Expired", SessionHealth.Dead, "Log in again")
    is TurnkeySessionState.Unauthenticated ->
        WalletSessionStatus("Unauthenticated", SessionHealth.Dead, "Log in again")
}

/** Privy: self-refreshing with no expiry; `Unverified` = restored offline, recoverable. */
fun PrivySessionState.toStatus(): WalletSessionStatus = when (this) {
    is PrivySessionState.Loading ->
        WalletSessionStatus("Restoring session", SessionHealth.Transitional)
    is PrivySessionState.Active ->
        WalletSessionStatus("Active", SessionHealth.Healthy, "Privy refreshes the session itself")
    is PrivySessionState.Unverified ->
        WalletSessionStatus(
            label = "Unverified",
            health = SessionHealth.Transitional,
            detail = "Restored offline; re-verified when connectivity returns"
        )
    is PrivySessionState.Unauthenticated ->
        WalletSessionStatus("Unauthenticated", SessionHealth.Dead, "Log in again")
}

/** Portal: derived from call outcomes — the vendor exposes no auth state. */
fun PortalSessionState.toStatus(): WalletSessionStatus = when (this) {
    is PortalSessionState.Unknown ->
        WalletSessionStatus("Unknown", SessionHealth.Unknown, "No Portal call has completed yet")
    is PortalSessionState.Active ->
        WalletSessionStatus("Active", SessionHealth.Healthy, "Last Portal call succeeded")
    is PortalSessionState.Refreshing ->
        WalletSessionStatus("Refreshing", SessionHealth.Transitional, "Installing a re-minted session token")
    is PortalSessionState.Expired ->
        WalletSessionStatus(
            label = "Expired",
            health = SessionHealth.Dead,
            detail = "Portal rejected the session token; provide a new one"
        )
}

private val clockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatClock(epochSeconds: Double): String =
    clockFormatter.format(Instant.ofEpochSecond(epochSeconds.toLong()))
