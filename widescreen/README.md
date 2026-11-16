# Modified widescreen CarPlay: 50-pixel right margin

The aim was to keep an existing aftermarket widescreen CarPlay setup on an older PCM, while leaving 50 pixels unused at the right. This is not the fullscreen patch that previously left this unit at its startup logo.

## Existing override from the car

The locally downloaded `load-from-pcm.jar` contains one configuration class. Its SHA-256 is:

`0d0a233828db0612b3431b8eb970bce5840943138b6f66bd7816c0a50bfca15c`

Inspected values:

| Method | Base | Margin edit |
| --- | ---: | ---: |
| `getScreenResolutionX` | 1440 | unchanged |
| `getScreenOffsetX` (LHD) | 114 | unchanged |
| `getWindowResolutionX` | 1326 | 1276 |
| `getTMWindowResolutionInXAxis` | 1326 | 1276 |
| `getWindowResolutionY` | 480 | unchanged |
| `getScreenOffsetY` | 55 | unchanged |

This gives `1440 - 114 - 1276 = 50` on the examined LHD layout. The same arithmetic does not establish a 50-pixel right margin on RHD, whose offset is different.

`PatchWide.java` reconstructs the edit on that exact base, requires its hash, and checks both method constants. It preserves the remaining class behavior, including the original CarPlay resolution selector. It does not install the output. The downloaded base and generated output are intentionally excluded from Git.

Build with JDK 8 and ASM core/tree on the classpath:

```powershell
javac -cp $env:ASM_CP -d widescreen/classes widescreen/PatchWide.java
java -cp "widescreen/classes;$env:ASM_CP" PatchWide load-from-pcm.jar widescreen/load-gap50.jar
```

Back up the actual effective override before replacement. A file named `g24cp.jar` is not necessarily the class currently in use. Classpath inspection found the aftermarket `load.jar` on this unit; adding another JAR with the same class is not a reliable replacement strategy.

## Earlier SD-card candidate

The SD card also contained a modified M.I.B. `mod/java/g24widescreen.jar` based on askac's project. The recorded candidate hash was:

`31e5201d7f2cd0adb86d5540913c0d0ed44199c200bb9c9a71ce78470d2c7c9`

Its configuration source used the same 1276 width, but replacing the standard M.I.B. file did not by itself establish that the aftermarket override had been replaced. This candidate must not be confused with the downloaded `load.jar` base or with the audio-control JAR.

The included `reference/PGen2TMConfiguration.java` is that SD-card reference source, with the accompanying askac BSD notice. It is not a full decompilation of the user's aftermarket firmware. The patch generator above is the reproducible change for the inspected aftermarket base.

The session record reports an active modified widescreen installation. A fresh screenshot, touch-coordinate check, and readback of the current widescreen artifact were not performed as part of preparing this repository; no new device installation is performed by this publishing task.
