param([ValidateSet(8080,8081)][int]$Port=8081,[switch]$StartAiAdapter)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$backendRoot=Join-Path $projectRoot 'backend/lexpro-backend'
[xml]$workspace=Get-Content -LiteralPath (Join-Path $backendRoot '.idea/workspace.xml')
$configuration=$workspace.SelectNodes('//configuration') | Where-Object { $_.SelectSingleNode('envs/env[@name="LEXPRO_JWT_SECRET"]') } | Select-Object -First 1
if(!$configuration){throw 'Existing local backend configuration was not found'}
foreach($entry in $configuration.SelectNodes('envs/env')){[Environment]::SetEnvironmentVariable($entry.name,$entry.value,'Process')}
$masterKey=[Environment]::GetEnvironmentVariable('LEXPRO_MODEL_CONFIG_MASTER_KEY','User')
if(!$masterKey){
    $bytes=New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $masterKey=[Convert]::ToBase64String($bytes)
    [Environment]::SetEnvironmentVariable('LEXPRO_MODEL_CONFIG_MASTER_KEY',$masterKey,'User')
}
$env:LEXPRO_MODEL_CONFIG_MASTER_KEY=$masterKey
$env:LEXPRO_MODEL_CONFIG_ENABLED='false'
$env:LEXPRO_MODEL_CONFIG_ALLOWED_URLS='http://127.0.0.1:8001/v1'
$env:LEXPRO_AI_ENABLED='true'
$env:LEXPRO_AI_BASE_URL='http://127.0.0.1:8001/v1'
$env:LEXPRO_AI_MODEL='LexPro_8B'
$env:LEXPRO_AI_API_KEY='EMPTY'
$env:LEXPRO_AI_ENABLE_THINKING='false'
$env:LEXPRO_AI_MAX_OUTPUT_TOKENS='1536'
$env:LEXPRO_AI_READ_TIMEOUT='PT180S'
$env:LEXPRO_FLYWAY_ENABLED='false'
$env:LEXPRO_BOOTSTRAP_ADMIN_ENABLED='false'
$env:LEXPRO_MCP_ENABLED='false'
$env:LEXPRO_AI_SERVICE_ENABLED='true'
$env:LEXPRO_AI_SERVICE_BASE_URL='http://127.0.0.1:8020'
if(!$env:LEXPRO_AI_SERVICE_INTERNAL_TOKEN){throw 'Existing AI adapter token is missing'}
$runtime=Join-Path $projectRoot '.runtime'
if(!(Test-Path -LiteralPath $runtime)){New-Item -ItemType Directory -Path $runtime | Out-Null}
$stamp=Get-Date -Format 'yyyyMMdd_HHmmss'
if($StartAiAdapter){
    $adapter=Start-Process -FilePath (Join-Path $projectRoot 'backend/retrieval-service/.venv/Scripts/python.exe') -ArgumentList '-m','uvicorn','app.main:app','--host','127.0.0.1','--port','8020' -WorkingDirectory (Join-Path $projectRoot 'backend/ai-service') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "adapter-$stamp.out.log") -RedirectStandardError (Join-Path $runtime "adapter-$stamp.err.log") -PassThru
    Write-Output "AI adapter PID: $($adapter.Id)"
}
$jar=Join-Path $backendRoot 'target/lexpro-backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Build the backend before starting acceptance'}
$runtimeJar=Join-Path $runtime "backend-$Port-$stamp.jar"
Copy-Item -LiteralPath $jar -Destination $runtimeJar
$jar=$runtimeJar
$backend=Start-Process -FilePath 'D:\jdk-21.0.5\bin\java.exe' -ArgumentList '-jar',$jar,"--server.port=$Port" -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "backend-$Port-$stamp.out.log") -RedirectStandardError (Join-Path $runtime "backend-$Port-$stamp.err.log") -PassThru
Write-Output "Backend PID: $($backend.Id); port: $Port"
