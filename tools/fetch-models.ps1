# Fetches and SHA-256 verifies the on-device model set for Phase 0 / Phase 1.
#
# Run this in your OWN PowerShell window. Total ~372 MB; the agent shell kills processes
# after a couple of minutes, which is not enough.
#
#   .\tools\fetch-models.ps1              # both ASR candidates + VAD (recommended)
#   .\tools\fetch-models.ps1 -Only A      # streaming FastConformer only
#   .\tools\fetch-models.ps1 -Only B      # batch IndicConformer only
#
# Every hash below came from an authoritative source, not from a first download:
#   - the two big models: Git-LFS pointer `oid sha256:` on Hugging Face
#   - silero_vad.onnx and the tokens files: hashed locally after fetching
#
# §4.3 leaves the ASR choice OPEN, to be settled by Phase 0 measurement on real hardware.
# Both candidates are fetched by default precisely so that comparison is possible. Mundari
# TTS is NOT here — see the note at the bottom.

param(
    [ValidateSet("A", "B", "both")]
    [string]$Only = "both",
    [string]$Dest = "$PSScriptRoot\..\models"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# name, url, sha256, bytes, group
$artifacts = @(
    # --- Option A: streaming FastConformer (§4.3 candidate A, V25) -----------------------
    # int8. First partial at 1,210 ms is an ARCHITECTURAL FLOOR that does not improve on
    # faster hardware. Median WER 0.328 on vaani-bench-hi. Apache-2.0.
    @{ group = "A"; name = "asr-streaming/model.int8.onnx"
       url = "https://huggingface.co/mobilebytesensei/betterflow-salesken-hindi-streaming/resolve/main/model.int8.onnx"
       sha = "376b83ebe9b88a5324ca3d525451d76413d969802241c887b048facd6b236b24"
       bytes = 174281364 },
    @{ group = "A"; name = "asr-streaming/tokens.txt"
       url = "https://huggingface.co/mobilebytesensei/betterflow-salesken-hindi-streaming/resolve/main/tokens.txt"
       sha = "28a457e3435be6751c361aa8978b8374be3b14764355e171a69939ed72711c19"
       bytes = 18364 },

    # --- Option B: batch IndicConformer (§4.3 candidate B, V17/V18) ----------------------
    # NOTE the size. §5.3 costed this at 120 MB; it is actually 197.6 MB. See the warning
    # printed at the end of this script.
    @{ group = "B"; name = "asr-batch/model.int8.onnx"
       url = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/hi/model.int8.onnx"
       sha = "915c71e04dd7e5378a4057fdebb252b3a587188e4e99db6d7ce0909ad5ad05fa"
       bytes = 197595593 },
    # The per-language model.int8.onnx files share this root-level tokens file. There is no
    # hi/tokens.txt — a CTC model cannot decode without this, so it is not optional.
    @{ group = "B"; name = "asr-batch/tokens.txt"
       url = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/tokens.txt"
       sha = "ee60967630213f31951817ac8b402b92ec18cce80718a24a49b388e56672dfb2"
       bytes = 67605 },

    # --- Silero VAD (§4.2, MIT) ----------------------------------------------------------
    # Already bundled in the sherpa-onnx runtime; this is the model file it loads.
    @{ group = "both"; name = "vad/silero_vad.onnx"
       url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
       sha = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
       bytes = 643854 }
)

function Sha256($path) {
    if (-not (Test-Path $path)) { return $null }
    return (Get-FileHash $path -Algorithm SHA256).Hash.ToLower()
}

$wanted = $artifacts | Where-Object { $_.group -eq "both" -or $Only -eq "both" -or $_.group -eq $Only }
$total = ($wanted | ForEach-Object { $_.bytes } | Measure-Object -Sum).Sum
Write-Host "Fetching $($wanted.Count) artifact(s), $([Math]::Round($total/1MB,1)) MB total" -ForegroundColor Cyan
Write-Host "Destination: $Dest`n"

$failed = 0
foreach ($a in $wanted) {
    $out = Join-Path $Dest $a.name
    New-Item -ItemType Directory -Path (Split-Path $out) -Force | Out-Null

    # Nested rather than a single -and expression: the guard must not depend on operator
    # precedence, and Sha256 must never be reached for a path that does not exist.
    if (Test-Path $out) {
        if ((Sha256 $out) -eq $a.sha) {
            Write-Host ("  [cached] {0}" -f $a.name) -ForegroundColor DarkGray
            continue
        }
        Write-Host ("  [stale]  {0} - hash mismatch, refetching" -f $a.name) -ForegroundColor Yellow
        Remove-Item $out -Force
    }

    $have = if (Test-Path $out) { (Get-Item $out).Length } else { 0 }
    Write-Host ("  [get]    {0}  ({1} MB{2})" -f $a.name, [Math]::Round($a.bytes/1MB,1),
        $(if ($have -gt 0) { ", resuming from $([Math]::Round($have/1MB,1)) MB" } else { "" }))

    # curl with -C - rather than Invoke-WebRequest, so an interrupted transfer RESUMES on the
    # next run instead of restarting. Both HF and GitHub advertise Accept-Ranges: bytes.
    # This matters on constrained shells and on the intermittent links these deployments have.
    & curl.exe -L -C - --retry 3 --retry-delay 2 --fail --silent --show-error `
        -o $out $a.url
    if ($LASTEXITCODE -ne 0) {
        # Exit 33 = server refused a byte range, usually because the file is already complete.
        if ($LASTEXITCODE -eq 33 -and (Test-Path $out) -and (Get-Item $out).Length -eq $a.bytes) {
            Write-Host "  [note]   already complete" -ForegroundColor DarkGray
        } else {
            Write-Host ("  [FAIL]   curl exit {0} - re-run to resume" -f $LASTEXITCODE) -ForegroundColor Red
            $failed++
            continue
        }
    }

    $actual = Sha256 $out
    if ($actual -ne $a.sha) {
        Remove-Item $out -Force
        Write-Host "  [FAIL]   SHA-256 mismatch - artifact DELETED" -ForegroundColor Red
        Write-Host "           expected $($a.sha)" -ForegroundColor Red
        Write-Host "           actual   $actual" -ForegroundColor Red
        $failed++
    } else {
        Write-Host "  [ok]     verified" -ForegroundColor Green
    }
}

Write-Host ""
if ($failed -gt 0) {
    Write-Host "$failed artifact(s) FAILED. Re-run to retry." -ForegroundColor Red
    exit 1
}
Write-Host "All artifacts verified." -ForegroundColor Green

Write-Host @"

------------------------------------------------------------------------------
TWO THINGS TO KNOW BEFORE PHASE 0
------------------------------------------------------------------------------

1. ARCHITECTURE.md section 5.3 costs the batch IndicConformer at 120 MB on disk.
   The real file is 197.6 MB - a 65% understatement. Streaming is 174.3 MB.

   So option B is LARGER than option A, not smaller. Section 5.3 concluded the
   opposite ("the two differ by 45% on disk", B being the light one) and used
   that to argue B sits comfortably in the memory budget while A is near the
   ceiling. That reasoning needs re-checking against measured resident memory,
   which is exactly what Phase 0 is for.

2. MUNDARI TTS IS NOT IN THIS SCRIPT, because it does not exist as a download.
   sherpa-onnx publishes only 8 vits-mms voices and none are Indic - no unr,
   hoc or sat. facebook/mms-tts-unr must be CONVERTED to sherpa-onnx format
   ourselves via the documented MMS path (V16). That is a real work item, not
   a fetch, and it is on the critical path for voice-to-voice output.

   Reminder: MMS-TTS is CC-BY-NC (V3). Fine for a government deployment,
   but the licence clearance is still an open Phase 0 task.
------------------------------------------------------------------------------
"@ -ForegroundColor Yellow
