package br.com.cadastro.dao;

import br.com.cadastro.model.Cliente;
import br.com.cadastro.model.Estatisticas;
import br.com.cadastro.util.Conexao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClienteDAO {
  public long inserir(Cliente cliente) throws SQLException {
    String sql = "INSERT INTO clientes(nome,idade,cidade,email,telefone_fixo,telefone_celular) VALUES(?,?,?,?,?,?)";
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando =
            conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      preencher(comando, cliente);
      comando.executeUpdate();
      try (ResultSet chaves = comando.getGeneratedKeys()) {
        return chaves.next() ? chaves.getLong(1) : 0;
      }
    }
  }

  public List<Cliente> listar(String ordem) throws SQLException {
    String campo =
        switch (ordem) {
          case "idade" -> "idade, nome";
          case "cidade" -> "cidade, nome";
          default -> "nome, id";
        };
    List<Cliente> clientes = new ArrayList<>();
    String sql = "SELECT id,nome,idade,cidade,email,telefone_fixo,telefone_celular FROM clientes ORDER BY " + campo;

    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement(sql);
        ResultSet resultado = comando.executeQuery()) {
      while (resultado.next()) {
        clientes.add(mapear(resultado));
      }
    }
    return clientes;
  }

  public Optional<Cliente> buscarPorId(long id) throws SQLException {
    String sql = "SELECT id,nome,idade,cidade,email,telefone_fixo,telefone_celular FROM clientes WHERE id=?";
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement(sql)) {
      comando.setLong(1, id);
      try (ResultSet resultado = comando.executeQuery()) {
        return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
      }
    }
  }

  public List<Cliente> pesquisar(String termo) throws SQLException {
    String sql =
        "SELECT id,nome,idade,cidade,email,telefone_fixo,telefone_celular FROM clientes "
            + "WHERE LOWER(nome) LIKE LOWER(?) OR LOWER(cidade) LIKE LOWER(?) ORDER BY nome";
    List<Cliente> clientes = new ArrayList<>();
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement(sql)) {
      comando.setString(1, "%" + termo + "%");
      comando.setString(2, "%" + termo + "%");
      try (ResultSet resultado = comando.executeQuery()) {
        while (resultado.next()) {
          clientes.add(mapear(resultado));
        }
      }
    }
    return clientes;
  }

  public boolean atualizar(Cliente cliente) throws SQLException {
    String sql = "UPDATE clientes SET nome=?,idade=?,cidade=?,email=?,telefone_fixo=?,telefone_celular=? WHERE id=?";
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement(sql)) {
      preencher(comando, cliente);
      comando.setLong(7, cliente.id());
      return comando.executeUpdate() == 1;
    }
  }

  public boolean excluir(long id) throws SQLException {
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement("DELETE FROM clientes WHERE id=?")) {
      comando.setLong(1, id);
      return comando.executeUpdate() == 1;
    }
  }

  public void padronizarTelefones() throws SQLException {
    String sql =
        """
        ;WITH telefones AS (
          SELECT id,
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone_fixo, ' ', ''), '(', ''), ')', ''), '-', ''), '.', ''), '+', ''), '/', '') AS fixo,
            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(telefone_celular, ' ', ''), '(', ''), ')', ''), '-', ''), '.', ''), '+', ''), '/', '') AS celular
          FROM clientes
        )
        UPDATE cliente
        SET
          telefone_fixo = CASE WHEN LEN(fixo) = 10 THEN '(' + STUFF(STUFF(fixo, 3, 0, ') '), 9, 0, '-') ELSE telefone_fixo END,
          telefone_celular = CASE WHEN LEN(celular) = 11 THEN '(' + STUFF(STUFF(celular, 3, 0, ') '), 10, 0, '-') ELSE telefone_celular END
        FROM clientes AS cliente
        INNER JOIN telefones ON telefones.id = cliente.id
        """;
    try (Connection conexao = Conexao.abrir();
        PreparedStatement comando = conexao.prepareStatement(sql)) {
      comando.executeUpdate();
    }
  }

  public Estatisticas estatisticas() throws SQLException {
    long total = 0;
    double media = 0;
    String maisVelho = "-";
    String cidade = "-";

    try (Connection conexao = Conexao.abrir();
        Statement comando = conexao.createStatement()) {
      try (ResultSet resultado =
          comando.executeQuery(
              "SELECT COUNT(*) total, COALESCE(AVG(idade),0) media FROM clientes")) {
        if (resultado.next()) {
          total = resultado.getLong("total");
          media = resultado.getDouble("media");
        }
      }
      try (ResultSet resultado =
          comando.executeQuery("SELECT TOP 1 nome FROM clientes ORDER BY idade DESC, nome")) {
        if (resultado.next()) {
          maisVelho = resultado.getString(1);
        }
      }
      try (ResultSet resultado =
          comando.executeQuery(
              "SELECT TOP 1 cidade, COUNT(*) qtd FROM clientes "
                  + "GROUP BY cidade ORDER BY qtd DESC, cidade")) {
        if (resultado.next()) {
          cidade = resultado.getString(1);
        }
      }
    }
    return new Estatisticas(total, media, maisVelho, cidade);
  }

  private static void preencher(PreparedStatement comando, Cliente cliente) throws SQLException {
    comando.setString(1, cliente.nome());
    comando.setInt(2, cliente.idade());
    comando.setString(3, cliente.cidade());
    comando.setString(4, cliente.email());
    comando.setString(5, cliente.telefoneFixo());
    comando.setString(6, cliente.telefoneCelular());
  }

  private static Cliente mapear(ResultSet resultado) throws SQLException {
    return new Cliente(
        resultado.getLong("id"),
        resultado.getString("nome"),
        resultado.getInt("idade"),
        resultado.getString("cidade"),
        resultado.getString("email"),
        resultado.getString("telefone_fixo"),
        resultado.getString("telefone_celular"));
  }
}
