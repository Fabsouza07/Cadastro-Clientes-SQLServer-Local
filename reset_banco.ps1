$ErrorActionPreference = 'Stop'

# 1. Localizar SqlLocalDB.exe
$sqlLocalDbPath = Get-ChildItem -Path "${env:ProgramFiles}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe", "${env:ProgramFiles(x86)}\Microsoft SQL Server\*\Tools\Binn\SqlLocalDB.exe" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $sqlLocalDbPath) {
    Write-Host "Erro: SqlLocalDB.exe não encontrado." -ForegroundColor Red
    return
}

# 2. Iniciar instância
Write-Host "Iniciando instância MSSQLLocalDB..." -ForegroundColor Cyan
& $sqlLocalDbPath.FullName start MSSQLLocalDB | Out-Null

# 3. Extrair o Named Pipe real (é a única forma 100% confiável no LocalDB)
$info = & $sqlLocalDbPath.FullName info MSSQLLocalDB
$pipeLine = $info | Where-Object { $_ -match 'pipe' } | Select-Object -First 1
if (-not $pipeLine) {
    Write-Host "Erro: Não foi possível localizar o named pipe do banco." -ForegroundColor Red
    return
}
$pipe = ($pipeLine -split ':', 2)[1].Trim()

# 4. Montar a conexão idêntica à do projeto
# Importante: usamos o pipe diretamente no server
$connStr = "server=$pipe;database=CadastroClientes;trusted_connection=yes;encrypt=disable"
$env:SQLSERVER_DSN = $connStr

Write-Host "Conectando via Pipe: $pipe" -ForegroundColor Gray

# 5. Executar a limpeza em Go
Write-Host "Executando limpeza..." -ForegroundColor Cyan
go run limpar_usuarios.go

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✨ Tudo pronto! Agora você pode abrir o executar.bat e criar seu novo Admin." -ForegroundColor Green
} else {
    Write-Host "`n❌ Erro ao executar a limpeza." -ForegroundColor Red
}
