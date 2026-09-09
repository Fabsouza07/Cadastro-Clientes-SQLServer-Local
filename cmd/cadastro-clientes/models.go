package main

import (
	"database/sql"
	"html/template"
	"sync"
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
	db            *sql.DB
	sessions      map[string]User
	mu            sync.RWMutex
	templateCache map[string]*template.Template
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
