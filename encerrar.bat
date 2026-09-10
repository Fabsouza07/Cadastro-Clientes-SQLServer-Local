@echo off
setlocal
cd /d "%~dp0"
echo Encerrando Cadastro de Clientes e todos os terminais...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "taskkill.exe /F /FI 'WINDOWTITLE eq Cadastro de Clientes*' 2>$null; Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match 'iniciar-go-localdb' -or $_.CommandLine -match 'iniciar-iisexpress' -or $_.CommandLine -match 'cadastro-clientes' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; Stop-Process -Name iisexpress, cadastro-clientes -Force -ErrorAction SilentlyContinue"
echo Aplicacao e terminais finalizados com sucesso.
endlocal
