package dev.shadoe.delta.data.softap

import android.net.ITetheringConnector
import android.net.MacAddress
import android.net.wifi.IWifiManager
import android.net.wifi.SoftApConfiguration as NativeConfiguration
import android.net.wifi.SoftApConfigurationHidden
import android.os.Build
import dev.rikka.tools.refine.Refine
import dev.shadoe.delta.api.SoftApConfiguration
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
import kotlinx.coroutines.test.TestScope
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

  private fun TestScope.applyConfiguration(
    original: SoftApConfigurationHidden,
    accepted: Boolean = true,
    valid: Boolean = true,
    edit: (SoftApConfiguration) -> SoftApConfiguration,
  ): Triple<Boolean, SoftApConfigurationHidden?, SoftApStateStore> {
    val wifi = mockk<IWifiManager>()
    val tethering = mockk<ITetheringConnector>(relaxed = true)
    val submitted = slot<NativeConfiguration>()
    val store = SoftApStateStore()
    val initial = original.toBridgeClass(InternalState("test-only-fallback"))
    store.mConfig.value = initial
    every { wifi.softApConfiguration } returns Refine.unsafeCast(original)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      every { wifi.validateSoftApConfiguration(any()) } returns valid
    }
    every { wifi.setSoftApConfiguration(capture(submitted), any()) } returns
      accepted
    val controller = SoftApController(tethering, wifi, store, backgroundScope)
    val success = controller.updateSoftApConfiguration(edit(initial))
    if (!accepted || !valid) {
      assertEquals(initial, store.config.value)
      verify { tethering wasNot Called }
    }
    return Triple(
      success,
      if (submitted.isCaptured) Refine.unsafeCast(submitted.captured) else null,
      store,
    )
  }

  @Test
  fun `hidden only edit preserves the entire remaining native configuration`() =
    runTest {
      val original = nativeConfiguration()
      val (success, saved) = applyConfiguration(original) { it.copy(isHidden = true) }
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
      val (success, saved) = applyConfiguration(original) {
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
      val (success, saved) = applyConfiguration(original) {
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
    val (success, _) = applyConfiguration(nativeConfiguration(), accepted = false) {
      it.copy(isHidden = true)
    }
    assertFalse(success)
  }

  @Test
  @Config(sdk = [35, 36], manifest = Config.NONE)
  fun `validation failure does not submit a configuration`() = runTest {
    val (success, saved) = applyConfiguration(nativeConfiguration(), valid = false) {
      it.copy(isHidden = true)
    }
    assertFalse(success)
    assertEquals(null, saved)
  }
}
