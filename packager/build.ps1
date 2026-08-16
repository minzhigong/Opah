# Opah Windows 绿色包打包脚本
# 流程：web build -> mvn package（内嵌 web 产物）-> jlink 裁剪 JRE -> jpackage app-image -> zip
# 前置：JDK 21（含 jlink/jpackage）、Maven、Node 22

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# 1. 前端构建
Write-Host "[1/5] building web..." -ForegroundColor Cyan
Push-Location "$root\web"
if (-not (Test-Path node_modules)) { npm install }
npm run build
if ($LASTEXITCODE -ne 0) { throw "web build failed" }
Pop-Location

# 2. 拷贝 web 产物到 server static
Write-Host "[2/5] copying web dist to server..." -ForegroundColor Cyan
$staticDir = "$root\server\src\main\resources\static"
if (Test-Path $staticDir) { Remove-Item $staticDir -Recurse -Force }
New-Item -ItemType Directory -Path $staticDir | Out-Null
Copy-Item "$root\web\dist\*" $staticDir -Recurse

# 3. 后端打包（jpackage 需主 jar；spring-boot-maven-plugin 已产出可执行 fat jar）
Write-Host "[3/5] building server..." -ForegroundColor Cyan
Push-Location "$root\server"
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "server build failed" }
Pop-Location

# 4. jlink 裁剪运行时
$jdk = $env:JAVA_HOME
if (-not $jdk) { $jdk = "$env:USERPROFILE\.workbuddy\binaries\java" }
$jmods = Join-Path $jdk "jmods"
$runtimeDir = "$root\packager\out\runtime"
if (Test-Path $runtimeDir) { Remove-Item $runtimeDir -Recurse -Force }
Write-Host "[4/5] jlink trimming runtime..." -ForegroundColor Cyan
& (Join-Path $jdk "bin\jlink.exe") `
  --module-path $jmods `
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs `
  --strip-debug --no-header-files --no-man-pages `
  --compress=zip-6 `
  --output $runtimeDir
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

# 5. jpackage app-image
$outDir = "$root\packager\out\app"
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
$jarPath = Get-ChildItem "$root\server\target\opah-server-*.jar" | Select-Object -First 1
Write-Host "[5/5] jpackage app-image..." -ForegroundColor Cyan
& (Join-Path $jdk "bin\jpackage.exe") `
  --type app-image `
  --name opah `
  --input $jarPath.DirectoryName `
  --main-jar $jarPath.Name `
  --runtime-image $runtimeDir `
  --win-console `
  --dest $outDir
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

# 打包 zip
$zipName = "opah-windows.zip"
Compress-Archive -Path "$outDir\opah\*" -DestinationPath "$root\packager\out\$zipName" -Force
Write-Host "DONE: $root\packager\out\$zipName" -ForegroundColor Green
