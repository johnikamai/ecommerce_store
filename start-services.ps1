param([switch]$Build, [switch]$NoFrontend)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$runtime = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force $runtime | Out-Null
$javaTemp = Join-Path $runtime 'tmp'
New-Item -ItemType Directory -Force $javaTemp | Out-Null
$env:TEMP = $javaTemp
$env:TMP = $javaTemp
$localProperties = Join-Path $runtime 'application.properties'
if (!(Test-Path -LiteralPath $localProperties)) { New-Item -ItemType File $localProperties | Out-Null }
if (!(Select-String -Path $localProperties -Pattern '^JWT_SECRET=' -Quiet) -and !$env:JWT_SECRET) {
    $keyBytes = New-Object byte[] 48
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($keyBytes) } finally { $random.Dispose() }
    Add-Content $localProperties ('JWT_SECRET=' + [Convert]::ToBase64String($keyBytes))
}
$java = (Get-Command java -ErrorAction Stop).Source
$modules = @('services/service-common', 'services/auth-service', 'backend', 'discovery-server', 'services/product-service', 'api-gateway')
if ($Build) {
    foreach ($module in $modules) {
        & "$projectRoot/backend/mvnw.cmd" -f "$projectRoot/$module/pom.xml" install -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $module" }
    }
}
function Start-ShopEase($name, $port, $jar, $arguments) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "$name already listening on $port; leaving it running."
        return
    }
    if (!(Test-Path -LiteralPath $jar)) { throw "Missing $jar. Run ./start-services.ps1 -Build first." }
    $javaArguments = @('-Dspring.main.banner-mode=off', ('"-Djava.io.tmpdir=' + $javaTemp + '"'), '-jar', ('"' + $jar + '"')) + $arguments
    $process = Start-Process -FilePath $java -ArgumentList $javaArguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/$name.log" -RedirectStandardError "$runtime/$name.err.log"
    Set-Content "$runtime/$name.pid" $process.Id
}
$localConfig = '--spring.config.additional-location=optional:file:./.local/application.properties'
Start-ShopEase 'discovery' 8761 "$projectRoot/discovery-server/target/discovery-server-1.0.0.jar" @()
Start-ShopEase 'auth' 8081 "$projectRoot/services/auth-service/target/auth-service-1.0.0.jar" @($localConfig)
Start-ShopEase 'compatibility' 8087 "$projectRoot/backend/target/ecommerce-system-0.0.1-SNAPSHOT.jar" @('--spring.profiles.active=compatibility', $localConfig)
Start-ShopEase 'product' 8082 "$projectRoot/services/product-service/target/product-service-1.0.0.jar" @('--spring.profiles.active=compatibility', $localConfig)
Start-ShopEase 'gateway' 8080 "$projectRoot/api-gateway/target/api-gateway-1.0.0.jar" @()
if (!$NoFrontend) {
    if (!(Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)) {
        $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
        $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/s', '/c', ('""' + $npm + '" run dev -- --host 127.0.0.1 --port 5173 --strictPort"')) -WorkingDirectory "$projectRoot/frontend" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/frontend.log" -RedirectStandardError "$runtime/frontend.err.log"
        Set-Content "$runtime/frontend.pid" $process.Id
    }
}
function Wait-ShopEase($name, $url) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3
            if ($response.StatusCode -eq 200) { Write-Host "$name ready (HTTP 200)"; return $response }
        } catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "$name did not become ready at $url. Inspect .local/$name.log and .local/$name.err.log."
}
Wait-ShopEase 'discovery' 'http://localhost:8761/actuator/health' | Out-Null
Wait-ShopEase 'auth' 'http://localhost:8081/actuator/health' | Out-Null
Wait-ShopEase 'compatibility' 'http://localhost:8087/api/health' | Out-Null
Wait-ShopEase 'product' 'http://localhost:8082/actuator/health' | Out-Null
$catalogCheck = Wait-ShopEase 'gateway' 'http://localhost:8080/api/products'
if ($catalogCheck.Headers['X-ShopEase-Backend'] -ne 'product-service') {
    throw 'Port 8080 is not serving the expected Gateway product route. Check the existing process before restarting.'
}
if (!$NoFrontend) { Wait-ShopEase 'frontend' 'http://localhost:5173' | Out-Null }
Write-Host 'ShopEase launched. Frontend: http://localhost:5173  Gateway: http://localhost:8080  Eureka: http://localhost:8761'
Write-Host 'Auth/profile/staff run in auth-service; basic product reads run in product-service. Other APIs are still migrating.'
Write-Host 'Run node run_integration_tests.js after startup. Logs are in .local/.'
