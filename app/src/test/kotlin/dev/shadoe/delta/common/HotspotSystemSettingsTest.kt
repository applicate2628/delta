package dev.shadoe.delta.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class HotspotSystemSettingsTest {
  @Test fun `direct hotspot settings are preferred when available`() {
    val context = mockk<Context>()
    val launched = mutableListOf<String?>()
    every { context.startActivity(any()) } answers { launched += firstArg<Intent>().action }
    assertTrue(openHotspotSystemSettings(context))
    assertEquals(listOf("com.android.settings.WIFI_TETHER_SETTINGS"), launched)
  }
  @Test fun `missing and protected OEM screens fall back to general settings`() {
    val context = mockk<Context>()
    val attempted = mutableListOf<String?>()
    every { context.startActivity(any()) } answers {
      val action = firstArg<Intent>().action
      attempted += action
      when(action) {
        "com.android.settings.WIFI_TETHER_SETTINGS" -> throw ActivityNotFoundException()
        Settings.ACTION_WIRELESS_SETTINGS -> throw SecurityException()
      }
    }
    assertTrue(openHotspotSystemSettings(context))
    assertEquals(listOf("com.android.settings.WIFI_TETHER_SETTINGS",
      Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS), attempted)
  }
  @Test fun `unavailable settings returns failure rather than claiming navigation`() {
    val context = mockk<Context>()
    every { context.startActivity(any()) } throws ActivityNotFoundException()
    assertFalse(openHotspotSystemSettings(context))
  }
}
