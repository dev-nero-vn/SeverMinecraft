param(
    [Parameter(Mandatory = $true)]
    [string]$Target
)

$ErrorActionPreference = "Stop"
$certificateBase64 = $env:WINDOWS_SIGNING_CERTIFICATE_BASE64
$certificatePassword = $env:WINDOWS_SIGNING_CERTIFICATE_PASSWORD

if ([string]::IsNullOrWhiteSpace($certificateBase64) -or
    [string]::IsNullOrWhiteSpace($certificatePassword)) {
    Write-Host "::warning::Tirror EXE has LEHAITRIEU version metadata but is NOT digitally signed. Configure the two Windows code-signing secrets with a trusted certificate to enable Authenticode signing."
    exit 0
}

if (-not (Test-Path -LiteralPath $Target)) {
    throw "EXE not found: $Target"
}

$pfxPath = Join-Path $env:RUNNER_TEMP "tirror-code-signing.pfx"
try {
    [IO.File]::WriteAllBytes($pfxPath, [Convert]::FromBase64String($certificateBase64))

    $sdkRoot = Join-Path ([Environment]::GetEnvironmentVariable('ProgramFiles(x86)')) "Windows Kits\10\bin"
    $signTool = Get-ChildItem -Path $sdkRoot -Filter signtool.exe -Recurse -ErrorAction Stop |
        Where-Object { $_.FullName -match "\\x64\\signtool\.exe$" } |
        Sort-Object FullName -Descending |
        Select-Object -First 1

    if ($null -eq $signTool) {
        throw "Windows SDK SignTool (x64) was not found on this runner."
    }

    & $signTool.FullName sign /fd SHA256 /td SHA256 /tr "https://timestamp.digicert.com" /f $pfxPath /p $certificatePassword $Target
    if ($LASTEXITCODE -ne 0) {
        throw "SignTool signing failed with exit code $LASTEXITCODE."
    }

    & $signTool.FullName verify /pa /all /v $Target
    if ($LASTEXITCODE -ne 0) {
        throw "SignTool signature verification failed with exit code $LASTEXITCODE."
    }

    Write-Host "Tirror EXE Authenticode signature verification passed."
}
finally {
    if (Test-Path -LiteralPath $pfxPath) {
        Remove-Item -LiteralPath $pfxPath -Force
    }
}
