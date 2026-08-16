package br.com.cadastro.dao;

import br.com.cadastro.model.Usuario;
import br.com.cadastro.util.Conexao;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UsuarioDAO {
  private static final String TABELA = "usuarios_cadastro_clientes";

  public void garantirTabela() throws SQLException {
    String sql = """
        IF OBJECT_ID('dbo.usuarios_cadastro_clientes','U') IS NULL
        CREATE TABLE dbo.usuarios_cadastro_clientes (
          id BIGINT IDENTITY(1,1) PRIMARY KEY, login NVARCHAR(50) NOT NULL UNIQUE,
          senha_hash VARBINARY(64) NOT NULL, senha_salt VARBINARY(32) NOT NULL,
          administrador BIT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_administrador DEFAULT 0,
          criado_em DATETIME2 NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_criado_em DEFAULT SYSDATETIME(),
          tentativas_falhas INT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_tentativas DEFAULT 0,
          bloqueado_ate DATETIME2 NULL)
        IF COL_LENGTH('dbo.usuarios_cadastro_clientes', 'tentativas_falhas') IS NULL
          ALTER TABLE dbo.usuarios_cadastro_clientes ADD tentativas_falhas INT NOT NULL CONSTRAINT DF_usuarios_cadastro_clientes_tentativas DEFAULT 0
        IF COL_LENGTH('dbo.usuarios_cadastro_clientes', 'bloqueado_ate') IS NULL
          ALTER TABLE dbo.usuarios_cadastro_clientes ADD bloqueado_ate DATETIME2 NULL
        """;
    try (Connection c = Conexao.abrir(); Statement s = c.createStatement()) { s.execute(sql); }
  }

  public boolean existeAlgum() throws SQLException {
    try (Connection c = Conexao.abrir(); Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT TOP 1 1 FROM " + TABELA)) { return r.next(); }
  }

  public Optional<Registro> buscarAutenticacao(String login) throws SQLException {
    String sql = """
        SELECT id,login,administrador,senha_hash,senha_salt,
          CASE WHEN bloqueado_ate > SYSUTCDATETIME() THEN 1 ELSE 0 END bloqueado
        FROM
        """ + TABELA + " WHERE login=?";
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, login);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? Optional.of(new Registro(mapear(r), r.getBytes("senha_hash"), r.getBytes("senha_salt"), r.getBoolean("bloqueado"))) : Optional.empty();
      }
    }
  }

  public List<Usuario> listar() throws SQLException {
    List<Usuario> usuarios = new ArrayList<>();
    try (Connection c = Conexao.abrir(); Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT id,login,administrador FROM " + TABELA + " ORDER BY administrador DESC, login")) {
      while (r.next()) usuarios.add(mapear(r));
    }
    return usuarios;
  }

  public void inserir(String login, byte[] hash, byte[] salt, boolean administrador) throws SQLException {
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(
        "INSERT INTO " + TABELA + "(login,senha_hash,senha_salt,administrador) VALUES(?,?,?,?)")) {
      s.setString(1, login); s.setBytes(2, hash); s.setBytes(3, salt); s.setBoolean(4, administrador); s.executeUpdate();
    }
  }

  public boolean alterarSenha(long id, byte[] hash, byte[] salt) throws SQLException {
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(
        "UPDATE " + TABELA + " SET senha_hash=?,senha_salt=? WHERE id=?")) {
      s.setBytes(1, hash); s.setBytes(2, salt); s.setLong(3, id); return s.executeUpdate() == 1;
    }
  }

  /** Registra uma falha pelo relógio do SQL Server e bloqueia após a terceira. */
  public Tentativa registrarFalha(long id) throws SQLException {
    String sql = "UPDATE " + TABELA + """
         SET
          tentativas_falhas = CASE
            WHEN bloqueado_ate IS NOT NULL AND bloqueado_ate <= SYSUTCDATETIME() THEN 1
            WHEN tentativas_falhas + 1 >= 3 THEN 0
            ELSE tentativas_falhas + 1 END,
          bloqueado_ate = CASE
            WHEN bloqueado_ate IS NOT NULL AND bloqueado_ate <= SYSUTCDATETIME() THEN NULL
            WHEN tentativas_falhas + 1 >= 3 THEN DATEADD(MINUTE, 10, SYSUTCDATETIME())
            ELSE NULL END
        OUTPUT INSERTED.tentativas_falhas, INSERTED.bloqueado_ate
        WHERE id=? AND (bloqueado_ate IS NULL OR bloqueado_ate <= SYSUTCDATETIME())
        """;
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, id);
      try (ResultSet r = s.executeQuery()) {
        return r.next() ? new Tentativa(r.getInt("tentativas_falhas"), r.getTimestamp("bloqueado_ate") != null) : null;
      }
    }
  }

  /** Limpa bloqueios e falhas somente se o usuário não estiver bloqueado no servidor. */
  public boolean registrarSucesso(long id) throws SQLException {
    String sql = "UPDATE " + TABELA + " SET tentativas_falhas=0,bloqueado_ate=NULL WHERE id=? "
        + "AND (bloqueado_ate IS NULL OR bloqueado_ate <= SYSUTCDATETIME())";
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, id);
      return s.executeUpdate() == 1;
    }
  }

  public boolean excluirOperador(long id) throws SQLException {
    try (Connection c = Conexao.abrir(); PreparedStatement s = c.prepareStatement(
        "DELETE FROM " + TABELA + " WHERE id=? AND administrador=0")) {
      s.setLong(1, id); return s.executeUpdate() == 1;
    }
  }

  private static Usuario mapear(ResultSet r) throws SQLException {
    return new Usuario(r.getLong("id"), r.getString("login"), r.getBoolean("administrador"));
  }

  public record Registro(Usuario usuario, byte[] hash, byte[] salt, boolean bloqueado) {}
  public record Tentativa(int falhas, boolean bloqueado) {}
}
