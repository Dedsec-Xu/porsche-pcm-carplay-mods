# Programmable steering-wheel key and CarPlay track skipping

## Reported behavior

On the examined P3645 unit, the steering-wheel programmable key is assigned to track skipping in PCM but does not skip CarPlay tracks. This investigation used the existing local firmware extraction; no new track-key patch was installed.

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

## Proposed implementation

Add a programmable-key subscription in the TerminalMode application and a narrow handler:

1. Register virtual key 13 without replacing existing native-media or car-settings listeners.
2. Read choice model 395 and act only for meanings 9 (next) or 14 (previous).
3. Require an active CarPlay session and the appropriate media-audio ownership; connection alone is insufficient. Preserve the existing standby/traffic-announcement exclusions.
4. Dispatch a single skip operation on the TerminalMode dispatcher, using the existing `CarPlayDSIManager.skip()` implementation.
5. Preserve other programmable-key assignments and the long-press settings action. Do not treat a held key as repeated skips.
6. Keep registration/de-registration symmetrical and avoid adding duplicate listeners across reconnects.

This is preferable to injecting touch coordinates, changing the widescreen geometry, or assuming a firmware upgrade is required. The exact new model allocation and event timing remain implementation work.

## Required validation

- Target J9 class-loading preflight before activation, as learned from the audio-control patch.
- Log the physical programmable-key event, selected meaning, CarPlay connection/audio ownership, and one dispatched skip to establish the live path.
- Test next and previous, short versus long press, non-skip assignments, CarPlay disconnect, native radio/media playback, navigation prompts, and calls.
- Verify one action per press and no native-media side effect while CarPlay owns playback.

Static analysis establishes the missing subscription and the available backend. It does not yet prove that this is the only runtime condition affecting the user's button, or that the proposed patch has been validated in the car.
