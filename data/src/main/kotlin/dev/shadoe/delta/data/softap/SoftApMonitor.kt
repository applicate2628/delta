package dev.shadoe.delta.data.softap

import android.net.wifi.IStringListener
import android.net.wifi.IWifiManager
import android.net.wifi.SoftApConfigurationHidden
import android.os.Build
import dev.rikka.tools.refine.Refine
import dev.shadoe.delta.api.SoftApEnabledState
import dev.shadoe.delta.data.qualifiers.SoftApBackgroundTasksScope
import dev.shadoe.delta.data.qualifiers.WifiSystemService
import dev.shadoe.delta.data.softap.internal.Extensions.toBridgeClass
import dev.shadoe.delta.data.softap.internal.Utils.generateRandomPassword
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class SoftApMonitor
@Inject
constructor(
  @WifiSystemService private val wifiManager: IWifiManager,
  private val softApController: SoftApController,
  private val softApStateStore: SoftApStateStore,
  @SoftApBackgroundTasksScope scope: CoroutineScope,
  private val snapshots: HotspotSnapshotRepository,
) : AutoCloseable {
  private val monitorJob = SupervisorJob(scope.coroutineContext[Job])
  private val monitorScope = CoroutineScope(scope.coroutineContext + monitorJob)

  // TODO: replace this flow with lifecycle aware calls to
  //  [SoftApControlRepository.refresh()]
  private val updateConfigOnExternalChange =
    flow {
        var previous: SoftApConfigurationHidden? = null
        while (true) {
          val current = runCatching {
            wifiManager.softApConfiguration?.let { Refine.unsafeCast<SoftApConfigurationHidden>(it) }
          }.getOrNull()
          if (current != null && current != previous) {
            emit(current)
            previous = current
          }
          // Delay also on binder/read failure: never spin while Shizuku is unavailable.
          delay(1.seconds)
        }
      }
      .onEach {
        val config = it.toBridgeClass(state = softApStateStore.internalState.value)
        softApStateStore.mConfig.value = config
        snapshots.record(config)
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun resetSoftApState() =
    softApStateStore.status
      .mapLatest { it.enabledState == SoftApEnabledState.WIFI_AP_STATE_FAILED }
      .onEach {
        if (!it) return@onEach
        softApController.stopSoftAp()
      }

  init {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      wifiManager.queryLastConfiguredTetheredApPassphraseSinceBoot(
        object : IStringListener.Stub() {
          override fun onResult(value: String?) {
            softApStateStore.internalState.update {
              it.copy(fallbackPassphrase = value ?: generateRandomPassword())
            }
            softApStateStore.mConfig.update {
              it.copy(
                passphrase =
                  softApStateStore.internalState.value.fallbackPassphrase
              )
            }
          }
        }
      )
    }
    updateConfigOnExternalChange.launchIn(monitorScope)
    resetSoftApState().launchIn(monitorScope)
  }

  override fun close() {
    monitorJob.cancel()
  }
}
