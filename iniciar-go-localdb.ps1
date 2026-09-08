$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Get-Command sqllocaldb -ErrorAction SilentlyContinue)) {
  $tools = Get-ChildItem -Path "${env:ProgramFiles}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe", "${env:ProgramFiles(x86)}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($tools) {
    $env:Path = "$($tools.DirectoryName);$env:Path"
  }
}
if (-not (Get-Command sqllocaldb -ErrorAction SilentlyContinue)) {
  throw 'SQL Server LocalDB não foi encontrado.'
}

sqllocaldb start MSSQLLocalDB | Out-Null
$info = sqllocaldb info MSSQLLocalDB
$pipeLine = $info | Where-Object { $_ -match 'pipe' } | Select-Object -First 1
if (-not $pipeLine) {
  throw 'Não foi possível localizar o named pipe da instância MSSQLLocalDB.'
}
$pipe = ($pipeLine -split ':', 2)[1].Trim()
if ([string]::IsNullOrWhiteSpace($pipe)) {
  throw 'O named pipe da instância MSSQLLocalDB está vazio.'
}

$env:SQLSERVER_DSN = "server=$pipe;database=CadastroClientes;trusted_connection=yes;encrypt=disable;protocol=np;serverspn=localhost"
$env:APP_ADDR = '127.0.0.1:8080'
Write-Host 'Iniciando Cadastro de Clientes em http://127.0.0.1:8080'
Write-Host 'Para acessar pelo IIS, abra http://localhost:8088.'
$executable = Join-Path $root 'bin\cadastro-clientes.exe'
if (Test-Path $executable) {
  & $executable
} elseif (Get-Command go -ErrorAction SilentlyContinue) {
  & go run (Join-Path $root 'cmd\cadastro-clientes')
} else {
  throw 'Executável Go não encontrado e Go não está disponível no PATH.'
}
