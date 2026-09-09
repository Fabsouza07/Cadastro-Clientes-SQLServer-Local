package main

import (
	"net/http"
	"strconv"
	"strings"
)

func (a *App) users(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok || !u.Admin {
		return
	}
	rows, err := a.db.Query(`SELECT id,login,administrador FROM usuarios_cadastro_clientes ORDER BY administrador DESC,login`)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer rows.Close()
	var us []User
	for rows.Next() {
		var x User
		rows.Scan(&x.ID, &x.Login, &x.Admin)
		us = append(us, x)
	}
	if err := rows.Err(); err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	a.render(w, "users.html", view{Title: "Usuários", User: u, Users: us})
}

func (a *App) userSave(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok || !u.Admin {
		return
	}
	login, s := strings.TrimSpace(r.FormValue("login")), r.FormValue("senha")
	if !validLogin(login) || !validPass(s) {
		http.Error(w, "Login ou senha inválidos", 400)
		return
	}
	salt := random(16)
	_, e := a.db.Exec(`INSERT INTO usuarios_cadastro_clientes(login,senha_hash,senha_salt,administrador) VALUES(@p1,@p2,@p3,0)`, login, pbkdf2([]byte(s), salt), salt)
	if e != nil {
		http.Error(w, e.Error(), 400)
		return
	}
	http.Redirect(w, r, "/usuarios", 302)
}

func (a *App) userPassword(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok || !u.Admin {
		return
	}
	id, _ := strconv.ParseInt(r.FormValue("id"), 10, 64)
	s := r.FormValue("senha")
	if !validPass(s) {
		http.Error(w, "A senha deve ter pelo menos 8 caracteres", 400)
		return
	}
	salt := random(16)
	a.db.Exec(`UPDATE usuarios_cadastro_clientes SET senha_hash=@p1,senha_salt=@p2 WHERE id=@p3`, pbkdf2([]byte(s), salt), salt, id)
	http.Redirect(w, r, "/usuarios", 302)
}

func (a *App) userDelete(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok || !u.Admin {
		return
	}
	id, _ := strconv.ParseInt(r.FormValue("id"), 10, 64)
	a.db.Exec(`DELETE FROM usuarios_cadastro_clientes WHERE id=@p1 AND administrador=0`, id)
	http.Redirect(w, r, "/usuarios", 302)
}
