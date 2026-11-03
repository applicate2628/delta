# Wi-Fi 6 control

## Contract and decisions
Add a separate Wi-Fi 6 (IEEE 802.11ax) control, not another frequency-band option.
Read the native configuration; on Android 12+ use the platform AX flag. A null
model value means unavailable or leave unchanged (old presets stay compatible).
Enabling a previously disabled mode requires an affirmative Soft AP capability.
Reject edits on older APIs and mismatching system readback; never silently force
Wi-Fi 7, security, bands, channels, or hidden-network settings.

Keep the native-copy save path from PR #2. Do not infer hotspot capability from
station/client Wi-Fi support. Presets keep the existing radio setting; changing
their persistent schema is outside this feature. No new runtime dependencies or
permissions. English and Russian labels distinguish frequency from generation.

## Implementation and acceptance
1. Add model contracts and native-builder regression tests; observe failures.
2. Wire capability/configuration reads, guarded setter and readback validation.
3. Add a labeled state and switch with unsupported/loading/unknown explanations.
4. Verify regression tests, full unit tests, formatting and APK compilation.
5. Review the diff, then separately test actual UI and radio on vivo.

Unit tests do not prove firmware support, client negotiation, or reboot persistence.
