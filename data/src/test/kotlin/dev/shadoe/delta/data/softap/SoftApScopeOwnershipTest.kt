package dev.shadoe.delta.data.softap

import android.net.ITetheringConnector
import android.net.wifi.IWifiManager
import dev.shadoe.delta.data.MacAddressCacheRepository
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SoftApScopeOwnershipTest {
  @Test fun `closing the monitor leaves shared scope alive for reconnection`() = runTest {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
    try {
      val monitor = SoftApMonitor(mockk<IWifiManager>(relaxed = true),
        mockk<SoftApController>(relaxed = true), SoftApStateStore(), scope,
        mockk<HotspotSnapshotRepository>(relaxed = true))
      monitor.close()
      assertTrue(scope.isActive)
    } finally { scope.cancel() }
  }
  @Test fun `closing callbacks leaves shared scope alive for reconnection`() = runTest {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
    try {
      val listener = SoftApStateListener(mockk<ITetheringConnector>(relaxed = true),
        mockk<IWifiManager>(relaxed = true), mockk<MacAddressCacheRepository>(relaxed = true),
        SoftApStateStore(), scope)
      listener.close()
      assertTrue(scope.isActive)
    } finally { scope.cancel() }
  }
}
