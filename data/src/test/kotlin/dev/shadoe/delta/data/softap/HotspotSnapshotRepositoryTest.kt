package dev.shadoe.delta.data.softap

import android.content.Context
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36], manifest = Config.NONE)
class HotspotSnapshotRepositoryTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  private val prefs
    get() =
      context.getSharedPreferences(
        "last_hotspot_snapshot",
        Context.MODE_PRIVATE,
      )

  @BeforeTest
  fun clear() {
    prefs.edit().clear().commit()
  }

  private fun config() =
    SoftApStateStore()
      .config
      .value
      .copy(
        ssid = "  exact-name",
        passphrase = "never-cache-this-password",
        isHidden = true,
        ieee80211axEnabled = true,
        securityType = 3,
        speedType = 2,
      )

  @Test
  fun `no observation means no invented hotspot configuration`() {
    assertNull(HotspotSnapshotRepository(context).snapshot.value)
  }

  @Test
  fun `last successful observation survives recreating the repository`() {
    val repository = HotspotSnapshotRepository(context)
    repository.record(config())
    val saved = assertNotNull(repository.snapshot.value)
    assertEquals("  exact-name", saved.ssid)
    assertEquals(true, saved.hidden)
    assertEquals(true, saved.wifi6)
    assertEquals(3, saved.securityType)
    assertEquals(2, saved.frequencyBand)
    assertTrue(saved.readAtMillis > 0)
    assertEquals(saved, HotspotSnapshotRepository(context).snapshot.value)
    assertTrue(
      prefs.all.values.none {
        it.toString().contains("never-cache-this-password")
      }
    )
    assertTrue(
      prefs.all.keys.none {
        it.contains("pass", true) || it.contains("client", true)
      }
    )
  }

  @Test
  fun `unknown Wi-Fi 6 is not silently cached as disabled`() {
    val repository = HotspotSnapshotRepository(context)
    repository.record(config())
    repository.record(config().copy(ieee80211axEnabled = null))
    assertNull(
      assertNotNull(HotspotSnapshotRepository(context).snapshot.value).wifi6
    )
  }

  @Test
  fun `corrupt or incomplete cache is unknown not a fabricated configuration`() {
    prefs.edit().putInt("version", 1).putLong("read_at", 1).commit()
    assertNull(HotspotSnapshotRepository(context).snapshot.value)
    prefs.edit().putString("version", "bad-type").commit()
    assertNull(HotspotSnapshotRepository(context).snapshot.value)
  }
}
