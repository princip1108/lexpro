param(
    [switch]$UpgradePip
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        throw 'Python 3.11 is required but was not found on PATH.'
    }
    python -m venv .venv
}

if ($UpgradePip) {
    & $python -m pip install --upgrade pip
}
& $python -m pip install -r requirements.txt
Write-Host "Environment ready: $python"
