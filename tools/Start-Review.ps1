# Start a local-only review screen without opening a terminal window.
$reviewRoot = Split-Path $PSScriptRoot -Parent
$reviewPython = Join-Path $reviewRoot '.local-data\collector-venv\Scripts\python.exe'
$reviewScript = Join-Path $reviewRoot 'tools\public_complaints\review_server.py'
if (-not (Test-Path -LiteralPath $reviewPython)) { throw 'Local Python runtime is missing.' }
try {
    $reviewPage = Invoke-WebRequest 'http://127.0.0.1:8765/' -TimeoutSec 2
    if ($reviewPage.Content -notmatch '민원 검수') { throw 'Port 8765 belongs to a different service.' }
} catch {
    if ($_.Exception.Message -like '*different service*') { throw }
    Start-Process -FilePath $reviewPython -ArgumentList @('"' + $reviewScript + '"') -WorkingDirectory $reviewRoot -WindowStyle Hidden
    Start-Sleep -Seconds 1
}
Write-Host 'Review screen: http://127.0.0.1:8765'
Write-Host 'Answers: .local-data/acrc-human-review/responses.json'
