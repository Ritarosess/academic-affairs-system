# 一键构建：前端构建 -> 产物并入后端静态目录 -> 后端打包
# 用法: & scripts\build.ps1
#
# 注意：本脚本大量调用外部命令（npm / mvn），必须用 Continue 而不是 Stop ——
# 在 Stop 下外部命令写入 stderr 会被当成终止性错误直接中断脚本，
# 导致下面的"构建失败则回退"逻辑根本没有机会执行。
# 因此这里统一改为 Continue，并逐步显式检查 $LASTEXITCODE。
$ErrorActionPreference = 'Continue'
$proj = Split-Path $PSScriptRoot -Parent
$mvn = Join-Path $proj 'tools\apache-maven-3.9.9\bin\mvn.cmd'
$settings = Join-Path $proj 'tools\maven-settings.xml'

if (-not (Test-Path $mvn)) { throw "未找到项目内置 Maven: $mvn" }

# Maven 与 java 都需要 Java 17+；PATH 上的 java 可能是 JDK 8，这里显式解析并设置 JAVA_HOME
. (Join-Path $PSScriptRoot '_jdk.ps1')
$jdkHome = Resolve-JdkHome
$env:JAVA_HOME = $jdkHome
Write-Host ("JDK    : {0}" -f $jdkHome) -ForegroundColor DarkGray

Write-Host '=== [1/3] 构建前端 ===' -ForegroundColor Cyan
Push-Location (Join-Path $proj 'frontend')
try {
    if (-not (Test-Path 'node_modules')) {
        $env:npm_config_cache = Join-Path $proj '.cache\npm'
        npm install
        if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) {
        # 受限沙箱禁止 Node 派生子进程，esbuild 无法启动，标准构建会失败。
        # 此时回退到绕开 esbuild 的沙箱配置（代价：CSS 不压缩，功能不受影响）。
        Write-Host '标准构建失败，回退到沙箱兼容构建（npm run build:sandbox）...' -ForegroundColor Yellow
        npm run build:sandbox
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    }
} finally { Pop-Location }

Write-Host '=== [2/3] 将前端产物并入后端静态目录 ===' -ForegroundColor Cyan
$staticDir = Join-Path $proj 'backend\src\main\resources\static'
if (Test-Path $staticDir) { Remove-Item $staticDir -Recurse -Force }
Copy-Item (Join-Path $proj 'frontend\dist') $staticDir -Recurse
Write-Host "已复制到 $staticDir"

Write-Host '=== [3/3] 打包后端 ===' -ForegroundColor Cyan
Push-Location (Join-Path $proj 'backend')
try {
    & $mvn -B -s $settings clean package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw '后端打包失败' }
} finally { Pop-Location }

Write-Host ''
Write-Host '构建完成。启动命令:' -ForegroundColor Green
Write-Host "  & scripts\start-backend.ps1"
Write-Host "  （或： `"$jdkHome\bin\java.exe`" -jar `"$(Join-Path $proj 'backend\target\edu-system.jar')`"）"
Write-Host '浏览器访问 http://localhost:8080'
