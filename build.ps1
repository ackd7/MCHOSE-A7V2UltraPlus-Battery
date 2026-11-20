# A7Battery 构建脚本
# 需要 JDK 17+（无需预装 Gradle，使用 gradlew 自动下载）

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "=== 构建（Compose Desktop）===" -ForegroundColor Cyan
.\gradlew.bat :app:build
if ($LASTEXITCODE -ne 0) { throw "构建失败" }

Write-Host "=== 打包桌面分发（MSI + EXE）===" -ForegroundColor Cyan
.\gradlew.bat :app:packageDistributionForCurrentOS
if ($LASTEXITCODE -ne 0) { throw "打包失败" }

Write-Host "=== 完成 ===" -ForegroundColor Green
Write-Host "产物目录: app\build\compose\binaries\"
