$ErrorActionPreference = "Stop"

$environmentFile = Join-Path $PSScriptRoot "local-env.ps1"
if (-not (Test-Path $environmentFile)) {
    throw "local-env.ps1이 없습니다. local-env.ps1.example을 복사한 뒤 값을 채워 주세요."
}

. $environmentFile
& (Join-Path $PSScriptRoot "gradlew.bat") bootRun
