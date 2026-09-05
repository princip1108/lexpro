param(
    [int]$Port = 8000,
    [string]$HostAddress = '127.0.0.1'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw 'The virtual environment is missing. Run .\setup.ps1 first.'
}

$required = @(
    @{ Name = 'DELTA model'; Path = (Join-Path $PSScriptRoot 'models\DELTA_CH\DELTA_CH') },
    @{ Name = 'Qwen model'; Path = (Join-Path $PSScriptRoot 'models\Qwen\Qwen3-14B') },
    @{ Name = 'MLP weights'; Path = (Join-Path $PSScriptRoot 'weights\focus_mlp.pt') }
)
foreach ($item in $required) {
    if (-not (Test-Path -LiteralPath $item.Path)) {
        throw "$($item.Name) is missing: $($item.Path). Configure the matching *_MODEL_PATH or MLP_WEIGHT_PATH in .env."
    }
}

& $python -c "from app.core.config import settings; print('database=' + settings.DATABASE_URL.Split('@')[-1]); print('device=' + settings.DELTA_DEVICE)"
& $python -m uvicorn app.main:app --host $HostAddress --port $Port
