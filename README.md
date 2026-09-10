# Sistema de Cadastro de Clientes — Go e SQL Server LocalDB

Aplicação web local desenvolvida em **Go (Golang)** conectada ao **Microsoft SQL Server LocalDB**, com interface web responsiva e sem dependências externas.

---

## 🚀 Como Executar

### 1. Execução Rápida (Recomendada)
Basta dar dois cliques no arquivo:
* **`executar.bat`**: Inicia a instância do LocalDB, sobe a aplicação Go na porta `8080` e abre automaticamente no **Google Chrome**.
* **`encerrar.bat`**: Encerra imediatamente a aplicação e fecha todos os terminais abertos.

> **Dica**: Você também pode encerrar a aplicação clicando em **Sair** no menu superior ou na tela de login. A saída fecha automaticamente todas as janelas de terminal associadas.

### 2. Execução via PowerShell
```powershell
.\iniciar-go-localdb.ps1
```
Acesse no navegador: <http://127.0.0.1:8080>

### 3. Execução com SQL Server Express / Servidor Externo
Por padrão, a aplicação pode se conectar a instâncias completas do SQL Server configurando as variáveis de ambiente:
```powershell
$env:SQLSERVER_SERVER = 'localhost\SQLEXPRESS'
$env:SQLSERVER_DATABASE = 'CadastroClientes'
$env:APP_ADDR = ':8080'
go run .\cmd\cadastro-clientes
```
Consulte o arquivo [`.env.example`](.env.example) para mais opções de conexão.

---

## 🌐 Integração com IIS / IIS Express (Proxy Reverso)

A aplicação pode rodar atrás do IIS utilizando regras de proxy reverso (`web.config`):

* **IIS Express:**
  ```powershell
  .\iniciar-iisexpress.ps1
  ```
  Acesse em: <http://localhost:8088>

* **IIS Completo:**
  Execute como Administrador no PowerShell:
  ```powershell
  .\configurar-iis.ps1
  ```

---

## 🔐 Acesso e Segurança

* **Primeiro Acesso:**
  No primeiro acesso, informe um login e uma senha com no mínimo 8 caracteres. Esse primeiro usuário é criado automaticamente como **Administrador**.
* **Perfis de Usuário:**
  * **Administrador:** Tem acesso à gestão de usuários (criação e exclusão de operadores, redefinição de senhas) e exportação de clientes para CSV.
  * **Operador:** Tem acesso ao painel geral e operações de CRUD dos clientes.
* **Segurança de Senhas:**
  As senhas são protegidas com **PBKDF2-HMAC-SHA256** (210.000 iterações com salt aleatório de 16 bytes).
* **Proteção contra Força Bruta:**
  Após três tentativas consecutivas de senha incorreta, o usuário é bloqueado por 10 minutos (utilizando o relógio do SQL Server via `SYSUTCDATETIME()`).

---

## 📦 Compilação do Executável

Para gerar um novo executável Windows em `bin/cadastro-clientes.exe`:

```powershell
go build -o .\bin\cadastro-clientes.exe .\cmd\cadastro-clientes
```

---

## 🗄️ Estrutura do Banco de Dados

* **`dbo.clientes`**: Armazena os clientes (nome, idade, cidade, e-mail único, telefone fixo e celular).
* **`dbo.usuarios_cadastro_clientes`**: Armazena os operadores e administradores do sistema.
* O script de criação das tabelas está disponível em [`sql/criar_banco.sql`](sql/criar_banco.sql). Ao iniciar, a aplicação também verifica e inicializa automaticamente as tabelas caso não existam.
