package dev.shadoe.delta.data.softap

import android.net.IIntResultListener
import android.net.ITetheringConnector
import android.net.TetheringManagerHidden
import android.net.TetheringManagerHidden.TETHERING_WIFI
import android.net.wifi.IWifiManager
import android.net.wifi.SoftApConfigurationHidden
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import dev.rikka.tools.refine.Refine
import dev.shadoe.delta.api.SoftApConfiguration
import dev.shadoe.delta.api.SoftApEnabledState
import dev.shadoe.delta.data.qualifiers.SoftApBackgroundTasksScope
import dev.shadoe.delta.data.qualifiers.TetheringSystemService
import dev.shadoe.delta.data.qualifiers.WifiSystemService
import dev.shadoe.delta.data.softap.internal.Extensions.toBridgeClass
import dev.shadoe.delta.data.softap.internal.Extensions.toOriginalClass
import dev.shadoe.delta.data.softap.internal.Utils.ADB_PACKAGE_NAME
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SoftApController
@Inject
constructor(
  @TetheringSystemService private val tetheringConnector: ITetheringConnector,
  @WifiSystemService private val wifiManager: IWifiManager,
  private val softApStateStore: SoftApStateStore,
  @SoftApBackgroundTasksScope private val scope: CoroutineScope,
) {
  companion object {
    private const val TAG = "SoftApControlRepository"
  }

  private val dummyIntResultReceiver =
    object : IIntResultListener.Stub() {
      override fun onResult(resultCode: Int) {}
    }

  private fun setSoftApConfiguration(c: SoftApConfiguration): SoftApConfiguration? {
    val original = Refine.unsafeCast<SoftApConfigurationHidden>(
      checkNotNull(wifiManager.softApConfiguration) {
        "Current hotspot configuration is unavailable"
      }
    )
    val radioChanged = c.ieee80211axEnabled != null &&
      (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        c.ieee80211axEnabled != original.isIeee80211axEnabled)
    if (radioChanged &&
      (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (c.ieee80211axEnabled == true &&
          softApStateStore.status.value.capabilities.ieee80211axSupported != true))) {
      return null
    }
    val updated = Refine.unsafeCast<android.net.wifi.SoftApConfiguration>(
      c.toOriginalClass(original)
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
      !wifiManager.validateSoftApConfiguration(updated)) return null
    if (!wifiManager.setSoftApConfiguration(updated, ADB_PACKAGE_NAME)) return null
    val saved = wifiManager.softApConfiguration ?: return null
    val actual = Refine.unsafeCast<SoftApConfigurationHidden>(saved)
    if (radioChanged && actual.isIeee80211axEnabled != c.ieee80211axEnabled) return null
    return actual.toBridgeClass(state = softApStateStore.internalState.value)
  }

  // TODO: remove this workaround during rewrite
  fun startSoftAp(forceStart: Boolean = false): Boolean {
    val state = softApStateStore.status.value.enabledState
    if (!forceStart && state != SoftApEnabledState.WIFI_AP_STATE_DISABLED) {
      return false
    }
    val request =
      TetheringManagerHidden.TetheringRequest.Builder(TETHERING_WIFI).build()
    try {
      tetheringConnector.startTethering(
        request.parcel,
        ADB_PACKAGE_NAME,
        null,
        dummyIntResultReceiver,
      )
    } catch (_: NoSuchMethodException) {
      tetheringConnector.startTethering(
        request.parcel,
        ADB_PACKAGE_NAME,
        dummyIntResultReceiver,
      )
    } catch (_: NoSuchMethodException) {
      tetheringConnector.startTethering(
        TETHERING_WIFI,
        ResultReceiver(Handler(Looper.getMainLooper())),
        false,
        ADB_PACKAGE_NAME,
      )
    } catch (_: NoSuchMethodException) {
      tetheringConnector.startTethering(
        TETHERING_WIFI,
        ResultReceiver(Handler(Looper.getMainLooper())),
        false,
      )
    } catch (_: NoSuchMethodException) {
      return false
    }
    return true
  }

  // TODO: remove this workaround during rewrite
  fun stopSoftAp(forceStop: Boolean = false): Boolean {
    val state = softApStateStore.status.value.enabledState
    if (!forceStop && state != SoftApEnabledState.WIFI_AP_STATE_ENABLED) {
      return false
    }
    try {
      tetheringConnector.stopTethering(
        TETHERING_WIFI,
        ADB_PACKAGE_NAME,
        null,
        dummyIntResultReceiver,
      )
    } catch (_: NoSuchMethodException) {
      tetheringConnector.stopTethering(
        TETHERING_WIFI,
        ADB_PACKAGE_NAME,
        dummyIntResultReceiver,
      )
    } catch (_: NoSuchMethodException) {
      tetheringConnector.stopTethering(TETHERING_WIFI)
    } catch (_: NoSuchMethodException) {
      return false
    }
    return true
  }

  fun updateSoftApConfiguration(c: SoftApConfiguration): Boolean {
    val saved = runCatching { setSoftApConfiguration(c) }
      .onFailure { Log.e(TAG, it.stackTraceToString()) }
      .getOrNull() ?: return false
    // Publish readback rather than an optimistic UI copy (including null preset flags).
    softApStateStore.mConfig.update { saved }
    scope.launch {
      val enabled = SoftApEnabledState.WIFI_AP_STATE_ENABLED
      val disabled = SoftApEnabledState.WIFI_AP_STATE_DISABLED
      val status = softApStateStore.mStatus
      if (status.value.enabledState == enabled) {
        stopSoftAp()
        while (status.value.enabledState != disabled) delay(500.milliseconds)
        startSoftAp()
        while (status.value.enabledState != enabled) delay(500.milliseconds)
      }
    }
    return true
  }

  fun refreshConfiguration() {
    softApStateStore.mConfig.update {
      Refine.unsafeCast<SoftApConfigurationHidden>(
          wifiManager.softApConfiguration
        )
        .toBridgeClass(state = softApStateStore.internalState.value)
    }
  }
}
