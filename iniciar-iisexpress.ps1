$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
try { $Host.UI.RawUI.WindowTitle = 'Cadastro de Clientes - IIS Express' } catch {}
$iis = Join-Path ${env:ProgramFiles} 'IIS Express\iisexpress.exe'
if (-not (Test-Path $iis)) { throw 'IIS Express não foi encontrado.' }
if (-not (Get-Command sqllocaldb -ErrorAction SilentlyContinue)) {
  $tools = Get-ChildItem -Path "${env:ProgramFiles}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe", "${env:ProgramFiles(x86)}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($tools) {
    $env:Path = "$($tools.DirectoryName);$env:Path"
  }
}
if (-not (Get-Command sqllocaldb -ErrorAction SilentlyContinue)) { throw 'SQL Server LocalDB não foi encontrado.' }

sqllocaldb start MSSQLLocalDB | Out-Null
$info = sqllocaldb info MSSQLLocalDB
$pipeLine = $info | Where-Object { $_ -match 'pipe' } | Select-Object -First 1
if (-not $pipeLine) { throw 'Não foi possível localizar o named pipe da instância MSSQLLocalDB.' }
$pipe = ($pipeLine -split ':', 2)[1].Trim()
if ([string]::IsNullOrWhiteSpace($pipe)) { throw 'O named pipe da instância MSSQLLocalDB está vazio.' }

$env:SQLSERVER_DSN = "server=$pipe;database=CadastroClientes;trusted_connection=yes;encrypt=disable;protocol=np;serverspn=localhost"
$env:APP_ADDR = '127.0.0.1:8080'
$executable = Join-Path $root 'bin\cadastro-clientes.exe'
$goProcess = if (Test-Path $executable) {
  Start-Process -FilePath $executable -WorkingDirectory $root -PassThru
} elseif (Get-Command go -ErrorAction SilentlyContinue) {
  Start-Process -FilePath 'go' -ArgumentList 'run',(Join-Path $root 'cmd\cadastro-clientes') -WorkingDirectory $root -PassThru
} else {
  throw 'Executável Go não encontrado e Go não está disponível no PATH.'
}

try {
  $ready = $false
  for ($i = 0; $i -lt 30; $i++) {
    if ($goProcess.HasExited) { throw 'A aplicação Go encerrou antes de abrir a porta 8080. Verifique o banco e o named pipe do LocalDB.' }
    if (Test-NetConnection -ComputerName 127.0.0.1 -Port 8080 -InformationLevel Quiet) { $ready = $true; break }
    Start-Sleep -Seconds 1
  }
  if (-not $ready) { throw 'A aplicação Go não abriu a porta 8080 no tempo esperado.' }
  Start-Process -FilePath $iis -ArgumentList "/path:$root","/port:8088" -Wait
}
finally {
  if ($goProcess -and -not $goProcess.HasExited) { Stop-Process -Id $goProcess.Id -Force }
}
