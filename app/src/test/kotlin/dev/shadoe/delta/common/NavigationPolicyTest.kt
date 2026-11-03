package dev.shadoe.delta.common

import dev.shadoe.delta.api.ShizukuStates
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationPolicyTest {
  @Test
  fun `missing stopped or denied Shizuku leads to useful limited mode`() {
    for (state in
      listOf(
        ShizukuStates.NOT_READY,
        ShizukuStates.NOT_AVAILABLE,
        ShizukuStates.NOT_RUNNING,
        ShizukuStates.NOT_CONNECTED,
      )) {
      assertEquals(Routes.LimitedScreen, selectStartRoute(false, state, false))
      assertEquals(Routes.LimitedScreen, selectStartRoute(false, state, true))
    }
  }

  @Test
  fun `first run keeps onboarding and connected users retain normal navigation`() {
    assertEquals(
      Routes.Setup.FirstUseScreen,
      selectStartRoute(true, ShizukuStates.NOT_RUNNING, false),
    )
    assertEquals(
      Routes.SoftApScreen,
      selectStartRoute(false, ShizukuStates.CONNECTED, false),
    )
    assertEquals(
      Routes.Setup.CrashHandlerSetupScreen,
      selectStartRoute(false, ShizukuStates.CONNECTED, true),
    )
  }
}
