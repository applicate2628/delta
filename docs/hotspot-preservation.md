# Hotspot configuration preservation

This fork addresses saving Delta settings without resetting native hotspot
options that Delta does not expose. It does not add root access, bypass OEM
restrictions, or make Shizuku unnecessary when changing protected settings.

## Save contract

Read the current Android `SoftApConfiguration`, copy it with its native
builder, and update only supported fields whose requested values differ.
A hidden-SSID-only save must preserve the other configuration properties,
including 802.11ax/802.11be flags, exact channel assignments, credentials,
security mode, client admission control, and bridged-instance timeout policy.
Failure to read the current configuration must not fall back to defaults.
The system setter's rejection must be reported as failure, without updating
the displayed configuration or requesting a hotspot restart.

The frequency-band selector (2.4/5/6 GHz) is not a Wi-Fi-generation selector.
Wi-Fi 6 means IEEE 802.11ax, not the 6 GHz band. This change preserves existing
radio-generation settings; it does not force an unsupported mode on a device.
It also does not restore options already lost through an older Delta save.

## Automated checks

The regression suite exercises the real Android configuration builder in
Robolectric across API levels 30, 31, 33, 35, and 36. Only the system service
boundary is mocked. It compares complete resulting native configurations,
not merely the presence of a call to a particular builder method.

```sh
./gradlew :data:testDebugUnitTest --tests '*SoftApConfigurationPreservationTest'
./gradlew test
./gradlew :app:assembleDebugKeySignedDebug
```

The debug variant has application ID `dev.shadoe.delta.debug`, distinct from
the upstream release. Building it does not install it on a phone. Do not try
to replace an installed upstream release with an APK signed by another key.

## Device acceptance (not implied by unit tests)

1. In the native settings, select the desired hotspot security, frequency
   band, and Wi-Fi 6 mode. Do this before opening the modified Delta build.
2. Grant this build access to a running Shizuku instance. Change only Hidden
   SSID and save. A running hotspot may restart and disconnect its clients.
3. Read the current hotspot configuration again. Check both `HiddenSsid` and
   `Ieee80211axEnabled` in the same current configuration. Do not infer the
   result from arbitrary `dumpsys wifi` lines: that output also contains old
   configurations, connection histories, and local-only hotspot state.
4. On a second device, test manual connection using the exact network name,
   security type, and password; check actual internet access and negotiated
   Wi-Fi mode. A saved network name may remain visible in the client's UI.
5. Turn the hotspot off and on using only the native system switch. Repeat
   the current-configuration and client checks without saving in Delta.
6. Separately test after stopping Shizuku and after rebooting the phone. If
   the OEM overwrites settings, record that behavior; do not claim one-time
   configuration is sufficient without this check.

Do not publish full Wi-Fi dumps or real network names, passwords, addresses,
or client identifiers in bug reports. Record only the relevant boolean
flags, channel/band settings, build versions, and test outcomes.

A hidden SSID is not an access-control or encryption mechanism. Preserve a
secure security mode and password regardless of whether its name is hidden.
