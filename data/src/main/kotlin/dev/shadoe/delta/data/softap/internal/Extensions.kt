package dev.shadoe.delta.data.softap.internal

import android.annotation.SuppressLint
import android.net.wifi.SoftApConfigurationHidden
import android.net.wifi.WifiSsid
import android.os.Build
import dev.shadoe.delta.api.MacAddress
import dev.shadoe.delta.api.SoftApConfiguration
import dev.shadoe.delta.api.SoftApRandomizationSetting
import dev.shadoe.delta.api.SoftApSecurityType
import dev.shadoe.delta.api.SoftApSpeedType

internal object Extensions {
  infix fun Int.hasBit(other: Int): Boolean = (this and other) == other

  fun MacAddress.toOriginalClass() =
    android.net.MacAddress.fromString(macAddress)

  fun android.net.MacAddress.toBridgeClass() = MacAddress(toString())

  fun SoftApConfigurationHidden.toBridgeClass(state: InternalState) =
    SoftApConfiguration(
      ssid =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          wifiSsid?.bytes?.decodeToString()
        } else {
          @Suppress("DEPRECATION") ssid
        },
      passphrase = passphrase ?: state.fallbackPassphrase,
      securityType = @SuppressLint("WrongConstant") securityType,
      macRandomizationSetting =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          macRandomizationSetting
        } else {
          SoftApRandomizationSetting.RANDOMIZATION_NONE
        },
      isHidden = isHiddenSsid,
      speedType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          bands.max().run {
            when {
              this hasBit SoftApSpeedType.BAND_6GHZ -> {
                SoftApSpeedType.BAND_6GHZ
              }

              this hasBit SoftApSpeedType.BAND_5GHZ -> {
                SoftApSpeedType.BAND_5GHZ
              }

              this hasBit SoftApSpeedType.BAND_2GHZ -> {
                SoftApSpeedType.BAND_2GHZ
              }

              else -> {
                SoftApSpeedType.BAND_UNKNOWN
              }
            }
          }
        } else {
          @Suppress("DEPRECATION") band
        },
      blockedDevices = blockedClientList.map { it.toBridgeClass() },
      allowedClients = allowedClientList.map { it.toBridgeClass() },
      isAutoShutdownEnabled = isAutoShutdownEnabled,
      autoShutdownTimeout = shutdownTimeoutMillis,
      maxClientLimit = maxNumberOfClients,
      ieee80211axEnabled =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isIeee80211axEnabled else null,
    )

  fun SoftApConfiguration.toOriginalClass(
    original: SoftApConfigurationHidden
  ): SoftApConfigurationHidden {
    val current = original.toBridgeClass(InternalState())
    val requestedPassphrase =
      when (securityType) {
        SoftApSecurityType.SECURITY_TYPE_OPEN,
        SoftApSecurityType.SECURITY_TYPE_WPA3_OWE,
        SoftApSecurityType.SECURITY_TYPE_WPA3_OWE_TRANSITION -> null
        else -> passphrase
      }

    // Keep native options absent from Delta's model, including Wi-Fi 6/7.
    return SoftApConfigurationHidden.Builder(original)
      .apply {
        if (ssid != current.ssid) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setWifiSsid(
              ssid?.encodeToByteArray()?.let { WifiSsid.fromBytes(it) }
            )
          } else {
            @Suppress("DEPRECATION") setSsid(ssid)
          }
        }
        if (
          securityType != current.securityType ||
            requestedPassphrase != original.passphrase
        ) {
          setPassphrase(
            requestedPassphrase,
            @SuppressLint("WrongConstant") securityType,
          )
        }
        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            macRandomizationSetting != current.macRandomizationSetting
        ) {
          setMacRandomizationSetting(
            @SuppressLint("WrongConstant") macRandomizationSetting
          )
        }
        if (isHidden != current.isHidden) setHiddenSsid(isHidden)
        if (ieee80211axEnabled != null && ieee80211axEnabled != current.ieee80211axEnabled) {
          check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Wi-Fi 6 configuration requires Android 12 or later"
          }
          setIeee80211axEnabled(ieee80211axEnabled)
        }

        // The UI projects bands to one value. Reapplying that value would
        // discard fixed channels and the original single/bridged topology.
        if (speedType != current.speedType) {
          val band2To5 = SoftApSpeedType.BAND_2GHZ or SoftApSpeedType.BAND_5GHZ
          val band2To6 = band2To5 or SoftApSpeedType.BAND_6GHZ
          when (speedType) {
            SoftApSpeedType.BAND_6GHZ -> {
              setBand(@SuppressLint("WrongConstant") band2To6)
            }
            SoftApSpeedType.BAND_5GHZ -> {
              setBand(@SuppressLint("WrongConstant") band2To5)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // FIXME: check isBridgedApConcurrencySupported() on wifimanager
                setBands(intArrayOf(SoftApSpeedType.BAND_2GHZ, band2To5))
              }
            }
            SoftApSpeedType.BAND_2GHZ -> {
              setBand(@SuppressLint("WrongConstant") SoftApSpeedType.BAND_2GHZ)
            }
            else -> {}
          }
        }
        if (blockedDevices != current.blockedDevices) {
          setBlockedClientList(blockedDevices.map { it.toOriginalClass() })
        }
        if (allowedClients != current.allowedClients) {
          setAllowedClientList(allowedClients.map { it.toOriginalClass() })
        }
        if (isAutoShutdownEnabled != current.isAutoShutdownEnabled) {
          setAutoShutdownEnabled(isAutoShutdownEnabled)
        }
        if (autoShutdownTimeout != current.autoShutdownTimeout) {
          setShutdownTimeoutMillis(autoShutdownTimeout)
        }
        if (maxClientLimit != current.maxClientLimit) {
          setMaxNumberOfClients(maxClientLimit)
        }
      }
      .build()
  }
}
