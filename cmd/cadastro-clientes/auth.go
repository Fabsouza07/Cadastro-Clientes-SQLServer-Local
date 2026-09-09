package main

import (
	"crypto/hmac"
	"database/sql"
	"encoding/base64"
	"fmt"
	"net/http"
	"strings"
)

func (a *App) current(r *http.Request) (*User, bool) {
	c, e := r.Cookie("session")
	if e != nil {
		return nil, false
	}
	a.mu.RLock()
	u, ok := a.sessions[c.Value]
	a.mu.RUnlock()
	return &u, ok
}

func (a *App) require(w http.ResponseWriter, r *http.Request) (*User, bool) {
	u, ok := a.current(r)
	if !ok {
		http.Redirect(w, r, "/login", 302)
	}
	return u, ok
}

func (a *App) login(w http.ResponseWriter, r *http.Request) {
	if r.Method == "GET" {
		userCount, _ := a.countUsers()
		a.render(w, "login.html", view{Title: "Acesso", HasUsers: userCount > 0})
		return
	}
	login := strings.TrimSpace(r.FormValue("login"))
	senha := r.FormValue("senha")
	var id int64
	var admin bool
	var hash, salt []byte
	var blocked bool
	err := a.db.QueryRow(`SELECT id,administrador,senha_hash,senha_salt,CASE WHEN bloqueado_ate>SYSUTCDATETIME() THEN 1 ELSE 0 END FROM usuarios_cadastro_clientes WHERE login=@p1`, login).Scan(&id, &admin, &hash, &salt, &blocked)
	found := err == nil
	if err == sql.ErrNoRows {
		if n, _ := a.countUsers(); n == 0 && validLogin(login) && len(senha) >= 8 {
			salt = random(16)
			hash = pbkdf2([]byte(senha), salt)
			err = a.db.QueryRow(`INSERT INTO usuarios_cadastro_clientes(login,senha_hash,senha_salt,administrador) OUTPUT INSERTED.id VALUES(@p1,@p2,@p3,1)`, login, hash, salt).Scan(&id)
			admin = true
		}
	}
	authenticated := err == nil && hmac.Equal(pbkdf2([]byte(senha), salt), hash) && !blocked
	if !authenticated && found && !blocked {
		_, _ = a.db.Exec(`UPDATE usuarios_cadastro_clientes SET tentativas_falhas=CASE WHEN bloqueado_ate IS NOT NULL AND bloqueado_ate<=SYSUTCDATETIME() THEN 1 WHEN tentativas_falhas+1>=3 THEN 0 ELSE tentativas_falhas+1 END,bloqueado_ate=CASE WHEN bloqueado_ate IS NOT NULL AND bloqueado_ate<=SYSUTCDATETIME() THEN NULL WHEN tentativas_falhas+1>=3 THEN DATEADD(MINUTE,10,SYSUTCDATETIME()) ELSE NULL END WHERE id=@p1 AND (bloqueado_ate IS NULL OR bloqueado_ate<=SYSUTCDATETIME())`, id)
	}
	if !authenticated {
		a.render(w, "error.html", view{Title: "Acesso", Error: "Login ou senha inválidos, ou usuário bloqueado."})
		return
	}
	token := base64.RawURLEncoding.EncodeToString(random(32))
	a.mu.Lock()
	a.sessions[token] = User{ID: id, Login: login, Admin: admin}
	a.mu.Unlock()
	http.SetCookie(w, &http.Cookie{Name: "session", Value: token, HttpOnly: true, SameSite: http.SameSiteLaxMode, Path: "/"})
	http.Redirect(w, r, "/", 302)
}

func (a *App) logout(w http.ResponseWriter, r *http.Request) {
	if c, e := r.Cookie("session"); e == nil {
		a.mu.Lock()
		delete(a.sessions, c.Value)
		a.mu.Unlock()
	}
	http.Redirect(w, r, "/login", 302)
}

func (a *App) countUsers() (int, error) {
	var n int
	err := a.db.QueryRow(`SELECT COUNT(*) FROM usuarios_cadastro_clientes`).Scan(&n)
	return n, err
}

func (a *App) debugUsers(w http.ResponseWriter, r *http.Request) {
	n, err := a.countUsers()
	if err != nil {
		fmt.Fprintf(w, "Erro ao contar usuários: %v", err)
		return
	}
	fmt.Fprintf(w, "Quantidade de usuários no banco: %d\n", n)
	if n == 0 {
		fmt.Fprintf(w, "O sistema está em modo de Primeiro Acesso. Você pode criar o admin agora.")
	} else {
		fmt.Fprintf(w, "O sistema NÃO está em modo de Primeiro Acesso. Você precisa usar /reset-admin para zerar a tabela.")
	}
}

func (a *App) resetUsersPublic(w http.ResponseWriter, r *http.Request) {
	_, err := a.db.Exec(`DELETE FROM dbo.usuarios_cadastro_clientes`)
	if err != nil {
		http.Error(w, "Erro ao resetar: "+err.Error(), 500)
		return
	}
	// Garante que a transação foi commitada (embora Exec faça isso automaticamente)
	n, _ := a.countUsers()
	fmt.Fprintf(w, "✅ SUCESSO! Tabela limpa. Usuários restantes: %d. Agora você pode ir para /login e criar o novo Administrador.", n)
}
