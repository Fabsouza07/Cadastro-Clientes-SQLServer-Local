package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/csv"
	"errors"
	"fmt"
	"html/template"
	"log"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	_ "github.com/microsoft/go-mssqldb"
	_ "github.com/microsoft/go-mssqldb/namedpipe"
)

const (
	iterations = 210000
	page       = `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{{.Title}} - Cadastro de Clientes</title><style>
*{box-sizing:border-box}body{margin:0;background:#f8fafc;color:#1e293b;font:14px Segoe UI,Arial,sans-serif}header{background:#0f172a;color:white;padding:16px 28px;display:flex;justify-content:space-between;align-items:center}header a{color:white;text-decoration:none}.brand{font-size:20px;font-weight:600}.muted{color:#94a3b8}.layout{display:flex;min-height:calc(100vh - 58px)}nav{width:220px;background:white;border-right:1px solid #e2e8f0;padding-top:28px}nav a{display:block;padding:13px 24px;color:#1e293b;text-decoration:none}nav a:hover{background:#eff6ff}.content{flex:1;padding:28px;max-width:1400px}.top{display:flex;justify-content:space-between;align-items:center}.top h1{margin:0 0 5px;font-size:27px}.btn{display:inline-block;border:0;border-radius:7px;background:#2563eb;color:white;padding:10px 16px;text-decoration:none;cursor:pointer}.btn.gray{background:#fff;color:#1e293b;border:1px solid #cbd5e1}.btn.red{background:#dc2626}.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:25px 0 18px}.card,.panel{background:white;border:1px solid #e2e8f0;border-radius:7px;padding:17px}.card .value{font-size:23px;font-weight:600;margin-top:10px}.panel h2{font-size:17px;margin:0 0 16px}.toolbar{display:flex;justify-content:space-between;margin-bottom:14px}.search{padding:9px;border:1px solid #cbd5e1;border-radius:6px;width:250px}table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:12px 9px;border-bottom:1px solid #e2e8f0}th{color:#64748b;font-size:12px}form.inline{display:inline}input,select{padding:10px;border:1px solid #cbd5e1;border-radius:6px;width:100%;margin:5px 0 14px}label{font-weight:600;font-size:13px}.form{max-width:650px}.actions{display:flex;gap:8px}.alert{padding:11px;border-radius:6px;margin:0 0 16px;background:#fee2e2;color:#991b1b}.success{background:#dcfce7;color:#166534}.login{max-width:430px;margin:10vh auto}.login .panel{padding:30px}.login h1{margin-top:0}.empty{text-align:center;color:#64748b;padding:25px}@media(max-width:800px){nav{width:150px}.cards{grid-template-columns:repeat(2,1fr)}.content{padding:16px}table{font-size:12px}}
</style></head><body>{{if .User}}<header><a class="brand" href="/">C &nbsp; Cadastro de Clientes</a><span>{{if .User.Admin}}Administrador{{else}}Operador{{end}}: {{.User.Login}} &nbsp; | &nbsp; <a href="/logout">Sair</a></span></header><div class="layout"><nav><a href="/">Visão geral</a><a href="/clientes/novo">Novo cliente</a>{{if .User.Admin}}<a href="/usuarios">Usuários</a><a href="/exportar">Exportar CSV</a>{{end}}</nav>{{end}}<main class="content">{{if not .User}}<div class="login">{{end}}{{if .Error}}<div class="alert">{{.Error}}</div>{{end}}{{if .Success}}<div class="alert success">{{.Success}}</div>{{end}}{{template "content" .}}{{if not .User}}</div>{{end}}</main></div></body></html>`
)

