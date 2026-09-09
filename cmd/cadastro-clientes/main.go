package main

import (
	"database/sql"
	"fmt"
	"html/template"
	"log"
	"net/http"
	"strings"
	"time"
)

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

	a := &App{
		db:            db,
		sessions:      make(map[string]User),
		templateCache: make(map[string]*template.Template),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/ok", func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, "OK") })
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
	mux.HandleFunc("/reset-admin", a.resetUsersPublic)
	mux.HandleFunc("/debug/users", a.debugUsers)

	addr := env("APP_ADDR", ":8080")
	displayAddr := addr
	if strings.HasPrefix(addr, ":") {
		displayAddr = "localhost" + addr
	}
	log.Printf("Sistema disponível em http://%s", displayAddr)
	log.Fatal(http.ListenAndServe(addr, mux))
}

func (a *App) render(w http.ResponseWriter, name string, v view) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")

	a.mu.Lock()
	tmpl, ok := a.templateCache[name]
	a.mu.Unlock()

	if !ok {
		var err error
		tmpl, err = template.ParseFiles("templates/layout.html", "templates/"+name)
		if err != nil {
			http.Error(w, "Erro ao carregar template: "+err.Error(), 500)
			return
		}
		a.mu.Lock()
		a.templateCache[name] = tmpl
		a.mu.Unlock()
	}

	if err := tmpl.ExecuteTemplate(w, "layout.html", v); err != nil {
		http.Error(w, err.Error(), 500)
	}
}
