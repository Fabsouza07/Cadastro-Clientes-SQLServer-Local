# Sistema Cadastro Clientes — SQL Server 2025

Projeto local e educacional em Java 25, Maven e JDBC.

## Preparação
1. Execute `sql/criar_banco.sql` no SQL Server 2025.
2. Edite `src/main/resources/config.properties` com usuário e senha locais. Se preferir
   variáveis de ambiente, use `SQLSERVER_DB_URL`, `SQLSERVER_DB_USUARIO` e `SQLSERVER_DB_SENHA`.
3. No terminal da pasta, execute: `mvn clean compile exec:java`.

Seu JDK 26 pode compilar o projeto porque o Maven usa `release 25`.

## Acesso ao sistema
No primeiro acesso, informe um login e uma senha de pelo menos 8 caracteres. Esse primeiro usuário é
criado como **administrador**. Pela opção **Usuários**, o administrador pode criar e excluir operadores
e alterar senhas. Operadores podem usar o cadastro de clientes, mas não gerenciam usuários nem exportam CSV.
Os usuários desta aplicação ficam na tabela `dbo.usuarios_cadastro_clientes`, separada de outros sistemas
que usam o mesmo banco.
Após três tentativas de senha incorretas, o login é bloqueado por 10 minutos. A contagem e o tempo de
bloqueio usam o relógio do SQL Server, não o relógio do computador que executa a aplicação.

## Recursos
CRUD completo, autenticação de usuários, confirmação de alteração/exclusão/saída, pesquisa, ordenação,
estatísticas, exportação CSV para administradores e validações.
