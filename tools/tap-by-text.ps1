# Taps a UI element found by its text, instead of by hardcoded coordinates.
#
# Fixed coordinates break the moment the layout reflows, and the diagnostics screen reflows every
# time it appends a result card — so a tap recorded before one action lands somewhere else after it.
# That failure is silent: the tap succeeds, nothing happens, and the run looks like the feature is
# broken rather than the script.
#
# Usage:
#   .\tools\tap-by-text.ps1 -Match 'Load voice'
#   .\tools\tap-by-text.ps1 -Match 'Run MT spike' -ScrollFirst 2
#
# -Match is a regex tested against each node's text attribute. Fails loudly if it matches nothing
# or more than one node, because tapping "probably the right one" is how you get a confusing run.

param(
    [Parameter(Mandatory = $true)][string]$Match,
    [int]$ScrollFirst = 0,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

# NOT "Stop". adb writes transfer progress to stderr even on success, and under Stop that becomes
# a terminating NativeCommandError — the command worked and only the plumbing lied. Redirecting
# with `*> $null` does not help, because the error record is raised by the native-command wrapper
# rather than written to a stream. This script validates its own results below, so it does not need
# blanket Stop semantics.
$ErrorActionPreference = "Continue"
for ($i = 0; $i -lt $ScrollFirst; $i++) {
    & $Adb shell input swipe 1440 1400 1440 400 300 *> $null
    Start-Sleep -Seconds 2
}

& $Adb shell uiautomator dump /sdcard/ui.xml *> $null
$tmp = Join-Path $env:TEMP "bolmitra-ui-$PID.xml"
& $Adb pull /sdcard/ui.xml $tmp *> $null

$xml = [xml](Get-Content $tmp -Raw)
Remove-Item $tmp -ErrorAction SilentlyContinue

$hits = @($xml.SelectNodes("//node") | Where-Object { $_.text -match $Match })
if ($hits.Count -eq 0) {
    Write-Host "FAIL: no node matching '$Match'. Is it scrolled off screen?" -ForegroundColor Red
    exit 1
}
if ($hits.Count -gt 1) {
    Write-Host "FAIL: '$Match' matched $($hits.Count) nodes - be more specific:" -ForegroundColor Red
    $hits | ForEach-Object { Write-Host "  '$($_.text)' $($_.bounds)" }
    exit 1
}

# bounds looks like "[765,1223][926,1267]", so the separators are BOTH brackets and commas.
# Splitting on whitespace alone leaves "765,1223" as one token, and [int] on that yields a
# nonsense number instead of failing — which taps a coordinate off-screen and looks exactly like
# the button not working.
if ($hits[0].bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
    Write-Host "FAIL: cannot parse bounds '$($hits[0].bounds)'" -ForegroundColor Red
    exit 1
}
$x1, $y1, $x2, $y2 = [int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4]
$cx = [int](($x1 + $x2) / 2)
$cy = [int](($y1 + $y2) / 2)
Write-Host "tapping '$($hits[0].text)' at ($cx,$cy)" -ForegroundColor Green
& $Adb shell input tap $cx $cy *> $null
exit 0
