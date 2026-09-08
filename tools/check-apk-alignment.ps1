# ARCHITECTURE.md section 4.8.7 CI gate: native library alignment + ORT uniqueness.
#
# Usage:
#   .\tools\check-apk-alignment.ps1
#   .\tools\check-apk-alignment.ps1 -Apk "app\build\outputs\apk\release\app-release.apk"
#
# Two assertions, deliberately of different severity:
#
#   LOAD p_align >= 0x4000   -> HARD FAIL. This is the actual 16 KB page requirement.
#   PT_GNU_RELRO end aligned -> WARN only. Measured 2026-09-03: AndroidX's own
#                               libandroidx.graphics.path.so also fails this, so it is the
#                               current state of the ecosystem rather than a defect in our
#                               dependency. It blocks a Play submission (V61) but this
#                               project sideloads (section 4.8.5). Reported so the accepted gap
#                               stays visible instead of being forgotten.
#
#   exactly one libonnxruntime.so  -> HARD FAIL if more. Guards against two *dynamic* ONNX
#                               Runtimes in the APK, which is the condition section 5.2
#                               Resolution 0 was written to avoid.
#
#                               REVISED 2026-09-08. The old test matched '^libonnxruntime.*\.so$',
#                               which also catches libonnxruntime4j_jni.so — so the moment the MT
#                               tier arrived, ONE runtime plus its JNI binding counted as two and
#                               the gate hard-failed on a correct build. The JNI shim is a ~100 KB
#                               binding, not a runtime. Only the core library is counted now.
#
#                               ACCEPTED, AND NOT WHAT THIS GATE MEASURES: there are now two
#                               copies of ORT *code* in the process — Microsoft's
#                               libonnxruntime.so for the MT tier, and the one statically linked
#                               inside libsherpa-onnx-jni.so for ASR and TTS. That is two builds
#                               of the same library loaded at once. It is safe only if sherpa's
#                               copy keeps its ORT symbols local; if it exports them, the dynamic
#                               linker may bind across the two. No file-level check can see this,
#                               so it is verified on device instead: ASR and TTS must still run
#                               after the MT dependency lands.

param(
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$Ndk = "C:\Users\Aaradhy\AppData\Local\Android\Sdk\ndk\28.2.13676358"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$readelf = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
foreach ($p in @($Apk, $readelf)) {
    if (-not (Test-Path $p)) { Write-Host "FAIL: not found - $p" -ForegroundColor Red; exit 1 }
}

$tmp = Join-Path $env:LOCALAPPDATA "bolmitra-so-check-$PID"
New-Item -ItemType Directory -Path $tmp -Force | Out-Null

try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
    $entries = $zip.Entries | Where-Object { $_.FullName -match '^lib/.*\.so$' }
    foreach ($e in $entries) {
        $dest = Join-Path $tmp ($e.FullName -replace '/', '_')
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
    }
    # Core runtime only. libonnxruntime4j_jni.so is the JNI binding that ships alongside it and
    # must not be counted as a second runtime.
    $ortCount = ($entries | Where-Object { $_.Name -eq 'libonnxruntime.so' }).Count
    $ortJniCount = ($entries | Where-Object { $_.Name -eq 'libonnxruntime4j_jni.so' }).Count
    $zip.Dispose()

    if ($entries.Count -eq 0) { Write-Host "FAIL: no .so found in $Apk" -ForegroundColor Red; exit 1 }

    $hardFail = 0
    $warn = 0

    Write-Host "APK: $Apk" -ForegroundColor Cyan
    Write-Host ("{0,-42} {1,-10} {2}" -f "LIBRARY", "LOAD", "RELRO END")
    Write-Host ("-" * 78)

    foreach ($so in Get-ChildItem $tmp -Filter *.so | Sort-Object Name) {
        $ph = & $readelf --program-headers $so.FullName 2>&1

        $aligns = ($ph | Select-String '^\s+LOAD') |
            ForEach-Object { ($_ -split '\s+')[-1] } | Sort-Object -Unique
        $loadOk = $true
        foreach ($a in $aligns) {
            if ([Convert]::ToUInt64($a, 16) -lt 16384) { $loadOk = $false }
        }

        $relroTxt = "none"
        $relroOk = $true
        $rel = $ph | Select-String 'GNU_RELRO'
        if ($rel) {
            $g = ($rel -split '\s+') | Where-Object { $_ -ne '' }
            $end = [Convert]::ToUInt64($g[2], 16) + [Convert]::ToUInt64($g[5], 16)
            $rem = $end % 16384
            $relroOk = ($rem -eq 0)
            $relroTxt = if ($relroOk) { "aligned" } else { "UNALIGNED (+$rem)" }
        }

        if (-not $loadOk) { $hardFail++ }
        if (-not $relroOk) { $warn++ }

        $colour = if (-not $loadOk) { "Red" } elseif (-not $relroOk) { "Yellow" } else { "Green" }
        Write-Host ("{0,-42} {1,-10} {2}" -f $so.Name, ($aligns -join ','), $relroTxt) -ForegroundColor $colour
    }

    Write-Host ("-" * 78)
    Write-Host "libonnxruntime.so count: $ortCount (1 expected once the MT tier is in; never >1)"
    Write-Host "libonnxruntime4j_jni.so count: $ortJniCount (JNI binding, not a runtime)"
    if ($ortCount -gt 1) {
        Write-Host "FAIL: duplicate ONNX Runtime - see ARCHITECTURE.md section 5.2" -ForegroundColor Red
        $hardFail++
    }
    if ($ortCount -eq 1) {
        Write-Host "NOTE: ORT for the MT tier is present. sherpa-onnx also has ORT statically" -ForegroundColor Cyan
        Write-Host "      linked inside libsherpa-onnx-jni.so, so two builds of ORT are loaded." -ForegroundColor Cyan
        Write-Host "      Confirm on device that ASR and TTS still run - no file check sees this." -ForegroundColor Cyan
    }

    if ($warn -gt 0) {
        Write-Host "`nWARN: $warn library(s) with unaligned PT_GNU_RELRO end." -ForegroundColor Yellow
        Write-Host "      Blocks a Play Store submission (V61). This project sideloads (section 4.8.5)," -ForegroundColor Yellow
        Write-Host "      and AndroidX itself has the same condition, so this is accepted, not solved." -ForegroundColor Yellow
    }

    if ($hardFail -gt 0) {
        Write-Host "`nRESULT: FAIL ($hardFail hard failure(s))" -ForegroundColor Red
        exit 1
    }
    Write-Host "`nRESULT: PASS - all LOAD segments are 16 KB aligned" -ForegroundColor Green
    exit 0
}
finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
