package main

import (
	"crypto/rand"
	"crypto/hmac"
	"crypto/sha256"
	"fmt"
	"net/url"
	"os"
	"regexp"
	"strings"
)

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
	iterations := 210000
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
