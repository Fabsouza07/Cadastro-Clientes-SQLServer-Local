$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$siteName = 'CadastroClientesGo'
$appcmd = Join-Path $env:windir 'System32\inetsrv\appcmd.exe'

if (-not (Test-Path $appcmd)) { throw 'IIS completo não está instalado.' }
Import-Module WebAdministration

if (-not (Get-Website -Name $siteName -ErrorAction SilentlyContinue)) {
  New-Website -Name $siteName -PhysicalPath $root -Port 8088 -HostHeader 'localhost' | Out-Null
} else {
  Set-ItemProperty "IIS:\Sites\$siteName" -Name physicalPath -Value $root
}

& $appcmd set config -section:system.webServer/proxy /enabled:true /commit:apphost
Start-Website -Name $siteName
Write-Host "IIS configurado. Acesse http://localhost:8088"
