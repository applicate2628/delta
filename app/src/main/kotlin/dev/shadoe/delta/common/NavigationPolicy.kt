package dev.shadoe.delta.common

import dev.shadoe.delta.api.ShizukuStates

internal fun selectStartRoute(
  firstRun: Boolean,
  shizukuState: Int,
  needsNotificationPermission: Boolean,
): Route = when {
  firstRun -> Routes.Setup.FirstUseScreen
  shizukuState != ShizukuStates.CONNECTED -> Routes.LimitedScreen
  needsNotificationPermission -> Routes.Setup.CrashHandlerSetupScreen
  else -> Routes.SoftApScreen
}
