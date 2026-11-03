# Limited mode without Shizuku

## Decision and scope
Do not impersonate a privileged service or pretend protected edits work offline.
An absent, stopped or unauthorized Shizuku opens a useful limited screen, with
system hotspot settings, a Shizuku setup action and a timestamped last-read
snapshot. First launch offers a visible path without Shizuku. Returning from
setup without granting access must not trap the user. Binder reconnection must
restore the ordinary controls without restarting the app.

Persist only SSID, hidden flag, Wi-Fi 6 flag, security type, frequency-band label
and observation time in private app preferences. Never cache credentials,
clients or fabricate an initial network. The view explicitly says last-read,
not current, and shows an empty state when nothing has been observed.

Alternatives: leaving the blocking setup screen fails the requested task;
a root/ADB replacement does not remove the privilege requirement. Use native
settings navigation with safe fallback instead. No new runtime dependencies,
permissions, automatically enabled debugging, network changes or installs.

## Plan and acceptance
1. Reproduce route blocking, missing last-read state and cancelled shared scopes.
2. Keep shared lifecycle scopes alive; cancel only owned jobs on disconnect.
3. Persist successful observations, build the limited screen and setup exits.
4. Test disconnected startup, unknown snapshot, password exclusion, restoration,
   shared-scope reuse, and connected navigation; compile the APK and review diff.
5. Separately check the real device screen, native-settings navigation and service
   reconnection; unit tests do not establish OEM navigation or hotspot persistence.
