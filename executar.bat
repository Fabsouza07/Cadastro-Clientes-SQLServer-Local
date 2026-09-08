@echo off
setlocal
cd /d "%~dp0"
start "Cadastro de Clientes - Go" powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-go-localdb.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ready=$false; for($i=0;$i -lt 30;$i++){if(Test-NetConnection -ComputerName 127.0.0.1 -Port 8080 -InformationLevel Quiet){$ready=$true;break};Start-Sleep -Seconds 1}; if($ready){Start-Process 'chrome.exe' 'http://127.0.0.1:8080'}else{Write-Host 'A aplicação não abriu a porta 8080.'; exit 1}"
endlocal
