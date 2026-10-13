#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][string]$GitHubLogin
)
$ErrorActionPreference = 'Stop'
$signingDirectory = Join-Path $env:LOCALAPPDATA 'KalamSigning'
$config = Import-Clixml -LiteralPath (Join-Path $signingDirectory 'credentials.xml')
$values = @{
    ANDROID_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($config.KeyPath))
    ANDROID_KEYSTORE_PASSWORD = [Net.NetworkCredential]::new('', $config.StorePassword).Password
    ANDROID_KEY_ALIAS = $config.Alias
    ANDROID_KEY_PASSWORD = [Net.NetworkCredential]::new('', $config.KeyPassword).Password
    ANDROID_SIGNING_CERT_SHA256 = (Get-FileHash -LiteralPath (Join-Path $signingDirectory 'certificate.der') -Algorithm SHA256).Hash.ToLowerInvariant()
}
# Pass secrets through stdin, never command arguments, source files or tool output.
$values | ConvertTo-Json -Compress | python (Join-Path $PSScriptRoot 'configure_android_signing.py') --repository $Repository --login $GitHubLogin
if ($LASTEXITCODE -ne 0) { throw 'GitHub signing configuration failed. No private values were printed.' }
