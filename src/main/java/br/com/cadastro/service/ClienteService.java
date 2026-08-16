package br.com.cadastro.service;

import br.com.cadastro.dao.ClienteDAO;
import br.com.cadastro.model.Cliente;
import br.com.cadastro.model.Estatisticas;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ClienteService {
  private final ClienteDAO dao = new ClienteDAO();

  public long cadastrar(
      String nome, int idade, String cidade, String email, String telefoneFixo, String telefoneCelular)
      throws SQLException {
    validar(nome, idade, cidade, email, telefoneFixo, telefoneCelular);
    return dao.inserir(
        new Cliente(
            null,
            nome,
            idade,
            cidade,
            email,
            formatarTelefone(telefoneFixo, false),
            formatarTelefone(telefoneCelular, true)));
  }

  public boolean alterar(
      long id,
      String nome,
      int idade,
      String cidade,
      String email,
      String telefoneFixo,
      String telefoneCelular)
      throws SQLException {
    validar(nome, idade, cidade, email, telefoneFixo, telefoneCelular);
    return dao.atualizar(
        new Cliente(
            id,
            nome,
            idade,
            cidade,
            email,
            formatarTelefone(telefoneFixo, false),
            formatarTelefone(telefoneCelular, true)));
  }

  public List<Cliente> listar(String ordem) throws SQLException {
    return dao.listar(ordem);
  }

  public List<Cliente> pesquisar(String termo) throws SQLException {
    return dao.pesquisar(termo);
  }

  public Optional<Cliente> buscar(long id) throws SQLException {
    return dao.buscarPorId(id);
  }

  public boolean excluir(long id) throws SQLException {
    return dao.excluir(id);
  }

  public void padronizarTelefones() throws SQLException {
    dao.padronizarTelefones();
  }

  public Estatisticas estatisticas() throws SQLException {
    return dao.estatisticas();
  }

  private static void validar(
      String nome,
      int idade,
      String cidade,
      String email,
      String telefoneFixo,
      String telefoneCelular) {
    if (nome == null || nome.isBlank()) {
      throw new IllegalArgumentException("Nome obrigatório.");
    }
    if (idade < 1 || idade > 120) {
      throw new IllegalArgumentException("Idade deve estar entre 1 e 120.");
    }
    if (cidade == null || cidade.isBlank()) {
      throw new IllegalArgumentException("Cidade obrigatória.");
    }
    if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new IllegalArgumentException("E-mail inválido.");
    }
    validarTelefone(telefoneFixo, 10, "Telefone fixo");
    validarTelefone(telefoneCelular, 11, "Telefone celular");
  }

  private static void validarTelefone(String telefone, int quantidadeDigitos, String rotulo) {
    if (telefone == null || telefone.isBlank()) {
      return;
    }
    String digitos = telefone.replaceAll("\\D", "");
    if (digitos.length() != quantidadeDigitos) {
      throw new IllegalArgumentException(rotulo + " incompleto.");
    }
  }

  private static String formatarTelefone(String telefone, boolean celular) {
    if (telefone == null || telefone.isBlank()) {
      return null;
    }
    String digitos = telefone.replaceAll("\\D", "");
    return celular
        ? String.format("(%s) %s-%s", digitos.substring(0, 2), digitos.substring(2, 7), digitos.substring(7))
        : String.format("(%s) %s-%s", digitos.substring(0, 2), digitos.substring(2, 6), digitos.substring(6));
  }
}
