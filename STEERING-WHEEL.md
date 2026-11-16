# Programmable steering-wheel key and CarPlay track skipping

## Reported behavior

On the examined P3645 unit, the steering-wheel programmable key was assigned to track skipping in PCM but did not skip CarPlay tracks. After installing the bridge, the owner explicitly confirmed that the issue was resolved. A subsequent double-click enhancement is documented below separately.

## Static findings

The firmware has a complete CarPlay skip implementation. The missing connection is more specific than a lack of CarPlay media-key support:

| Layer | Finding |
| --- | --- |
| `HMIApplication` | `VIRTUAL_BUTTON_JOKER1 = 13`; normal previous/next virtual buttons are 0/1 |
| `VirtualGUIManager.connectService()` | Registers each application's non-null virtual button models |
| `VirtualButtonHandler` | Delivers press/typed/release to registered models |
| Native media `HardKeyListener` | Registers model 200572 for the programmable key |
| Native media `HardKeyListener.keyReleased()` | Reads choice model 395; meaning 9 routes to next model 200575, meaning 14 to previous model 200576 |
| `AbstractTerminalModeHMIApplication.getVirtualButton()` | Handles regular previous/next keys and several other keys, but has no case 13; returns null for the programmable key |
| TerminalMode `HardKeyListener` | Registers 3200031/3200039 and 3200052/3200051; no programmable-key listener |
| `CarPlayDSIManager.skip(boolean, int)` | Already sends paired press/release events for the requested direction and count |

The last step uses an intermediate button enumeration: `skip(true, 1)` sends internal button 2 with states 1 then 2; `skip(false, 1)` uses internal button 3. `CarplayDSILifecycleController.CarplayDSIController` maps these to **DSICarplay button 9/10 and state 0/1**. Do not pass raw DSI values into the intermediate controller API.

The generic rotary/joystick key adapter is a separate path. Its lack of skip keys does not mean the dedicated CarPlay skip backend is absent.

## Implementation

Add a programmable-key subscription in the TerminalMode application and a narrow handler:

1. Register virtual key 13 without replacing existing native-media or car-settings listeners.
2. Read choice model 395 and act only for meanings 9 (next) or 14 (previous).
3. Require an active CarPlay session and the appropriate media-audio ownership; connection alone is insufficient. Preserve the existing standby/traffic-announcement exclusions.
4. Dispatch a single skip operation on the TerminalMode dispatcher, using the existing `CarPlayDSIManager.skip()` implementation.
5. Preserve other programmable-key assignments and the long-press settings action. Do not treat a held key as repeated skips.
6. Keep registration/de-registration symmetrical and avoid adding duplicate listeners across reconnects.

`steering-wheel/src/pcm/JokerBridge.java` implements this subscription. It supplies direct button models 3200999 and 3200998 for virtual keys 13 (short press) and 39 (long press). These are directly registered virtual button models, not additions to the firmware model bank. The long-press listener clears the pending short press: the existing global key manager suppresses the short release after a long press, so failing to clear it would leave stale state.

The handler captures assignment/session at press, sends at most one operation on release, and checks the same session, assignment, standby state, traffic-announcement exclusion, audio focus, and audible MEDIA connection again on the TerminalMode dispatcher. Other existing subscribers remain registered. Live testing is still required to confirm the actual vehicle's focus state and absence of side effects.

`PatchJoker.java` adds the HMI subscription and CarPlay activate/deactivate hooks to the existing audio JAR. It modifies that JAR's CarPlayDSIManager in place rather than adding a competing override in a separate JAR. A Java class-literal instruction in the converted HMI registration method is rewritten to `Class.forName` for the Foundation 1.1 target; both patched classes use classfile version 46.

## Installation record

- Combined package: 18 classes / 304 methods passed ASM verification.
- Target J9 `-verify` loaded JokerBridge, both anonymous helper classes, AbstractTerminalModeHMIApplication, and CarPlayDSIManager.
- Compared with the previous audio JAR, only the existing CarPlayDSIManager class changed; four class entries were added. Other audio/UI classes remained byte-identical.
- Installed package SHA-256: `7653fff2e6871b722284380cd5447447e139056a55eed1a646862c3fdf326855`.
- Installed under `/mnt/app/eso/hmi/lsd/jars/pcm-prompt-level.jar`; readback matched before reboot.
- Previous audio-only package retained as `pcm-prompt-level.jar.before-joker`.
- Reboot was requested. The owner unplugged the network adapter to reconnect the iPhone before post-reboot logs could be inspected, then confirmed that the track-skipping issue was resolved. This confirms the original short-press use case, not every edge case in the test matrix.

Build with `steering-wheel/build.ps1`, using the same Java/ASM environment as the audio build and a locally supplied verified baseline JAR. `steering-wheel/deploy.py install` requires the exact recorded audio baseline and refuses an existing recovery backup. `rollback` restores only the prior audio JAR; it leaves the native audio binary unchanged. Both require a subsequent reboot. Generated packages, readbacks, and credentials remain excluded from Git.

## Required validation

- Target J9 class-loading preflight before activation, as learned from the audio-control patch.
- Log the physical programmable-key event, selected meaning, CarPlay connection/audio ownership, and one dispatched skip to establish the live path.
- Test next and previous, short versus long press, non-skip assignments, CarPlay disconnect, native radio/media playback, navigation prompts, and calls.
- Verify one action per press and no native-media side effect while CarPlay owns playback.

The owner confirmed the original short-press fix during actual use. The additional scenarios above still need individual validation.

## Double-click enhancement

For the next-track assignment, a short click sends next after a 300 ms recognition window. A second press within that window followed by release sends previous directly, without first sending next. The previous-track assignment remains previous. Long press cancels pending clicks and preserves the original long-press action. A third click begins a new gesture.

`ClickGate` is a time-driven state machine tested independently of HMI services. Tests cover single/double clicks, a held second press canceled as a long press, repeated down/up events, stale timer callbacks, the exact 300 ms boundary, triple clicks, and cancellation. The bridge cancels pending actions on eligibility loss and rechecks session/assignment/audio state at dispatch.

- Combined package: 19 classes / 317 methods passed ASM verification.
- New/changed classes passed target J9 `-verify` preflight, including both anonymous listeners.
- Installed/readback-verified SHA-256: `47d65f0816cd378513ae8a4c9427a09b95c5bc603113b8cfdbc087be808bc1b3`.
- The confirmed single-click package is preserved as `pcm-prompt-level.jar.before-double-click`.
- `deploy.py install-double` requires the exact confirmed single-click artifact; `rollback-double` restores it. Reboot is required after either action.
- Reboot requested; physical double-click behavior remains pending owner confirmation.
