package br.com.cadastro.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Conexao {

  private Conexao() {}

  public static Connection abrir() throws SQLException {
    String url = Config.get("sqlserver.db.url");
    String usuario = Config.get("sqlserver.db.usuario");
    String senha = Config.get("sqlserver.db.senha");

    if (usuario == null || usuario.isBlank()) {
      return DriverManager.getConnection(url);
    }

    return DriverManager.getConnection(url, usuario, senha);
  }

  public static void testar() throws SQLException {
    try (Connection conexao = abrir()) {
      if (!conexao.isValid(5)) {
        throw new SQLException("A conexão foi aberta, mas não foi validada.");
      }
    }
  }
}
