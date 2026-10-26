param(
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$DatabaseHost = '127.0.0.1',
    [int]$DatabasePort = 3306,
    [string]$DatabaseUser = 'root'
)
$ErrorActionPreference = 'Stop'
if (!$env:DB_PASSWORD) { throw 'Set DB_PASSWORD in this admin session; never pass it as a command-line argument.' }
$dumpCommand = Get-Command mysqldump -ErrorAction SilentlyContinue
$dumpTool = if ($dumpCommand) { $dumpCommand.Source } else { 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe' }
if (!(Test-Path -LiteralPath $dumpTool)) { throw 'Install the MySQL 8 client tools first.' }
$targetDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
$backupFile = Join-Path $targetDirectory ('shopease-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.sql')
if (Test-Path -LiteralPath $backupFile) { throw 'Backup destination exists; refusing to overwrite.' }
$oldPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & $dumpTool -h $DatabaseHost -P $DatabasePort -u $DatabaseUser --single-transaction --quick --hex-blob --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$backupFile" --databases ecommerce_db shopease_auth shopease_product shopease_order shopease_payment shopease_notification shopease_recommendation
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed. Retain the partial file for diagnosis; do not restore it.' }
    $hash = Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
    @{ file = [IO.Path]::GetFileName($backupFile); sha256 = $hash.Hash; bytes = (Get-Item -LiteralPath $backupFile).Length; capturedAt = (Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath ($backupFile + '.manifest.json')
    Write-Host 'All seven schemas exported in one consistent snapshot; SHA256 manifest saved.'
    Write-Host 'The export contains customer data and password hashes. Encrypt it and retain an off-host copy before any cutover.'
} finally {
    $env:MYSQL_PWD = $oldPassword
}
