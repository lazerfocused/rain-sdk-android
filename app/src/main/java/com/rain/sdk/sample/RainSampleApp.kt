package com.rain.sdk.sample

import android.app.Application
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/** Owns the process-wide sample state: the SDK holder, the session store, and launch-time vendor init. */
class RainSampleApp : Application() {

    lateinit var store: SessionStore
        private set

    /** Lives here (not in a composable) so it survives Activity recreation. */
    val session = RainSession()

    /** Launch-time Turnkey/Privy init from the saved ids; resume joins it before checking the session. */
    var vendorInit: Job? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Timber.plant(Timber.DebugTree())
        }
        store = SessionStore(this)
        vendorInit = scope.launch {
            runCatching {
                when (store.provider) {
                    SessionStore.Provider.Turnkey ->
                        if (store.turnkeyOrgId.isNotBlank() && store.turnkeyAuthProxyConfigId.isNotBlank()) {
                            TurnkeyAuthSample.init(this@RainSampleApp, store.turnkeyOrgId, store.turnkeyAuthProxyConfigId)
                        }
                    SessionStore.Provider.Privy ->
                        if (store.privyAppId.isNotBlank() && store.privyAppClientId.isNotBlank()) {
                            PrivyAuthSample.init(this@RainSampleApp, store.privyAppId, store.privyAppClientId)
                        }
                    SessionStore.Provider.Portal, null -> Unit
                }
            }.onFailure { SampleLog.w("Launch", "vendor init failed: ${it.message}", it) }
        }
    }
}
