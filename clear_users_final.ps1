$ErrorActionPreference = 'Stop'
sqllocaldb start MSSQLLocalDB | Out-Null
$info = sqllocaldb info MSSQLLocalDB
$pipeLine = $info | Where-Object { $_ -match 'pipe' } | Select-Object -First 1
$pipe = ($pipeLine -split ':', 2)[1].Trim()

# Use sqlcmd if available, otherwise we fail.
# We try to find sqlcmd in common paths.
$sqlcmd = Get-ChildItem -Path "${env:ProgramFiles}\Microsoft SQL Server\*\Tools\Binn\sqlcmd.exe", "${env:ProgramFiles(x86)}\Microsoft SQL Server\*\Tools\Binn\sqlcmd.exe" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($sqlcmd) {
    & $sqlcmd.FullName -S $pipe -d CadastroClientes -Q "DELETE FROM dbo.usuarios_cadastro_clientes" -E
    Write-Host "Usuários removidos com sucesso via sqlcmd."
} else {
    Write-Host "sqlcmd.exe não encontrado no sistema."
}
