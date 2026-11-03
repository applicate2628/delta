package dev.shadoe.delta.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Open settings, never change Wi-Fi or debugging settings on behalf of the user. */
internal fun openHotspotSystemSettings(context: Context, isBigScreen: Boolean = false): Boolean {
  for (action in listOf("com.android.settings.WIFI_TETHER_SETTINGS",
    Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (isBigScreen) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    try {
      context.startActivity(intent)
      return true
    } catch (_: ActivityNotFoundException) {
      // OEMs may not expose the direct hotspot screen.
    } catch (_: SecurityException) {
      // Try the next public settings screen instead.
    }
  }
  return false
}
