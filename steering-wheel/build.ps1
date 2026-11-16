$ErrorActionPreference = 'Stop'
if (-not $env:JAVA8_HOME -or -not $env:ASM_CP) { throw 'Set JAVA8_HOME and ASM_CP.' }
$repo = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $env:JAVA8_HOME 'bin'
$stock = Join-Path $repo 'P3645-lsd.jar'
$baseline = Join-Path $PSScriptRoot 'before-joker.local.jar'
if (-not (Test-Path $baseline)) { throw 'Supply the verified audio-only JAR as steering-wheel/before-joker.local.jar.' }
if ((Get-FileHash $baseline).Hash.ToLower() -ne 'e0826c15189b46d8493567c3360bbd9785c28e93b614fa8e94a7e08c451afff6') { throw 'Baseline mismatch' }
$classes = Join-Path $PSScriptRoot 'classes'
$tests = Join-Path $PSScriptRoot 'test-classes'
New-Item -ItemType Directory -Force $classes, $tests | Out-Null
& "$jdk/javac.exe" -source 1.3 -target 1.2 -cp $stock -d $classes (Join-Path $PSScriptRoot 'src/pcm/JokerBridge.java')
if ($LASTEXITCODE) { throw 'Bridge compilation failed' }
& "$jdk/javac.exe" -cp $env:ASM_CP -d $tests (Join-Path $PSScriptRoot 'PatchJoker.java') (Join-Path $repo 'sound-control/VerifyPatch.java')
if ($LASTEXITCODE) { throw 'Tool compilation failed' }
$outputJar = Join-Path $PSScriptRoot 'pcm-audio-joker.jar'
& "$jdk/java.exe" -cp "$tests;$env:ASM_CP" PatchJoker $stock $baseline $classes $outputJar
if ($LASTEXITCODE) { throw 'Patch failed' }
& "$jdk/java.exe" -cp "$tests;$env:ASM_CP" VerifyPatch $outputJar
if ($LASTEXITCODE) { throw 'Verification failed' }
Get-FileHash $outputJar
