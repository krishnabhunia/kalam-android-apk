#requires -Version 7.0
param(
    [ValidateSet('Generate', 'Existing', 'CopySecret', 'Fingerprint')]
    [string]$Mode = 'Fingerprint',
    [ValidateSet('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')]
    [string]$SecretName
)
$ErrorActionPreference = 'Stop'
$signingDirectory = Join-Path $env:LOCALAPPDATA 'KalamSigning'
$configPath = Join-Path $signingDirectory 'credentials.xml'
$keyTool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { (Get-Command keytool).Source }
function Plain([Security.SecureString]$secure) {
    [Net.NetworkCredential]::new('', $secure).Password
}
if ($Mode -in @('Generate', 'Existing')) {
    if (Test-Path -LiteralPath $configPath) { throw 'Signing is already configured locally. Do not replace the permanent key.' }
    New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
    if ($Mode -eq 'Generate') {
        $keyPath = Join-Path $signingDirectory 'kalam-release.p12'
        if (Test-Path -LiteralPath $keyPath) { throw 'The key file already exists. Use Existing mode to keep it.' }
        $keyAlias = 'kalam'
        $randomBytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
        $password = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
        $keyPassword = $password
        try {
            $env:KALAM_SETUP_STORE_PASSWORD = Plain $password
            $env:KALAM_SETUP_KEY_PASSWORD = Plain $keyPassword
            & $keyTool -genkeypair -keystore $keyPath -storetype PKCS12 -alias $keyAlias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Kalam Android' -storepass:env KALAM_SETUP_STORE_PASSWORD -keypass:env KALAM_SETUP_KEY_PASSWORD
            if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
        } finally {
            Remove-Item Env:KALAM_SETUP_STORE_PASSWORD -ErrorAction SilentlyContinue
            Remove-Item Env:KALAM_SETUP_KEY_PASSWORD -ErrorAction SilentlyContinue
        }
    } else {
        $keyPath = (Resolve-Path -LiteralPath (Read-Host 'Original keystore path')).Path
        $keyAlias = Read-Host 'Original key alias'
        $password = Read-Host 'Keystore password' -AsSecureString
        $keyPassword = Read-Host 'Key password' -AsSecureString
    }
    # Passwords are encrypted by Windows for the current user; nothing goes into Git.
    $config = @{ KeyPath = $keyPath; Alias = $keyAlias; StorePassword = $password; KeyPassword = $keyPassword }
}
if ($Mode -notin @('Generate', 'Existing')) {
    if (-not (Test-Path -LiteralPath $configPath)) { throw 'Use Generate once, or Existing to reuse the original keystore.' }
    $config = Import-Clixml -LiteralPath $configPath
}
if ($Mode -eq 'CopySecret') {
    if (-not $SecretName) { throw 'Specify -SecretName.' }
    $value = switch ($SecretName) {
        'ANDROID_KEYSTORE_BASE64' { [Convert]::ToBase64String([IO.File]::ReadAllBytes($config.KeyPath)) }
        'ANDROID_KEYSTORE_PASSWORD' { Plain $config.StorePassword }
        'ANDROID_KEY_ALIAS' { $config.Alias }
        'ANDROID_KEY_PASSWORD' { Plain $config.KeyPassword }
    }
    Set-Clipboard -Value $value
    Write-Host "$SecretName copied. Paste it into the matching GitHub repository Actions secret."
    exit
}
try {
    $env:KALAM_SETUP_STORE_PASSWORD = Plain $config.StorePassword
    $certificatePath = Join-Path $signingDirectory 'certificate.der'
    & $keyTool -exportcert -keystore $config.KeyPath -alias $config.Alias -storepass:env KALAM_SETUP_STORE_PASSWORD -file $certificatePath
    if ($LASTEXITCODE -ne 0) { throw 'The signing key could not be read.' }
    if ($Mode -in @('Generate', 'Existing')) { $config | Export-Clixml -LiteralPath $configPath }
    $fingerprint = (Get-FileHash -LiteralPath $certificatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "Permanent key: $($config.KeyPath)"
    Write-Host "ANDROID_SIGNING_CERT_SHA256: $fingerprint"
    Write-Host 'Back up the key and its passwords securely. Keep this same key for all future updates.'
} finally { Remove-Item Env:KALAM_SETUP_STORE_PASSWORD -ErrorAction SilentlyContinue }
