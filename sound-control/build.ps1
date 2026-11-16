$ErrorActionPreference = 'Stop'
if (-not $env:JAVA8_HOME -or -not $env:ASM_CP) { throw 'Set JAVA8_HOME and ASM_CP first.' }
$repo = Split-Path $PSScriptRoot -Parent
$javac = Join-Path $env:JAVA8_HOME 'bin/javac.exe'
$java = Join-Path $env:JAVA8_HOME 'bin/java.exe'
$inputJar = Join-Path $repo 'P3645-lsd.jar'
$classes = Join-Path $PSScriptRoot 'classes'
$tests = Join-Path $PSScriptRoot 'test-classes'
New-Item -ItemType Directory -Force $classes, $tests | Out-Null
python (Join-Path $PSScriptRoot 'build_native_popup.py')
if ($LASTEXITCODE) { throw 'Popup generation failed' }
$sources = (Get-ChildItem (Join-Path $PSScriptRoot 'src/pcm') -Filter '*.java').FullName
& $javac -source 1.3 -target 1.2 -cp $inputJar -d $classes $sources
if ($LASTEXITCODE) { throw 'Runtime compilation failed' }
& $javac -cp $env:ASM_CP -d $tests (Join-Path $PSScriptRoot 'PatchHmi.java') (Join-Path $PSScriptRoot 'VerifyPatch.java')
if ($LASTEXITCODE) { throw 'Patcher compilation failed' }
$outputJar = Join-Path $PSScriptRoot 'pcm-prompt-rotary.jar'
& $java -cp "$tests;$env:ASM_CP" PatchHmi $inputJar $classes $outputJar
if ($LASTEXITCODE) { throw 'Patching failed' }
& $java -cp "$tests;$env:ASM_CP" VerifyPatch $outputJar
if ($LASTEXITCODE) { throw 'Verification failed' }
$testSources = (Get-ChildItem (Join-Path $PSScriptRoot 'test') -Filter '*.java').FullName
& $javac -cp "$classes;$inputJar" -d $tests $testSources
if ($LASTEXITCODE) { throw 'Test compilation failed' }
& $java -cp "$tests;$classes;$inputJar" ControllerTest
if ($LASTEXITCODE) { throw 'Controller tests failed' }
& $java -cp "$tests;$classes;$inputJar" GainTest
if ($LASTEXITCODE) { throw 'Gain tests failed' }
Get-FileHash $outputJar -Algorithm SHA256
