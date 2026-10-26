# Dot-source this file before Gradle: . .\tools\Use-Gms.ps1
# The key is encrypted for the current Windows user, outside Git.
$gmsSecretPath = Join-Path $PSScriptRoot '..\.local-data\gms\api-key.dpapi'
if (-not (Test-Path -LiteralPath $gmsSecretPath)) {
    throw 'Local GMS credential is missing.'
}
$gmsEncryptedValue = (Get-Content -LiteralPath $gmsSecretPath -Raw -ErrorAction Stop).Trim()
$gmsSecureValue = ConvertTo-SecureString -String $gmsEncryptedValue -ErrorAction Stop
$gmsCredential = [System.Net.NetworkCredential]::new('', $gmsSecureValue)
$env:OPENAI_API_KEY = $gmsCredential.Password
$env:OPENAI_BASE_URL = 'https://gms.ssafy.io/gmsapi/api.openai.com/v1'
Remove-Variable gmsSecureValue, gmsCredential, gmsSecretPath, gmsEncryptedValue
Write-Host 'GMS configured for this PowerShell session. Credential value hidden.'
