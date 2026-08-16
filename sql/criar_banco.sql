IF DB_ID('CadastroClientes') IS NULL CREATE DATABASE CadastroClientes;
GO
USE CadastroClientes;
GO
IF OBJECT_ID('dbo.clientes','U') IS NULL
CREATE TABLE dbo.clientes (id BIGINT IDENTITY(1,1) PRIMARY KEY,nome NVARCHAR(120) NOT NULL,idade INT NOT NULL,cidade NVARCHAR(100) NOT NULL,email NVARCHAR(160) NOT NULL UNIQUE,telefone_fixo NVARCHAR(20) NULL,telefone_celular NVARCHAR(20) NULL);
GO

IF COL_LENGTH('dbo.clientes', 'telefone_fixo') IS NULL
    ALTER TABLE dbo.clientes ADD telefone_fixo NVARCHAR(20) NULL;
GO
IF COL_LENGTH('dbo.clientes', 'telefone_celular') IS NULL
    ALTER TABLE dbo.clientes ADD telefone_celular NVARCHAR(20) NULL;
GO

IF OBJECT_ID('dbo.usuarios_cadastro_clientes','U') IS NULL
CREATE TABLE dbo.usuarios_cadastro_clientes (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    login NVARCHAR(50) NOT NULL UNIQUE,
    senha_hash VARBINARY(64) NOT NULL,
    senha_salt VARBINARY(32) NOT NULL,
    administrador BIT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_administrador DEFAULT 0,
    criado_em DATETIME2 NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_criado_em DEFAULT SYSDATETIME(),
    tentativas_falhas INT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_tentativas DEFAULT 0,
    bloqueado_ate DATETIME2 NULL
);
GO
IF COL_LENGTH('dbo.usuarios_cadastro_clientes', 'tentativas_falhas') IS NULL
    ALTER TABLE dbo.usuarios_cadastro_clientes ADD tentativas_falhas INT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_tentativas DEFAULT 0;
GO
IF COL_LENGTH('dbo.usuarios_cadastro_clientes', 'bloqueado_ate') IS NULL
    ALTER TABLE dbo.usuarios_cadastro_clientes ADD bloqueado_ate DATETIME2 NULL;
GO

-- Padroniza telefones existentes no formato brasileiro usado pelo cadastro.
;WITH telefones AS (
    SELECT
        id,
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone_fixo, ' ', ''), '(', ''), ')', ''), '-', ''), '.', ''), '+', ''), '/', '') AS fixo,
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone_celular, ' ', ''), '(', ''), ')', ''), '-', ''), '.', ''), '+', ''), '/', '') AS celular
    FROM dbo.clientes
)
UPDATE cliente
SET
    telefone_fixo = CASE WHEN LEN(fixo) = 10 THEN '(' + STUFF(STUFF(fixo, 3, 0, ') '), 9, 0, '-') ELSE telefone_fixo END,
    telefone_celular = CASE WHEN LEN(celular) = 11 THEN '(' + STUFF(STUFF(celular, 3, 0, ') '), 10, 0, '-') ELSE telefone_celular END
FROM dbo.clientes AS cliente
INNER JOIN telefones ON telefones.id = cliente.id;
GO