type User struct {
	ID    int64
	Login string
	Admin bool
}
type Client struct {
	ID                         int64
	Name                       string
	Age                        int
	City, Email, Phone, Mobile string
}
type Stats struct {
	Total        int64
	Average      float64
	Oldest, City string
}
type App struct {
	db        *sql.DB
	sessions  map[string]User
	mu        sync.RWMutex
	templates *template.Template
}
type view struct {
	Title                 string
	User                  *User
	HasUsers              bool
	Clients               []Client
	Client                Client
	Stats                 Stats
	Query, Error, Success string
	Users                 []User
	Editing               bool
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
func dsn() string {
	if v := os.Getenv("SQLSERVER_DSN"); v != "" {
		return v
	}
	server := env("SQLSERVER_SERVER", `localhost\SQLEXPRESS`)
	db := env("SQLSERVER_DATABASE", "CadastroClientes")
	user := os.Getenv("SQLSERVER_USER")
	pass := os.Getenv("SQLSERVER_PASSWORD")
	instance := ""
	if parts := strings.SplitN(server, `\`, 2); len(parts) == 2 {
		server, instance = parts[0], parts[1]
	}
	u := &url.URL{Scheme: "sqlserver", Host: server, Path: "/" + instance}
	query := u.Query()
	query.Set("database", db)
	query.Set("encrypt", "disable")
	if user == "" {
		query.Set("trusted_connection", "yes")
	} else {
		u.User = url.UserPassword(user, pass)
	}
	u.RawQuery = query.Encode()
	return u.String()
}

func main() {
	db, err := sql.Open("sqlserver", dsn())
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	db.SetMaxOpenConns(10)
	db.SetConnMaxLifetime(30 * time.Minute)
	if err = db.Ping(); err != nil {
		log.Fatalf("falha ao conectar ao SQL Server LocalDB: %v", err)
	}
	if err = ensureSchema(db); err != nil {
		log.Fatal(err)
	}
	t := template.Must(template.New("page").Parse(page))
	t = template.Must(t.Parse(`{{define "content"}}<div></div>{{end}}`))
	a := &App{db: db, sessions: map[string]User{}, templates: t}
	mux := http.NewServeMux()
	mux.HandleFunc("/login", a.login)
	mux.HandleFunc("/logout", a.logout)
	mux.HandleFunc("/", a.dashboard)
	mux.HandleFunc("/clientes/novo", a.clientForm)
	mux.HandleFunc("/clientes/salvar", a.clientSave)
	mux.HandleFunc("/clientes/excluir", a.clientDelete)
	mux.HandleFunc("/exportar", a.export)
	mux.HandleFunc("/usuarios", a.users)
	mux.HandleFunc("/usuarios/salvar", a.userSave)
	mux.HandleFunc("/usuarios/senha", a.userPassword)
	mux.HandleFunc("/usuarios/excluir", a.userDelete)
	addr := env("APP_ADDR", ":8080")
	displayAddr := addr
	if strings.HasPrefix(addr, ":") {
		displayAddr = "localhost" + addr
	}
	log.Printf("Sistema disponível em http://%s", displayAddr)
	log.Fatal(http.ListenAndServe(addr, mux))
}

func ensureSchema(db *sql.DB) error {
	_, err := db.Exec(`IF OBJECT_ID('dbo.clientes','U') IS NULL CREATE TABLE dbo.clientes(id BIGINT IDENTITY PRIMARY KEY,nome NVARCHAR(120) NOT NULL,idade INT NOT NULL,cidade NVARCHAR(100) NOT NULL,email NVARCHAR(160) NOT NULL UNIQUE,telefone_fixo NVARCHAR(20),telefone_celular NVARCHAR(20)); IF OBJECT_ID('dbo.usuarios_cadastro_clientes','U') IS NULL CREATE TABLE dbo.usuarios_cadastro_clientes(id BIGINT IDENTITY PRIMARY KEY,login NVARCHAR(50) NOT NULL UNIQUE,senha_hash VARBINARY(64) NOT NULL,senha_salt VARBINARY(32) NOT NULL,administrador BIT NOT NULL DEFAULT 0,criado_em DATETIME2 NOT NULL DEFAULT SYSDATETIME(),tentativas_falhas INT NOT NULL DEFAULT 0,bloqueado_ate DATETIME2 NULL)`)
	return err
}
func (a *App) current(w http.ResponseWriter, r *http.Request) (*User, bool) {
	c, e := r.Cookie("session")
	if e != nil {
		return nil, false
	}
	a.mu.RLock()
	u, ok := a.sessions[c.Value]
	a.mu.RUnlock()
	return &u, ok
}
func (a *App) render(w http.ResponseWriter, name string, v view) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	tmpl := template.Must(template.New("page").Parse(page))
	template.Must(tmpl.Parse(name))
	if err := tmpl.Execute(w, v); err != nil {
		http.Error(w, err.Error(), 500)
	}
}
func (a *App) require(w http.ResponseWriter, r *http.Request) (*User, bool) {
	u, ok := a.current(w, r)
	if !ok {
		http.Redirect(w, r, "/login", 302)
	}
	return u, ok
}

func (a *App) login(w http.ResponseWriter, r *http.Request) {
	if r.Method == "GET" {
		userCount, _ := a.countUsers()
		a.render(w, `{{define "content"}}<div class="panel"><h1>Acesso ao Cadastro de Clientes</h1>{{if .HasUsers}}<p>Informe suas credenciais para acessar o sistema.</p>{{else}}<p>Primeiro acesso: crie o administrador do sistema.</p>{{end}}<form method="post"><label>Login</label><input name="login" autofocus><label>Senha</label><input type="password" name="senha"><button class="btn">Entrar</button></form></div>{{end}}`, view{Title: "Acesso", HasUsers: userCount > 0})
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
		a.render(w, `{{define "content"}}<div class="panel"><h1>Acesso ao Cadastro de Clientes</h1><div class="alert">Login ou senha inválidos, ou usuário bloqueado.</div><form method="post"><label>Login</label><input name="login"><label>Senha</label><input type="password" name="senha"><button class="btn">Entrar</button></form></div>{{end}}`, view{Title: "Acesso", Error: "Login ou senha inválidos"})
		return
	}
	token := base64.RawURLEncoding.EncodeToString(random(32))
	a.mu.Lock()
	a.sessions[token] = User{ID: id, Login: login, Admin: admin}
	a.mu.Unlock()
	http.SetCookie(w, &http.Cookie{Name: "session", Value: token, HttpOnly: true, SameSite: http.SameSiteLaxMode, Path: "/"})
	http.Redirect(w, r, "/", 302)
}
func (a *App) countUsers() (int, error) {
	var n int
	err := a.db.QueryRow(`SELECT COUNT(*) FROM usuarios_cadastro_clientes`).Scan(&n)
	return n, err
}
func (a *App) logout(w http.ResponseWriter, r *http.Request) {
	if c, e := r.Cookie("session"); e == nil {
		a.mu.Lock()
		delete(a.sessions, c.Value)
		a.mu.Unlock()
	}
	http.Redirect(w, r, "/login", 302)
}

func (a *App) dashboard(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok {
		return
	}
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	rows, err := a.db.Query(`SELECT id,nome,idade,cidade,email,COALESCE(telefone_fixo,''),COALESCE(telefone_celular,'') FROM clientes WHERE @p1='' OR LOWER(nome) LIKE LOWER(@p2) OR LOWER(cidade) LIKE LOWER(@p2) ORDER BY nome,id`, q, "%"+q+"%")
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer rows.Close()
	var cs []Client
	for rows.Next() {
		var c Client
		rows.Scan(&c.ID, &c.Name, &c.Age, &c.City, &c.Email, &c.Phone, &c.Mobile)
		cs = append(cs, c)
	}
	if err := rows.Err(); err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	var s Stats
	a.db.QueryRow(`SELECT COUNT(*),COALESCE(AVG(idade),0) FROM clientes`).Scan(&s.Total, &s.Average)
	a.db.QueryRow(`SELECT TOP 1 nome FROM clientes ORDER BY idade DESC,nome`).Scan(&s.Oldest)
	a.db.QueryRow(`SELECT TOP 1 cidade FROM clientes GROUP BY cidade ORDER BY COUNT(*) DESC,cidade`).Scan(&s.City)
	a.render(w, `{{define "content"}}<div class="top"><div><h1>Visão geral</h1><span class="muted">Acompanhe e gerencie os clientes cadastrados.</span></div><a class="btn" href="/clientes/novo">+ Novo cliente</a></div><div class="cards"><div class="card">Total de clientes<div class="value">{{.Stats.Total}}</div></div><div class="card">Idade média<div class="value">{{printf "%.1f" .Stats.Average}} anos</div></div><div class="card">Cliente mais velho<div class="value">{{.Stats.Oldest}}</div></div><div class="card">Cidade em destaque<div class="value">{{.Stats.City}}</div></div></div><div class="panel"><div class="toolbar"><h2>Clientes cadastrados</h2><form><input class="search" name="q" value="{{.Query}}" placeholder="Pesquisar por nome ou cidade"></form></div>{{if .Clients}}<table><tr><th>ID</th><th>Nome</th><th>Idade</th><th>Cidade</th><th>E-mail</th><th>Telefones</th><th></th></tr>{{range .Clients}}<tr><td>{{.ID}}</td><td>{{.Name}}</td><td>{{.Age}}</td><td>{{.City}}</td><td>{{.Email}}</td><td>{{.Phone}}<br>{{.Mobile}}</td><td><a class="btn gray" href="/clientes/novo?id={{.ID}}">Editar</a> <form class="inline" method="post" action="/clientes/excluir"><input type="hidden" name="id" value="{{.ID}}"><button class="btn red" onclick="return confirm('Excluir este cliente?')">Excluir</button></form></td></tr>{{end}}</table>{{else}}<div class="empty">Nenhum cliente encontrado.</div>{{end}}</div>{{end}}`, view{Title: "Visão geral", User: u, Clients: cs, Stats: s, Query: q})
}

func validClient(c Client) error {
	if strings.TrimSpace(c.Name) == "" || strings.TrimSpace(c.City) == "" {
		return errors.New("Nome e cidade são obrigatórios.")
	}
	if c.Age < 1 || c.Age > 120 {
		return errors.New("Idade deve estar entre 1 e 120.")
	}
	if !regexp.MustCompile(`^[^\s@]+@[^\s@]+\.[^\s@]+$`).MatchString(c.Email) {
		return errors.New("E-mail inválido.")
	}
	return nil
}
func phone(v string, n int) string {
	d := regexp.MustCompile(`\D`).ReplaceAllString(v, "")
	if d == "" {
		return ""
	}
	if len(d) != n {
		return v
	}
	if n == 10 {
		return fmt.Sprintf("(%s) %s-%s", d[:2], d[2:6], d[6:])
	}
	return fmt.Sprintf("(%s) %s-%s", d[:2], d[2:7], d[7:])
}
func (a *App) clientForm(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok {
		return
	}
	var c Client
	id, _ := strconv.ParseInt(r.URL.Query().Get("id"), 10, 64)
	editing := id > 0
	if editing {
		a.db.QueryRow(`SELECT id,nome,idade,cidade,email,COALESCE(telefone_fixo,''),COALESCE(telefone_celular,'') FROM clientes WHERE id=@p1`, id).Scan(&c.ID, &c.Name, &c.Age, &c.City, &c.Email, &c.Phone, &c.Mobile)
	}
	a.render(w, `{{define "content"}}<div class="panel form"><h1>{{if .Editing}}Editar cliente{{else}}Cadastrar cliente{{end}}</h1><form method="post" action="/clientes/salvar"><input type="hidden" name="id" value="{{.Client.ID}}"><label>Nome</label><input name="nome" value="{{.Client.Name}}"><label>Idade</label><input type="number" name="idade" value="{{.Client.Age}}"><label>Cidade</label><input name="cidade" value="{{.Client.City}}"><label>E-mail</label><input type="email" name="email" value="{{.Client.Email}}"><label>Telefone fixo</label><input name="fixo" value="{{.Client.Phone}}"><label>Celular</label><input name="celular" value="{{.Client.Mobile}}"><div class="actions"><button class="btn">Salvar</button><a class="btn gray" href="/">Cancelar</a></div></form></div>{{end}}`, view{Title: "Cliente", User: u, Client: c, Editing: editing})
}
func (a *App) clientSave(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok {
		return
	}
	id, _ := strconv.ParseInt(r.FormValue("id"), 10, 64)
	age, _ := strconv.Atoi(r.FormValue("idade"))
	c := Client{ID: id, Name: strings.TrimSpace(r.FormValue("nome")), Age: age, City: strings.TrimSpace(r.FormValue("cidade")), Email: strings.TrimSpace(r.FormValue("email")), Phone: phone(r.FormValue("fixo"), 10), Mobile: phone(r.FormValue("celular"), 11)}
	if err := validClient(c); err != nil {
		a.render(w, `{{define "content"}}<div class="alert">{{.Error}}</div><a href="javascript:history.back()">Voltar</a>{{end}}`, view{Title: "Erro", User: u, Error: err.Error()})
		return
	}
	var err error
	if id > 0 {
		_, err = a.db.Exec(`UPDATE clientes SET nome=@p1,idade=@p2,cidade=@p3,email=@p4,telefone_fixo=@p5,telefone_celular=@p6 WHERE id=@p7`, c.Name, c.Age, c.City, c.Email, c.Phone, c.Mobile, id)
	} else {
		_, err = a.db.Exec(`INSERT INTO clientes(nome,idade,cidade,email,telefone_fixo,telefone_celular) VALUES(@p1,@p2,@p3,@p4,@p5,@p6)`, c.Name, c.Age, c.City, c.Email, c.Phone, c.Mobile)
	}
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	http.Redirect(w, r, "/", 302)
}
func (a *App) clientDelete(w http.ResponseWriter, r *http.Request) {
	if _, ok := a.require(w, r); !ok {
		return
	}
	id, _ := strconv.ParseInt(r.FormValue("id"), 10, 64)
	a.db.Exec(`DELETE FROM clientes WHERE id=@p1`, id)
	http.Redirect(w, r, "/", 302)
}

func validLogin(v string) bool {
	return regexp.MustCompile(`^[A-Za-z0-9._-]{3,50}$`).MatchString(strings.TrimSpace(v))
}
func validPass(v string) bool { return len(v) >= 8 }
func random(n int) []byte {
	b := make([]byte, n)
	if _, e := rand.Read(b); e != nil {
		panic(e)
	}
	return b
}
func pbkdf2(p, s []byte) []byte {
	out := make([]byte, 32)
	for block := uint32(1); block == 1; block++ {
		mac := hmac.New(sha256.New, p)
		mac.Write(s)
		mac.Write([]byte{0, 0, 0, 1})
		u := mac.Sum(nil)
		copy(out, u)
		for i := 1; i < iterations; i++ {
			mac = hmac.New(sha256.New, p)
			mac.Write(u)
			u = mac.Sum(nil)
			for j := range out {
				out[j] ^= u[j]
			}
		}
	}
	return out
}
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
	a.render(w, `{{define "content"}}<div class="top"><h1>Usuários</h1></div><div class="panel form"><h2>Novo operador</h2><form method="post" action="/usuarios/salvar"><label>Login</label><input name="login"><label>Senha (mínimo de 8 caracteres)</label><input type="password" name="senha"><button class="btn">Criar operador</button></form></div><div class="panel" style="margin-top:18px"><table><tr><th>ID</th><th>Login</th><th>Perfil</th><th>Ações</th></tr>{{range .Users}}<tr><td>{{.ID}}</td><td>{{.Login}}</td><td>{{if .Admin}}Administrador{{else}}Operador{{end}}</td><td><form class="inline" method="post" action="/usuarios/senha"><input type="hidden" name="id" value="{{.ID}}"><input name="senha" placeholder="Nova senha"><button class="btn gray">Alterar senha</button></form>{{if not .Admin}} <form class="inline" method="post" action="/usuarios/excluir"><input type="hidden" name="id" value="{{.ID}}"><button class="btn red">Excluir</button></form>{{end}}</td></tr>{{end}}</table></div>{{end}}`, view{Title: "Usuários", User: u, Users: us})
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
func (a *App) export(w http.ResponseWriter, r *http.Request) {
	u, ok := a.require(w, r)
	if !ok || !u.Admin {
		return
	}
	rows, err := a.db.Query(`SELECT id,nome,idade,cidade,email,COALESCE(telefone_fixo,''),COALESCE(telefone_celular,'') FROM clientes ORDER BY nome`)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer rows.Close()
	w.Header().Set("Content-Type", "text/csv; charset=utf-8")
	w.Header().Set("Content-Disposition", "attachment; filename=clientes.csv")
	out := csv.NewWriter(w)
	out.Comma = ';'
	out.Write([]string{"id", "nome", "idade", "cidade", "email", "telefone_fixo", "telefone_celular"})
	for rows.Next() {
		var c Client
		rows.Scan(&c.ID, &c.Name, &c.Age, &c.City, &c.Email, &c.Phone, &c.Mobile)
		out.Write([]string{fmt.Sprint(c.ID), c.Name, fmt.Sprint(c.Age), c.City, c.Email, c.Phone, c.Mobile})
	}
	if err := rows.Err(); err != nil {
		return
	}
	out.Flush()
}
