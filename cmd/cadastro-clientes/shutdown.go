package main

import (
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"syscall"
	"time"
)

func (a *App) shutdown(w http.ResponseWriter, r *http.Request) {
	// 1. Limpa a sessão se houver
	if c, e := r.Cookie("session"); e == nil {
		a.mu.Lock()
		delete(a.sessions, c.Value)
		a.mu.Unlock()
	}
	http.SetCookie(w, &http.Cookie{
		Name:     "session",
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
	})

	// 2. Renderiza a tela de encerramento amigável para o usuário
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)

	html := `<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Aplicação Encerrada</title>
    <style>
        * { box-sizing: border-box; }
        body {
            margin: 0;
            background: #0f172a;
            color: #f8fafc;
            font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            font-size: 15px;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
        }
        .card {
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 36px 32px;
            max-width: 480px;
            width: 90%;
            text-align: center;
            box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5);
        }
        .icon {
            font-size: 48px;
            margin-bottom: 12px;
        }
        h1 {
            margin: 0 0 10px;
            font-size: 22px;
            color: #38bdf8;
        }
        p {
            color: #94a3b8;
            margin: 0 0 18px;
            line-height: 1.5;
        }
        .badge {
            display: inline-block;
            background: #059669;
            color: white;
            padding: 5px 14px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 16px;
        }
        .note {
            font-size: 13px;
            color: #64748b;
            border-top: 1px solid #334155;
            padding-top: 16px;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">🚪</div>
        <div class="badge">Encerrado</div>
        <h1>Aplicação Finalizada</h1>
        <p>A aplicação foi encerrada com sucesso e todos os terminais foram fechados.</p>
        <div class="note">Você já pode fechar esta aba do navegador.</div>
    </div>
    <script>
        setTimeout(function() {
            window.close();
        }, 1200);
    </script>
</body>
</html>`

	_, _ = w.Write([]byte(html))
	if flusher, ok := w.(http.Flusher); ok {
		flusher.Flush()
	}

	// 3. Executa o encerramento de todos os processos e terminais em segundo plano
	go func() {
		time.Sleep(600 * time.Millisecond)
		closeAllTerminals()
	}()
}

func closeAllTerminals() {
	pid := os.Getpid()
	ppid := os.Getppid()

	psScript := fmt.Sprintf(`
		# 1. Fechar janelas com título do Cadastro de Clientes
		taskkill.exe /F /FI "WINDOWTITLE eq Cadastro de Clientes*" 2>$null

		# 2. Localizar e encerrar processos PowerShell e CMD relacionados à aplicação
		Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
			$_.ProcessId -ne %d -and (
				$_.CommandLine -match 'iniciar-go-localdb' -or
				$_.CommandLine -match 'iniciar-iisexpress' -or
				$_.CommandLine -match 'executar\.bat' -or
				$_.CommandLine -match 'cmd[\\\/]cadastro-clientes'
			)
		} | ForEach-Object {
			Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
		}

		# 3. Encerrar processo do IIS Express caso ativo
		Stop-Process -Name iisexpress -Force -ErrorAction SilentlyContinue

		# 4. Encerrar terminal pai (janela do PowerShell ou Prompt que iniciou o Go)
		if (%d -gt 0 -and %d -ne %d) {
			Stop-Process -Id %d -Force -ErrorAction SilentlyContinue
		}

		# 5. Encerrar instâncias do executável da aplicação
		Get-Process -Name 'cadastro-clientes' -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne %d } | Stop-Process -Force -ErrorAction SilentlyContinue

		# 6. Encerrar o processo atual
		Stop-Process -Id %d -Force -ErrorAction SilentlyContinue
	`, pid, ppid, ppid, pid, ppid, pid, pid)

	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	_ = cmd.Run()

	os.Exit(0)
}
