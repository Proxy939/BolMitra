# Tap a coordinate, then print the resulting screen.
#
# The stale-file trap this avoids: `uiautomator dump` writes to a fixed path, and if the dump
# fails the previous file is still there, so `adb pull` hands back the OLD screen and the UI looks
# frozen. Deleting first turns that into a visible error instead of a wrong answer.
param(
    [int]$X = -1,
    [int]$Y = -1,
    [string]$Filter = ""
)

$adb = "C:\Users\Aaradhy\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if ($X -ge 0) {
    & $adb shell input tap $X $Y
    Start-Sleep -Milliseconds 1500
}

& $adb shell rm -f /sdcard/ui.xml
$out = & $adb shell uiautomator dump /sdcard/ui.xml 2>&1
if ($out -notmatch "dumped to") { Write-Error "dump failed: $out"; exit 1 }
& $adb pull /sdcard/ui.xml ui.xml 2>&1 | Out-Null
python tools\ui-dump.py ui.xml $Filter
