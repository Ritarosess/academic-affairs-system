# 解析可用的 JDK（要求 Java 17 及以上），供其他脚本点源使用。
#
# 背景：本机 PATH 上的 `java` 可能是 JDK 8（D:\Java\jdk1.8.0_162），
# 而本项目的 jar 由 JDK 21 编译，class 文件版本 61，JDK 8 无法加载，
# 直接运行会报 UnsupportedClassVersionError。
# 因此这里按优先级查找，并**实际校验版本**，找不到时给出明确提示。

function Get-JavaMajorVersion {
    param([string]$JavaExe)
    if (-not $JavaExe -or -not (Test-Path $JavaExe)) { return $null }
    # java -version 会向 stderr 输出，在 ErrorActionPreference='Stop' 下会抛
    # NativeCommandError，因此这里临时切回 Continue。
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $JavaExe -version 2>&1 | Out-String
    } catch {
        return $null
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($out -match 'version "(\d+)') {
        # JDK 8 输出 1.8.0_162，捕获到 1，天然小于 17
        return [int]$Matches[1]
    }
    return $null
}

function Resolve-JdkHome {
    $candidates = @()

    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

    # IntelliJ 下载的 JDK 常见位置
    foreach ($pattern in @('.jdks\openjdk-2*', '.jdks\openjdk-1[7-9]*', '.jdks\corretto-2*',
                           '.jdks\temurin-2*', '.jdks\ms-2*', '.jdks\graalvm-2*')) {
        $dir = Join-Path $env:USERPROFILE $pattern
        $candidates += (Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue |
                        Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
    }

    # 系统安装位置
    foreach ($root in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
                        'C:\Program Files\Microsoft', 'C:\Program Files\Amazon Corretto')) {
        $candidates += (Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                        Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
    }

    # 注意：不能用 $home 作循环变量 —— 它是 PowerShell 的只读自动变量
    foreach ($jdkCandidate in $candidates) {
        if (-not $jdkCandidate) { continue }
        $exe = Join-Path $jdkCandidate 'bin\java.exe'
        $major = Get-JavaMajorVersion $exe
        if ($major -and $major -ge 17) {
            return (Resolve-Path $jdkCandidate).Path
        }
    }

    # 最后才考虑 PATH 上的 java
    $onPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    $major = Get-JavaMajorVersion $onPath
    if ($major -and $major -ge 17) {
        return (Split-Path (Split-Path $onPath -Parent) -Parent)
    }

    $found = if ($onPath) { "PATH 上的 java 为 Java $major（$onPath）" } else { 'PATH 上未找到 java' }
    throw @"
未找到 Java 17 或更高版本的 JDK，无法运行本项目。
  · $found
  · 本项目由 JDK 21 编译，需要 Java 17+ 才能运行。
  · 解决办法（任选其一）：
      1. 设置环境变量 JAVA_HOME 指向 JDK 21 安装目录，例如：
           `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
      2. 或在启动前把 JDK 21 的 bin 目录加入 PATH 最前面。
"@
}
