package com.rain.sdk.turnkey

/**
 * Session-hardening policy for the Turnkey provider.
 *
 * Controls how the SDK guards wallet calls against session expiry and transient failures:
 * expiry is checked before every Turnkey call, sessions inside [refreshBufferSeconds] of
 * expiry are refreshed proactively (when [autoRefresh] is on), a 401/invalid-session failure
 * is refreshed and retried once, and transient failures (HTTP 5xx/429/408, network I/O) on
 * read paths are retried with exponential backoff. Writes (sends, signing) are never retried
 * on transient failures — only after a refresh that proves the original request was rejected
 * before execution.
 *
 * @param refreshBufferSeconds Refresh the session when it is within this window of expiring.
 * @param autoRefresh When true the SDK calls Turnkey's `refreshSession` itself; when false an
 *                    expired session surfaces as `RainError.TokenExpired` and re-auth is the
 *                    host's job.
 * @param refreshExpirationSeconds TTL requested for refreshed sessions; null uses Turnkey's
 *                                 default (900 seconds).
 * @param maxTransientRetries Retries (beyond the first attempt) for transient failures on
 *                            idempotent reads.
 * @param initialRetryDelayMs First backoff delay; doubles per retry up to [maxRetryDelayMs].
 * @param maxRetryDelayMs Backoff ceiling.
 */
data class TurnkeySessionPolicy(
    val refreshBufferSeconds: Long = DEFAULT_REFRESH_BUFFER_SECONDS,
    val autoRefresh: Boolean = true,
    val refreshExpirationSeconds: String? = null,
    val maxTransientRetries: Int = DEFAULT_MAX_TRANSIENT_RETRIES,
    val initialRetryDelayMs: Long = DEFAULT_INITIAL_RETRY_DELAY_MS,
    val maxRetryDelayMs: Long = DEFAULT_MAX_RETRY_DELAY_MS,
) {
    init {
        require(refreshBufferSeconds >= 0) { "refreshBufferSeconds must be >= 0" }
        require(maxTransientRetries >= 0) { "maxTransientRetries must be >= 0" }
        require(initialRetryDelayMs >= 0) { "initialRetryDelayMs must be >= 0" }
        require(maxRetryDelayMs >= initialRetryDelayMs) {
            "maxRetryDelayMs must be >= initialRetryDelayMs"
        }
    }

    companion object {
        const val DEFAULT_REFRESH_BUFFER_SECONDS = 60L
        const val DEFAULT_MAX_TRANSIENT_RETRIES = 2
        const val DEFAULT_INITIAL_RETRY_DELAY_MS = 500L
        const val DEFAULT_MAX_RETRY_DELAY_MS = 4_000L
    }
}

/**
 * The Turnkey session as seen at the Rain SDK boundary. Observable via
 * [TurnkeyProvider.sessionState] so a host can react to a session dying without waiting for
 * a wallet call to fail.
 */
sealed class TurnkeySessionState {
    /** Turnkey is still restoring persisted sessions (app launch). */
    data object Loading : TurnkeySessionState()

    /** A session exists and its JWT has not expired. */
    data class Active(val expiresAtEpochSeconds: Double) : TurnkeySessionState()

    /** A session object is still present but its JWT expiry has passed. Re-authenticate. */
    data object Expired : TurnkeySessionState()

    /** No session (never logged in, logged out, or cleared by Turnkey's expiry timer). */
    data object Unauthenticated : TurnkeySessionState()
}
