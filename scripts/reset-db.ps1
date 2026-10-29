# 重置数据库并导入演示数据
# 用法: pwsh -File scripts\reset-db.ps1 [-User root] [-Password 123456]
param(
    [string]$User = 'root',
    [string]$Password = '123456'
)
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$dbDir = Join-Path $proj 'db'
$env:MYSQL_PWD = $Password

foreach ($script in @('schema.sql', 'data.sql')) {
    Write-Host "=== 执行 $script ===" -ForegroundColor Cyan
    Push-Location $dbDir
    try {
        & mysql -u $User -h 127.0.0.1 --default-character-set=utf8mb4 -e "source $script"
        if ($LASTEXITCODE -ne 0) { throw "$script 执行失败" }
    } finally { Pop-Location }
}
$env:MYSQL_PWD = $null
Write-Host '数据库 school_ms 已重置为初始演示数据。' -ForegroundColor Green
