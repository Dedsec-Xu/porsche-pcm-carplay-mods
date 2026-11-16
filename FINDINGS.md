# Findings from this head unit

## Widescreen is not fullscreen

The unit already had an aftermarket widescreen modification installed through an earlier firmware installation. A separate fullscreen attempt caused a startup-logo problem. Do not infer that a firmware update will make the fullscreen patch compatible.

The existing override was found as `load.jar`, containing `de/audi/app/terminalmode/pgen2/PGen2TMConfiguration.class`. The downloaded base reports a 1440×540 screen, horizontal origin 114 for left-hand drive, vertical origin 55, and a 1326×480 window. Both `getWindowResolutionX()` and `getTMWindowResolutionInXAxis()` return 1326. The right-margin change sets both to 1276. Other methods, including the CarPlay resolution selector, are preserved.

Changing only the standard M.I.B. widescreen JAR can have no visible effect when an existing override supplies the same class. Inspect effective classpath order and loaded JARs before replacing anything. See `widescreen/README.md` for the distinction between the SD-card candidate and the existing installation.

## CarPlay music attenuation

The examined callback carries **duration first, volume second**. Earlier experiments that changed constants interpreted as a gain were actually changing the duration. Those experiments are superseded, not proof of a volume change.

The Java path is `CarPlayDSIManager.duckAudio(int durationMs, double volume)` → `SmartphoneManager`. In the inspected implementation, the latter does not apply the supplied volume as a multiplier; it requests audio lowering using the duration. `AudioStreamRampVolume` in the examined AirPlay library is a stub. These observations do not establish that Apple alone determines the actual attenuation.

Relevant local connection IDs:

| Connection | ID |
| --- | ---: |
| CarPlay announcement | 154 |
| CarPlay lowering | 155 |
| CarPlay media | 162 |

The RCC audio table is gzip-compressed despite its `.xml` filename. The inspected Bose profile has a persistent `VOLUME_ATTENUATION_DIO` setting with four keys (0, 2, 3, 4), and a separate native navigation setting. Four existing presets are not automatically five distinct adjustable levels.

The experimental runtime instead queries the live baseline and range for media connection 162 and calls `DSISoundHandler.setInputGainOffSet`. Its mapping is:

```
effectiveLinearGain = requestedVolume + (1 - requestedVolume) * (level - 1) / 4
target = baseline + round(20 * log10(effectiveLinearGain))
```

The result is bounded by the reported minimum and baseline. Level 5 is unity gain, not a gain boost. This is interpolation toward unity, not a fixed multiplier on arbitrary CarPlay requests. The interpretation of input-gain units as dB and the audible result across all five levels still require playback verification. Hardware bounds can make different levels produce the same gain.

The code restores the baseline on unduck/disconnect, and has a 120-second missing-unduck watchdog. It persists the level at `/mnt/ols/pcm-custom/carplay-prompt-level`. It currently applies gain immediately rather than reproducing the requested fade duration. The baseline is captured once per sound registration; independent changes to the same input gain are not tracked continuously.

## Native no-duck baseline

For the exact original `dio_manager` SHA-256 below, the ARM callback at virtual address `0x00157818` was changed from `f0 43 2d e9` to `1e ff 2f e1` (return immediately). The generator checks the input hash, maps the address through ELF load segments, and checks the original bytes.

- Original: `6c2e5e17aa0ff6cc85bb903509ef168dc0b19b47000daecb441ce814cc861cf3`
- No-duck output: `6afd68fdb39ae04fc5055585338851fe6e0b0808a379aa86e5b3b5eb00690432`

The Java approach restores the original native binary so duck callbacks reach Java. Leaving the native bypass enabled would prevent the adjustable Java backend from receiving those callbacks. Keep the two approaches distinct.

## Why the first audio patch broke CarPlay

The device error archive reported:

```
Start of bundle AppTerminalMode failed due to undefined class
ClassNotFoundException: de.audi.app.terminalmode.smartphone.carplay.CarPlayDSIManager
```

The converter inferred classfile version 49 for this class; the other patched classes were version 46. Desktop Java 8 loading and ASM verification passed, but the target uses IBM J9 2.3, J2ME Foundation Specification v1.1. Those desktop checks did not establish target compatibility.

A separate process on the actual unit reproduced the old JAR's failure without starting another HMI instance. Emitting version 46 made the class load successfully. The patcher rejects class-literal LDC and invokedynamic instructions before lowering the version; this check is specific to these examined classes, not a general Java backporter.

The corrected package uses the native menu and rotary partial-popup classes, with popup ID 1000140 and range model ID 1000162. The first direct horizontal-slider layout was replaced in response to the intended native interaction. Popup/model IDs are specific to the inspected firmware and must be checked for collisions on other versions.

## Validation and recovery record

- Controller tests cover bounds, persisted values, failed saves, and bypass selection.
- Gain tests cover 505 level/request combinations plus invalid requests and bounds.
- Corrected package: 14 classes and 266 methods passed ASM analysis.
- Nine key classes passed a separate target J9 `-verify` loading check.
- Corrected installed JAR SHA-256: `e0826c15189b46d8493567c3360bbd9785c28e93b614fa8e94a7e08c451afff6`.
- The corrected HMI reboot showed `PCM-PromptRestore` and TerminalMode dispatcher threads without the earlier bundle exception.
- Actual rotary interaction and full CarPlay navigation/audio behavior remain pending explicit verification.

The first bad package was disabled, the previously working native no-duck backup restored, and the unit rebooted. The user confirmed the old version worked. Backups were retained for the corrected installation. A matching readback hash proves transferred bytes, not functional correctness.

## USB/network observations

The network connection used a USB Ethernet adapter and a private LAN. Device logs identified an ASIX 88772b interface and loaded `devnp-asix.so`. A product label or hardware revision alone is insufficient to infer support. The adapter occupied the port needed for the iPhone, so network inspection and CarPlay playback testing had to be performed sequentially.

No USB EEPROM/PID rewrite utility or claim that every D-Link revision is compatible is included here.

## Instrument-cluster CarPlay video (2026-11-16)

The owner explicitly confirmed dual-screen playback after the Porsche-specific adaptation of the Audi B9 Harman MHI2 second-screen approach. A complete session decoded 5,209 frames with zero reported decode errors. The display lifecycle was also exercised independently with synthetic video while main-screen CarPlay remained active.

Unplugging exposed a separate problem: teardown began, but the expected phone-gone marker was absent when the process exited. The supervisor classified that as abnormal and made later launches fall back to original CarPlay. Its correction now also recognizes a newly logged whole-session teardown. The correction is installed and covered by tests; repeated real-phone hot-plug recovery and the interrupted teardown's root cause remain open.

See [the full comparison, ABI findings, validation limits, and recovery record](CLUSTER-NAVIGATION.md). The initial complete-restart success must not be presented as proof that every warm-start or reconnect path works.
