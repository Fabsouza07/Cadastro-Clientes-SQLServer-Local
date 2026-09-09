package main

import (
	"database/sql"
	_ "github.com/microsoft/go-mssqldb"
	_ "github.com/microsoft/go-mssqldb/namedpipe"
)

func ensureSchema(db *sql.DB) error {
	_, err := db.Exec(`IF OBJECT_ID('dbo.clientes','U') IS NULL CREATE TABLE dbo.clientes(id BIGINT IDENTITY PRIMARY KEY,nome NVARCHAR(120) NOT NULL,idade INT NOT NULL,cidade NVARCHAR(100) NOT NULL,email NVARCHAR(160) NOT NULL UNIQUE,telefone_fixo NVARCHAR(20),telefone_celular NVARCHAR(20)); IF OBJECT_ID('dbo.usuarios_cadastro_clientes','U') IS NULL CREATE TABLE dbo.usuarios_cadastro_clientes(id BIGINT IDENTITY PRIMARY KEY,login NVARCHAR(50) NOT NULL UNIQUE,senha_hash VARBINARY(64) NOT NULL,senha_salt VARBINARY(32) NOT NULL,administrador BIT NOT NULL DEFAULT 0,criado_em DATETIME2 NOT NULL DEFAULT SYSDATETIME(),tentativas_falhas INT NOT NULL DEFAULT 0,bloqueado_ate DATETIME2 NULL)`)
	return err
}
