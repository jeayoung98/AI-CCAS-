param([switch]$SaveCredential)

# Dot-source to configure this PowerShell session. DPAPI storage stays outside Git.
$lawCredentialPath = Join-Path $PSScriptRoot '..\.local-data\law-api\oc.dpapi'
try {
    if ($SaveCredential) {
        $lawSecureValue = Read-Host 'Approved LAW API authentication value (OC)' -AsSecureString
        if ($lawSecureValue.Length -eq 0) {
            throw 'Authentication value cannot be empty.'
        }
        $lawCredentialDirectory = Split-Path -Parent $lawCredentialPath
        New-Item -ItemType Directory -Path $lawCredentialDirectory -Force -ErrorAction Stop | Out-Null
        ConvertFrom-SecureString -SecureString $lawSecureValue -ErrorAction Stop |
            Set-Content -LiteralPath $lawCredentialPath -Encoding UTF8 -ErrorAction Stop
    }
    if (-not (Test-Path -LiteralPath $lawCredentialPath -PathType Leaf)) {
        throw 'LAW API credential is missing. After provider approval, dot-source with -SaveCredential.'
    }
    $lawEncryptedValue = (Get-Content -LiteralPath $lawCredentialPath -Raw -ErrorAction Stop).Trim()
    $lawSecureValue = ConvertTo-SecureString -String $lawEncryptedValue -ErrorAction Stop
    $lawCredential = [System.Net.NetworkCredential]::new('', $lawSecureValue)
    $env:LAW_API_OC = $lawCredential.Password
    Write-Host 'LAW API configured for this PowerShell session. Credential value hidden. Provider approval is separate.'
}
finally {
    Remove-Variable lawCredentialPath, lawCredentialDirectory, lawSecureValue, lawEncryptedValue, lawCredential -ErrorAction SilentlyContinue
}
