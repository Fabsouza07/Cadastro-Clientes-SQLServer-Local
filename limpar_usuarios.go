package main

import (
	"database/sql"
	"fmt"
	"os"

	_ "github.com/microsoft/go-mssqldb"
)

func main() {
	// 1. Prioridade total para a DSN vinda do ambiente (calculada pelo script PS)
	dsn := os.Getenv("SQLSERVER_DSN")

	if dsn == "" {
		// Fallback para as strings comuns se a variável não existir
		connections := []string{
			"server=(localdb)\\MSSQLLocalDB;database=CadastroClientes;trusted_connection=yes;encrypt=disable",
			"server=localhost\\SQLEXPRESS;database=CadastroClientes;trusted_connection=yes;encrypt=disable",
		}

		var success bool
		var rowsAffected int64

		for _, connStr := range connections {
			fmt.Printf("Tentando conectar via fallback: %s\n", connStr)
			db, err := sql.Open("sqlserver", connStr)
			if err != nil {
				continue
			}
			if err := db.Ping(); err == nil {
				res, err := db.Exec("DELETE FROM dbo.usuarios_cadastro_clientes")
				if err == nil {
					rowsAffected, _ = res.RowsAffected()
					success = true
					db.Close()
					break
				}
				db.Close()
			}
			db.Close()
		}

		if success {
			fmt.Printf("\n✅ SUCESSO! %d usuários foram removidos.\n", rowsAffected)
			return
		}
		fmt.Println("\n❌ FALHA: Não foi possível conectar ao banco de dados.")
		return
	}

	// 2. Uso da DSN fornecida pelo script de reset
	fmt.Printf("Conectando via DSN dinâmica: %s\n", dsn)
	db, err := sql.Open("sqlserver", dsn)
	if err != nil {
		fmt.Printf("Erro ao abrir conexão: %v\n", err)
		return
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		fmt.Printf("Erro ao dar Ping no banco: %v\n", err)
		return
	}

	res, err := db.Exec("DELETE FROM dbo.usuarios_cadastro_clientes")
	if err != nil {
		fmt.Printf("Erro ao deletar usuários: %v\n", err)
		return
	}

	rows, _ := res.RowsAffected()
	fmt.Printf("\n✅ SUCESSO! %d usuários foram removidos via pipe dinâmico.\n", rows)
}
