# A7Battery 构建脚本
# 需要 .NET 8 SDK（无需 Visual Studio）

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "=== 构建 Debug ===" -ForegroundColor Cyan
dotnet build src\A7Battery\A7Battery.csproj -c Debug -p:Platform=x64
if ($LASTEXITCODE -ne 0) { throw "Debug 构建失败" }

Write-Host "=== 构建 Release (自包含) ===" -ForegroundColor Cyan
dotnet build src\A7Battery\A7Battery.csproj -c Release -p:Platform=x64
if ($LASTEXITCODE -ne 0) { throw "Release 构建失败" }

Write-Host "=== 验证协议（需连接鼠标）===" -ForegroundColor Cyan
dotnet run --project tools\Probe\Probe.csproj

Write-Host "=== 完成 ===" -ForegroundColor Green
Write-Host "Release 输出: src\A7Battery\bin\x64\Release\net8.0-windows\win-x64\"
