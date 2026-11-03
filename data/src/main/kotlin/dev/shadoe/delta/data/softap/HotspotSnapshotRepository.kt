package dev.shadoe.delta.data.softap

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.shadoe.delta.api.SoftApConfiguration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
  private val value = MutableStateFlow<HotspotSnapshot?>(null)
  val snapshot = value.asStateFlow()
  fun record(config: SoftApConfiguration) {}
}
