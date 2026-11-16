# Reverse-engineering and modifying my Porsche PCM

Notes, patch generators, and experimental code from reverse-engineering and modifying my own Porsche PCM head unit. The work covers an existing modified widescreen CarPlay installation and navigation-prompt music attenuation.

Target examined: **PCM 4.1 / MHI2 US POG24 P3645 / MU0935 / Bose**. This is a record of one unit, not a universal PCM firmware package.

## Changes

- **Widescreen CarPlay:** preserve the existing widescreen layout and reserve 50 pixels at the right edge. On the examined left-hand-drive layout, `1440 - 114 - 1276 = 50`. The effective override may be named `load.jar`, not the usual `g24cp.jar`.
- **Navigation ducking:** a native callback bypass provided a previously user-confirmed no-attenuation baseline. The newer Java experiment exposes **Sound → Volume → CarPlay Music Level**, opening the native circular/rotary popup, with levels 1–5 and level 5 requesting no reduction.
- **Compatibility fix:** the first audio JAR prevented CarPlay startup because one converted class had classfile version 49. The corrected version 46 loaded on the unit's J9 Foundation 1.1 VM.

## Current evidence

| Check | Result |
| --- | --- |
| Earlier native no-duck patch | User reported no reduction |
| First audio UI JAR | Failed CarPlay startup; rolled back |
| Corrected rotary JAR | Installed and read back with matching SHA-256 |
| Actual head-unit J9 class-loading preflight | All nine checked classes passed |
| Corrected reboot | No repeat of the AppTerminalMode class-loading failure; control and TerminalMode threads present |
| Rotary appearance, selection persistence, and iPhone playback | Not yet independently confirmed in the recorded session |

An `ok` acknowledging a progress message is not counted as a successful end-to-end playback test.

See [findings](FINDINGS.md), [build and deployment](BUILD.md), and [widescreen details](widescreen/README.md).

## Contents and provenance

Only patch logic, locally written support code, and curated findings are published. OEM JARs, JXE files, full decompilations, generated firmware derivatives, credentials, and device log archives are excluded. Supply your own matching inputs locally.

- [M.I.B.](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash): existing tooling and background. The SD-card preparation used V3.6.0, commit `754f654e879ab024dfc02d56b8d302a892463d4f`.
- [askac/pcm41_carplay_fullscreen](https://github.com/askac/pcm41_carplay_fullscreen): widescreen reference, commit `0a5372a93859da45b41a96178792243fefa6dbea`. Its BSD notice is retained under `widescreen/`.
- [luka-dev/jxe2jar](https://github.com/luka-dev/jxe2jar): conversion tool used at commit `367e99bee3c90e72de0de1edc1be0529258fd7f2`.
- ASM 9.7 and CFR 0.152 were used for bytecode editing, verification, and inspection; they are not bundled.

This repository is not affiliated with Porsche, Apple, or the upstream projects. It does not include a firmware upgrade or activation package.
