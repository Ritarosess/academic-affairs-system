# 启动后端服务（默认 8080 端口）
#
# ── 用法 ────────────────────────────────────────────────────────────────────
# 1) 模拟发送模式（默认，不连网络，邮件正文打到日志）
#      pwsh -File scripts\start-backend.ps1
#
# 2) 真实发送 —— QQ 邮箱（端口 465 / SSL）
#      pwsh -File scripts\start-backend.ps1 -MailHost smtp.qq.com -MailPort 465 -MailSsl `
#           -MailUser 你的QQ号@qq.com -MailPassword 你的SMTP授权码
#
# 3) 真实发送 —— 163 邮箱（端口 465 / SSL）
#      pwsh -File scripts\start-backend.ps1 -MailHost smtp.163.com -MailPort 465 -MailSsl `
#           -MailUser 你的账号@163.com -MailPassword 你的SMTP授权码
#
#    授权码在邮箱网页端「设置 → 账户 → SMTP 服务」中开启后生成，**不是登录密码**。
#    启动后可在「学业预警」页面点「邮件自检」发一封测试邮件验证配置。
param(
    [string]$MailHost,
    [int]$MailPort = 465,
    [string]$MailUser,
    [string]$MailPassword,
    [string]$MailFrom,
    [switch]$MailSsl,
    [switch]$MailStartTls,
    # 服务端口。默认 8080；若被其它程序（如另一个课程项目）占用，用 -Port 指定其它端口
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $proj 'backend\target\edu-system.jar'

if (-not (Test-Path $jar)) {
    throw "未找到 $jar，请先执行 scripts\build.ps1 或 mvn package"
}

# 端口占用预检查：否则 Spring Boot 启动到一半才失败，抛一大段堆栈，很难看出原因
$occupied = (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
if ($occupied) {
    $owner = ''
    try {
        $line = (& netstat -ano 2>&1 | Select-String ":$Port\s" | Where-Object { $_.Line -match 'LISTENING' } | Select-Object -First 1)
        if ($line) {
            $ownerPid = ($line.Line.Trim() -split '\s+')[-1]
            $proc = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
            if ($proc) { $owner = "（PID $ownerPid / $($proc.ProcessName)）" }
        }
    } catch { }
    $suggested = $Port + 1
    throw @"
端口 $Port 已被占用 $owner，本服务无法启动。
  · 若占用者是另一个项目，请换一个端口启动本项目（例如 $suggested）：
        & scripts\start-backend.ps1 -Port $suggested
  · 若确认占用者已无用，可结束它：
        Stop-Process -Id <PID>
  · 注意：换了端口后，前端开发服务器（Vite，5173）的 /api 代理也要指向同一端口，
    启动前端前先执行（把端口换成你实际用的）：
        `$env:VITE_API_TARGET = 'http://localhost:$suggested'
"@
}
$env:SERVER_PORT = "$Port"

# 解析 Java 17+ 的 JDK。不能直接用 PATH 上的 java ——
# 本机 PATH 上可能是 JDK 8，而 jar 由 JDK 21 编译，会报 UnsupportedClassVersionError。
. (Join-Path $PSScriptRoot '_jdk.ps1')
$jdkHome = Resolve-JdkHome
$javaExe = Join-Path $jdkHome 'bin\java.exe'
$env:JAVA_HOME = $jdkHome
Write-Host ("JDK    : {0}" -f $jdkHome)

# 让 JVM 的临时目录落在项目内，避免受限环境下的写入问题
$env:TEMP = Join-Path $proj '.tmp'
$env:TMP = Join-Path $proj '.tmp'
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null

# 未在命令行显式指定 -MailHost 时，尝试加载本地邮件凭据文件。
# 该文件被 .gitignore 忽略，用于避免把 SMTP 授权码写进受版本控制的 application.yml。
$localMail = Join-Path $proj 'config\mail.local.ps1'
if (-not $MailHost -and (Test-Path $localMail)) {
    . $localMail
    if ($MailConfig -and $MailConfig.Host) {
        $MailHost = $MailConfig.Host
        $MailUser = $MailConfig.User
        $MailPassword = $MailConfig.Password
        $MailPort = [int]$MailConfig.Port
        $MailSsl = [bool]$MailConfig.Ssl
        if ($MailConfig.StartTls) { $MailStartTls = $true }
        if ($MailConfig.From) { $MailFrom = $MailConfig.From }
        if ($MailConfig.PortalUrl) { $env:APP_MAIL_PORTAL = $MailConfig.PortalUrl }
        Write-Host '已加载本地邮件凭据：config\mail.local.ps1' -ForegroundColor DarkGray
    } else {
        Write-Warning "config\mail.local.ps1 存在但未定义 `$MailConfig，已忽略"
    }
}
if ($MailHost -and (-not $MailUser -or -not $MailPassword)) {
    # 只缺账号/授权码时再尝试本地文件补齐
    if (Test-Path $localMail) {
        . $localMail
        if ($MailConfig) {
            if (-not $MailUser) { $MailUser = $MailConfig.User }
            if (-not $MailPassword) { $MailPassword = $MailConfig.Password }
            if ($MailConfig.From -and -not $MailFrom) { $MailFrom = $MailConfig.From }
        }
    }
}

if ($MailHost) {
    if (-not $MailUser -or -not $MailPassword) {
        throw '真实发送需要同时提供 -MailUser 与 -MailPassword（SMTP 授权码）'
    }
    # 未显式指定加密方式时按端口推断：465→SSL，587→STARTTLS，其余（如 25）不加密
    if (-not $MailSsl -and -not $MailStartTls) {
        if ($MailPort -eq 465) { $MailSsl = $true }
        elseif ($MailPort -eq 587) { $MailStartTls = $true }
    }

    $env:APP_MAIL_ENABLED = 'true'
    $env:MAIL_HOST = $MailHost
    $env:MAIL_PORT = "$MailPort"
    $env:MAIL_USERNAME = $MailUser
    $env:MAIL_PASSWORD = $MailPassword
    $env:MAIL_SMTP_AUTH = 'true'
    $env:MAIL_SMTP_SSL = if ($MailSsl) { 'true' } else { 'false' }
    $env:MAIL_SMTP_STARTTLS = if ($MailStartTls) { 'true' } else { 'false' }
    if ($MailFrom) { $env:MAIL_FROM = $MailFrom }

    Write-Host '邮件：真实发送模式' -ForegroundColor Green
    Write-Host ("  SMTP   : {0}:{1}" -f $MailHost, $MailPort)
    Write-Host ("  账号   : {0}" -f $MailUser)
    Write-Host ("  加密   : {0}" -f $(if ($MailSsl) { 'SSL' } elseif ($MailStartTls) { 'STARTTLS' } else { '无（端口 25/2525 等）' }))
    Write-Host ("  发件人 : {0}" -f $(if ($MailFrom) { $MailFrom } else { "$MailUser（默认使用登录账号）" }))
    Write-Host '  提示   : MAIL_PASSWORD 必须是邮箱服务商生成的 SMTP 授权码，不是登录密码'
} else {
    $env:APP_MAIL_ENABLED = 'false'
    Write-Host '邮件：模拟发送模式（不连网络，正文输出到服务端日志）' -ForegroundColor Yellow
    Write-Host '  如需真实发送，请参阅本脚本头部的用法说明'
}

# 邮件正文里的系统入口地址跟随实际端口，避免改了 -Port 后学生点击链接跳错地方
$env:APP_MAIL_PORTAL = "http://localhost:$Port"

Write-Host ''
Write-Host '启动高校教务系统后端...' -ForegroundColor Cyan
Write-Host ("访问地址: http://localhost:{0}" -f $Port)
Write-Host '邮件自检: 登录管理员 → 学业预警 → 邮件自检'
Write-Host ''

& $javaExe -jar $jar
