package dev.shadoe.delta.data.softap

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.shadoe.delta.api.SoftApConfiguration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Non-secret, last-observed settings, never evidence of current hotspot state. */
data class HotspotSnapshot(
  val ssid: String?,
  val hidden: Boolean,
  val wifi6: Boolean?,
  val securityType: Int,
  val frequencyBand: Int,
  val readAtMillis: Long,
)

@Singleton
class HotspotSnapshotRepository @Inject constructor(
  @ApplicationContext context: Context
) {
  private val prefs = context.getSharedPreferences("last_hotspot_snapshot", Context.MODE_PRIVATE)
  private val value = MutableStateFlow(readSnapshot())
  val snapshot = value.asStateFlow()

  private fun readSnapshot(): HotspotSnapshot? = runCatching {
    if (prefs.getInt("version", 0) != 1 || prefs.getLong("read_at", 0) <= 0 ||
      !prefs.contains("hidden") || !prefs.contains("security") || !prefs.contains("band")) return@runCatching null
    HotspotSnapshot(
      ssid = prefs.getString("ssid", null),
      hidden = prefs.getBoolean("hidden", false),
      wifi6 = if (prefs.contains("wifi6")) prefs.getBoolean("wifi6", false) else null,
      securityType = prefs.getInt("security", -1),
      frequencyBand = prefs.getInt("band", 0),
      readAtMillis = prefs.getLong("read_at", 0),
    )
  }.getOrNull()

  fun record(config: SoftApConfiguration) {
    val saved = HotspotSnapshot(config.ssid, config.isHidden, config.ieee80211axEnabled,
      config.securityType, config.speedType, System.currentTimeMillis())
    // Only this explicit allowlist is persisted: no passwords, addresses or clients.
    val editor = prefs.edit().putInt("version", 1)
      .putString("ssid", saved.ssid).putBoolean("hidden", saved.hidden)
      .putInt("security", saved.securityType).putInt("band", saved.frequencyBand)
      .putLong("read_at", saved.readAtMillis)
    saved.wifi6?.let { editor.putBoolean("wifi6", it) } ?: editor.remove("wifi6")
    editor.apply()
    value.value = saved
  }
}
