package main

import (
	"encoding/csv"
	"errors"
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
)

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
	a.render(w, "dashboard.html", view{Title: "Visão geral", User: u, Clients: cs, Stats: s, Query: q})
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
	a.render(w, "client_form.html", view{Title: "Cliente", User: u, Client: c, Editing: editing})
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
		a.render(w, "error.html", view{Title: "Erro", User: u, Error: err.Error()})
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
