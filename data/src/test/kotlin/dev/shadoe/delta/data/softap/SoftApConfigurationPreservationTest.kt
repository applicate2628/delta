package dev.shadoe.delta.data.softap

import android.net.ITetheringConnector
import android.net.MacAddress
import android.net.wifi.IWifiManager
import android.net.wifi.SoftApConfiguration as NativeConfiguration
import android.net.wifi.SoftApConfigurationHidden
import android.os.Build
import dev.rikka.tools.refine.Refine
import dev.shadoe.delta.api.SoftApConfiguration
import dev.shadoe.delta.api.SoftApEnabledState
import dev.shadoe.delta.api.SoftApSpeedType
import dev.shadoe.delta.data.softap.internal.Extensions.toBridgeClass
import dev.shadoe.delta.data.softap.internal.InternalState
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 35, 36], manifest = Config.NONE)
class SoftApConfigurationPreservationTest {
  @Suppress("DEPRECATION")
  private fun nativeConfiguration() =
    SoftApConfigurationHidden.Builder()
      .setSsid("delta-regression")
      .setPassphrase("test-only-password", 1)
      .setChannel(36, SoftApSpeedType.BAND_5GHZ)
      .setHiddenSsid(false)
      .setClientControlByUserEnabled(true)
      .setAutoShutdownEnabled(true)
      .setShutdownTimeoutMillis(60_000)
      .setMaxNumberOfClients(5)
      .setBssid(MacAddress.fromString("02:00:00:00:00:01"))
      .apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          setMacRandomizationSetting(0)
          setIeee80211axEnabled(true)
          setBridgedModeOpportunisticShutdownEnabled(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          setIeee80211beEnabled(true)
        }
      }
      .build()

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun TestScope.applyConfiguration(
    original: SoftApConfigurationHidden,
    accepted: Boolean = true,
    valid: Boolean = true,
    wifi6Support: Boolean? = true,
    readbackApplied: Boolean = true,
    edit: (SoftApConfiguration) -> SoftApConfiguration,
  ): Pair<Boolean, SoftApConfigurationHidden?> {
    val wifi = mockk<IWifiManager>()
    val tethering = mockk<ITetheringConnector>(relaxed = true)
    val submitted = slot<NativeConfiguration>()
    val store = SoftApStateStore()
    val initial = original.toBridgeClass(InternalState("test-only-fallback"))
    store.mConfig.value = initial
    store.mStatus.value = store.status.value.copy(
      capabilities = store.status.value.capabilities.copy(ieee80211axSupported = wifi6Support)
    )
    every { wifi.softApConfiguration } answers {
      if (accepted && readbackApplied && submitted.isCaptured) submitted.captured
      else Refine.unsafeCast(original)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      every { wifi.validateSoftApConfiguration(any()) } returns valid
    }
    every { wifi.setSoftApConfiguration(capture(submitted), any()) } returns
      accepted
    if (!accepted || !valid) {
      store.mStatus.value =
        store.status.value.copy(
          enabledState = SoftApEnabledState.WIFI_AP_STATE_ENABLED
        )
    }
    val controller = SoftApController(tethering, wifi, store, backgroundScope)
    val success = controller.updateSoftApConfiguration(edit(initial))
    if (!accepted || !valid) {
      runCurrent()
      assertEquals(initial, store.config.value)
      verify { tethering wasNot Called }
    }
    return Pair(
      success,
      if (submitted.isCaptured) Refine.unsafeCast(submitted.captured) else null,
    )
  }

  @Test
  fun `hidden only edit preserves the entire remaining native configuration`() =
    runTest {
      val original = nativeConfiguration()
      val (success, saved) =
        applyConfiguration(original) { it.copy(isHidden = true) }
      assertTrue(success)
      assertEquals(
        SoftApConfigurationHidden.Builder(original).setHiddenSsid(true).build(),
        saved,
      )
    }

  @Test
  fun `saving unchanged settings preserves native configuration exactly`() =
    runTest {
      val original = nativeConfiguration()
      val (success, saved) = applyConfiguration(original) { it }
      assertTrue(success)
      assertEquals(original, saved)
    }

  @Test
  fun `explicit band edit is still applied without resetting native flags`() =
    runTest {
      val original = nativeConfiguration()
      val (success, saved) =
        applyConfiguration(original) {
          it.copy(speedType = SoftApSpeedType.BAND_2GHZ)
        }
      assertTrue(success)
      assertEquals(
        SoftApConfigurationHidden.Builder(original)
          .setBand(SoftApSpeedType.BAND_2GHZ)
          .build(),
        saved,
      )
    }

  @Test
  fun `changing a password preserves radio and client control settings`() =
    runTest {
      val original = nativeConfiguration()
      val (success, saved) =
        applyConfiguration(original) {
          it.copy(passphrase = "another-test-password")
        }
      assertTrue(success)
      assertEquals(
        SoftApConfigurationHidden.Builder(original)
          .setPassphrase("another-test-password", 1)
          .build(),
        saved,
      )
    }

  @Test
  fun `system rejection is not reported as successful saving`() = runTest {
    val (success, _) =
      applyConfiguration(nativeConfiguration(), accepted = false) {
        it.copy(isHidden = true)
      }
    assertFalse(success)
  }

  @Test
  @Config(sdk = [35, 36], manifest = Config.NONE)
  fun `validation failure does not submit a configuration`() = runTest {
    val (success, saved) =
      applyConfiguration(nativeConfiguration(), valid = false) {
        it.copy(isHidden = true)
      }
    assertFalse(success)
    assertEquals(null, saved)
  }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `hidden edit does not force disabled radio generations on`() = runTest {
    val original =
      SoftApConfigurationHidden.Builder(nativeConfiguration())
        .setIeee80211axEnabled(false)
        .apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setIeee80211beEnabled(false)
          }
        }
        .build()
    val (success, saved) =
      applyConfiguration(original) { it.copy(isHidden = true) }
    assertTrue(success)
    assertEquals(
      SoftApConfigurationHidden.Builder(original).setHiddenSsid(true).build(),
      saved,
    )
  }

  @Test
  fun `hidden edit preserves a passwordless network without using fallback`() =
    runTest {
      val original =
        SoftApConfigurationHidden.Builder(nativeConfiguration())
          .setPassphrase(null, 0)
          .build()
      val (success, saved) =
        applyConfiguration(original) { it.copy(isHidden = true) }
      assertTrue(success)
      assertEquals(
        SoftApConfigurationHidden.Builder(original).setHiddenSsid(true).build(),
        saved,
      )
    }

  @Test
  fun `missing native configuration is rejected instead of using defaults`() =
    runTest {
      val wifi = mockk<IWifiManager>(relaxed = true)
      val tethering = mockk<ITetheringConnector>(relaxed = true)
      val store = SoftApStateStore()
      val initial = nativeConfiguration().toBridgeClass(InternalState())
      store.mConfig.value = initial
      every { wifi.softApConfiguration } returns null
      every { wifi.setSoftApConfiguration(any(), any()) } returns true
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        every { wifi.validateSoftApConfiguration(any()) } returns true
      }
      val controller = SoftApController(tethering, wifi, store, backgroundScope)
      assertFalse(
        controller.updateSoftApConfiguration(initial.copy(isHidden = true))
      )
      assertEquals(initial, store.config.value)
      verify(exactly = 0) { wifi.setSoftApConfiguration(any(), any()) }
      verify { tethering wasNot Called }
    }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `read actual Wi-Fi 6 configuration independently of frequency band`() = runTest {
    val original = nativeConfiguration()
    assertEquals(true, original.toBridgeClass(InternalState()).ieee80211axEnabled)
    val disabled = SoftApConfigurationHidden.Builder(original).setIeee80211axEnabled(false).build()
    assertEquals(false, disabled.toBridgeClass(InternalState()).ieee80211axEnabled)
  }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `turn Wi-Fi 6 off without changing hidden security channels or Wi-Fi 7`() = runTest {
    val original = SoftApConfigurationHidden.Builder(nativeConfiguration()).setHiddenSsid(true).build()
    val (success, saved) = applyConfiguration(original) { it.copy(ieee80211axEnabled = false) }
    assertTrue(success)
    assertEquals(SoftApConfigurationHidden.Builder(original).setIeee80211axEnabled(false).build(), saved)
  }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `turn Wi-Fi 6 on only when hotspot capability confirms support`() = runTest {
    val original = SoftApConfigurationHidden.Builder(nativeConfiguration()).setIeee80211axEnabled(false).build()
    val (success, saved) = applyConfiguration(original) { it.copy(ieee80211axEnabled = true) }
    assertTrue(success)
    assertEquals(SoftApConfigurationHidden.Builder(original).setIeee80211axEnabled(true).build(), saved)
  }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `unsupported or unknown Wi-Fi 6 enable never reaches system setter`() = runTest {
    val original = SoftApConfigurationHidden.Builder(nativeConfiguration()).setIeee80211axEnabled(false).build()
    for (support in listOf(false, null)) {
      val (success, saved) = applyConfiguration(original, wifi6Support = support) { it.copy(ieee80211axEnabled = true) }
      assertFalse(success)
      assertEquals(null, saved)
    }
  }

  @Test
  @Config(sdk = [31, 33, 35, 36], manifest = Config.NONE)
  fun `rejected radio readback is not reported as success`() = runTest {
    val (success, _) = applyConfiguration(nativeConfiguration(), readbackApplied = false) {
      it.copy(ieee80211axEnabled = false)
    }
    assertFalse(success)
  }

  @Test
  fun `null radio preference from old presets preserves native radio options`() = runTest {
    val original = nativeConfiguration()
    val (success, saved) = applyConfiguration(original) { it.copy(isHidden = true, ieee80211axEnabled = null) }
    assertTrue(success)
    assertEquals(SoftApConfigurationHidden.Builder(original).setHiddenSsid(true).build(), saved)
  }

  @Test
  @Config(sdk = [30], manifest = Config.NONE)
  fun `API 30 reports unknown radio flag and rejects explicit edits`() = runTest {
    val original = nativeConfiguration()
    assertEquals(null, original.toBridgeClass(InternalState()).ieee80211axEnabled)
    val (success, saved) = applyConfiguration(original) { it.copy(ieee80211axEnabled = false) }
    assertFalse(success)
    assertEquals(null, saved)
  }
}
