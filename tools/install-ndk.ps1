# Installs the NDK + CMake that Phase 0 needs.
#
# Run this in your OWN PowerShell window, not through the agent — the download is
# ~760 MB and takes 5-10 minutes, which exceeds the agent shell's process lifetime.
#
# Versions are deliberate:
#   ndk/28.2.13676358  = r28c. r28+ is MANDATORY (ARCHITECTURE.md §4.8.2, V33): 16 KB
#                        page alignment is the default from r28, and sherpa-onnx's
#                        prebuilt libonnxruntime4j_jni.so fails it. Also AGP 9's default.
#   cmake/3.31.6       = latest 3.x. NOT 4.x: CMake 4 dropped support for
#                        cmake_minimum_required(VERSION < 3.5), which breaks ONNX
#                        Runtime's transitive dependencies.

$env:JAVA_HOME   = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\Aaradhy\AppData\Local\Android\Sdk"
$acli = "$env:ANDROID_HOME\cmdline-tools\latest\bin\android.exe"

if (-not (Test-Path $acli)) {
    Write-Host "android CLI not found at $acli" -ForegroundColor Red
    exit 1
}

Write-Host "Installing NDK r28c and CMake 3.31.6 (~760 MB)..." -ForegroundColor Cyan
& $acli sdk install ndk/28.2.13676358 cmake/3.31.6

Write-Host "`n--- verifying ---" -ForegroundColor Cyan
$ndkOk   = Test-Path "$env:ANDROID_HOME\ndk\28.2.13676358"
$cmakeOk = Test-Path "$env:ANDROID_HOME\cmake\3.31.6"
Write-Host ("NDK r28c    : {0}" -f $(if ($ndkOk)   { "OK" } else { "MISSING" })) -ForegroundColor $(if ($ndkOk)   { "Green" } else { "Red" })
Write-Host ("CMake 3.31.6: {0}" -f $(if ($cmakeOk) { "OK" } else { "MISSING" })) -ForegroundColor $(if ($cmakeOk) { "Green" } else { "Red" })

if ($ndkOk -and $cmakeOk) {
    Write-Host "`nDone. Tell the agent to continue with Phase 0." -ForegroundColor Green
} else {
    Write-Host "`nIncomplete. Re-run this script - it resumes." -ForegroundColor Yellow
}
